package com.example.gieterp

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RemoteControlService {
    private const val ACTION_REGISTER_INSTALL = "register_install"
    private const val ACTION_HEARTBEAT = "heartbeat"
    private const val ACTION_GET_ROLL_ACCESS = "get_roll_access"
    private const val ACTION_GET_APP_CONTROL = "get_app_control"

    private fun endpoint(context: Context): String = context.getString(R.string.apps_script_web_app_url).trim()
    private fun isConfigured(serviceUrl: String): Boolean {
        return serviceUrl.isNotBlank() && !serviceUrl.contains("REPLACE_WITH_YOUR_WEB_APP_ID")
    }

    fun ensureInstallRegistered(context: Context, rollNo: String) {
        if (rollNo.isBlank()) return

        val preferences = context.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(AppSession.KEY_INSTALL_REGISTERED, false)) {
            heartbeat(context, rollNo)
            return
        }

        sendAction(
            context = context,
            action = ACTION_REGISTER_INSTALL,
            rollNo = rollNo,
            onSuccess = {
                preferences.edit {
                    putBoolean(AppSession.KEY_INSTALL_REGISTERED, true)
                }
            },
        )
    }

    fun heartbeat(context: Context, rollNo: String) {
        if (rollNo.isBlank()) return
        sendAction(context = context, action = ACTION_HEARTBEAT, rollNo = rollNo)
    }

    fun checkRollAccess(context: Context, rollNo: String, onResult: (RollAccessInfo) -> Unit) {
        val serviceUrl = endpoint(context)
        if (rollNo.isBlank() || !isConfigured(serviceUrl)) {
            onResult(RollAccessInfo.allow())
            return
        }

        val request = object : JsonObjectRequest(
            Request.Method.POST,
            serviceUrl,
            buildBasePayload(context, ACTION_GET_ROLL_ACCESS, rollNo),
            { response ->
                onResult(
                    RollAccessInfo.fromJson(
                        json = response,
                        fallbackMessage = context.getString(R.string.roll_restricted_default_message),
                    ),
                )
            },
            { error ->
                Log.w("RemoteControlService", "Roll access check failed", error)
                onResult(RollAccessInfo.allow())
            },
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "Content-Type" to "application/json",
            )
        }

        request.setShouldCache(false)
        VolleyProvider.getRequestQueue(context).add(request)
    }

    fun getAppControl(context: Context, onResult: (AppControlInfo?) -> Unit) {
        val serviceUrl = endpoint(context)
        if (!isConfigured(serviceUrl)) {
            onResult(null)
            return
        }

        val request = object : JsonObjectRequest(
            Request.Method.POST,
            serviceUrl,
            JSONObject().apply { put("action", ACTION_GET_APP_CONTROL) },
            { response ->
                val hasControlPayload = response.has("appEnabled") || response.has("appControl")
                onResult(if (hasControlPayload) AppControlInfo.fromJson(response) else null)
            },
            { error ->
                Log.w("RemoteControlService", "App control fetch failed", error)
                onResult(null)
            },
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "Content-Type" to "application/json",
            )
        }

        request.setShouldCache(false)
        VolleyProvider.getRequestQueue(context).add(request)
    }

    private fun sendAction(
        context: Context,
        action: String,
        rollNo: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        val serviceUrl = endpoint(context)
        if (!isConfigured(serviceUrl)) return

        val request = object : JsonObjectRequest(
            Request.Method.POST,
            serviceUrl,
            buildBasePayload(context, action, rollNo),
            { onSuccess?.invoke() },
            { error ->
                Log.w("RemoteControlService", "Action '$action' failed", error)
            },
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "Content-Type" to "application/json",
            )
        }

        request.setShouldCache(false)
        VolleyProvider.getRequestQueue(context).add(request)
    }

    private fun buildBasePayload(context: Context, action: String, rollNo: String): JSONObject {
        return JSONObject().apply {
            put("action", action)
            put("installId", InstallIdentity.getOrCreate(context))
            put("rollNo", rollNo)
            put("appVersion", AppVersion.name(context))
            put("packageName", context.packageName)
            put("deviceModel", Build.MODEL ?: "")
            put("manufacturer", Build.MANUFACTURER ?: "")
            put("androidVersion", Build.VERSION.RELEASE ?: "")
            put("sdkInt", Build.VERSION.SDK_INT)
            put("timestamp", isoTimestamp())
        }
    }

    private fun isoTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
    }
}
