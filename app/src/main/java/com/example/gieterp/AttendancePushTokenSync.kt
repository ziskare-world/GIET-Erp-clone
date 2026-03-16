package com.example.gieterp

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

object AttendancePushTokenSync {
    private const val TAG = "AttendancePushToken"
    private const val PLATFORM = "android"

    fun requestNotificationPermissionIfNeeded(activity: androidx.appcompat.app.AppCompatActivity) {
        if (!isPushConfigured(activity.applicationContext)) {
            return
        }
        NotificationPermissionHelper.requestIfNeeded(activity)
    }

    fun syncCurrentTokenIfPossible(context: Context) {
        val appContext = context.applicationContext
        if (!isPushConfigured(appContext)) {
            return
        }

        val preferences = appContext.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val rollNo = preferences.getString(AppSession.KEY_ROLL_NO, null).orEmpty().trim()
        val registerUrl = appContext.getString(R.string.attendance_token_register_url).trim()
        if (rollNo.isBlank() || registerUrl.isBlank()) {
            return
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Failed to fetch FCM token", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result?.trim().orEmpty()
            if (token.isBlank()) {
                return@addOnCompleteListener
            }

            val storedToken = preferences.getString(AppSession.KEY_PUSH_TOKEN, null)
            val storedRollNo = preferences.getString(AppSession.KEY_PUSH_TOKEN_ROLL_NO, null)
            preferences.edit {
                putString(AppSession.KEY_PUSH_TOKEN, token)
            }
            if (token == storedToken && rollNo == storedRollNo) {
                return@addOnCompleteListener
            }

            val payload = JSONObject().apply {
                put("rollNo", rollNo)
                put("fcmToken", token)
                put("platform", PLATFORM)
                put("appVersion", AppVersion.name(appContext))
            }
            postJson(
                context = appContext,
                url = registerUrl,
                payload = payload,
                onSuccess = {
                    preferences.edit {
                        putString(AppSession.KEY_PUSH_TOKEN_ROLL_NO, rollNo)
                    }
                },
            )
        }
    }

    fun unregisterCurrentToken(context: Context, rollNoOverride: String? = null) {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val token = preferences.getString(AppSession.KEY_PUSH_TOKEN, null).orEmpty().trim()
        val rollNo = rollNoOverride?.trim().orEmpty()
            .ifBlank {
                preferences.getString(AppSession.KEY_PUSH_TOKEN_ROLL_NO, null).orEmpty().trim()
            }
        val unregisterUrl = appContext.getString(R.string.attendance_token_unregister_url).trim()

        AttendanceNotificationManager.clearHandledEvent(appContext)

        if (token.isBlank() || rollNo.isBlank() || unregisterUrl.isBlank()) {
            preferences.edit {
                remove(AppSession.KEY_PUSH_TOKEN_ROLL_NO)
            }
            return
        }

        val payload = JSONObject().apply {
            put("rollNo", rollNo)
            put("fcmToken", token)
            put("platform", PLATFORM)
        }
        postJson(
            context = appContext,
            url = unregisterUrl,
            payload = payload,
            onSuccess = {
                preferences.edit {
                    remove(AppSession.KEY_PUSH_TOKEN_ROLL_NO)
                }
            },
        )
    }

    fun saveLatestToken(context: Context, token: String) {
        if (token.isBlank()) {
            return
        }
        context.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            putString(AppSession.KEY_PUSH_TOKEN, token)
        }
    }

    fun isPushConfigured(context: Context): Boolean {
        if (!BuildConfig.FCM_ENABLED) {
            return false
        }
        return runCatching {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        }.getOrDefault(false)
    }

    private fun postJson(
        context: Context,
        url: String,
        payload: JSONObject,
        onSuccess: () -> Unit,
    ) {
        val request = object : JsonObjectRequest(
            Request.Method.POST,
            url,
            payload,
            { onSuccess() },
            { error ->
                Log.w(TAG, "Push token sync failed for $url", error)
            },
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "Content-Type" to "application/json",
            )
        }
        request.setShouldCache(false)
        VolleyProvider.getRequestQueue(context).add(request)
    }
}
