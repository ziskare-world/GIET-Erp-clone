package com.example.gieterp

import android.content.Context
import android.os.Build
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SupabaseService {
    private const val TAG = "SupabaseService"

    private fun getUrl(context: Context): String = context.getString(R.string.supabase_url).trim().trimEnd('/')
    private fun getApiKey(context: Context): String = context.getString(R.string.supabase_anon_key).trim()

    fun isConfigured(context: Context): Boolean {
        val url = getUrl(context)
        val key = getApiKey(context)
        return url.isNotBlank() && key.isNotBlank() && !url.contains("YOUR_PROJECT")
    }

    private fun getHeaders(context: Context, upsert: Boolean = false): MutableMap<String, String> {
        val headers = mutableMapOf(
            "apikey" to getApiKey(context),
            "Authorization" to "Bearer ${getApiKey(context)}",
            "Content-Type" to "application/json",
        )
        if (upsert) {
            headers["Prefer"] = "resolution=merge-duplicates"
        }
        return headers
    }

    fun syncDevice(context: Context, rollNo: String = "") {
        if (!isConfigured(context)) return

        val installId = InstallIdentity.getOrCreate(context)
        val currentRoll = if (rollNo.isNotBlank()) {
            rollNo
        } else {
            context.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(AppSession.KEY_ROLL_NO, "").orEmpty()
        }

        val body = JSONObject().apply {
            put("install_id", installId)
            put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("device_model", Build.MODEL ?: "")
            put("manufacturer", Build.MANUFACTURER ?: "")
            put("android_version", Build.VERSION.RELEASE ?: "")
            put("sdk_int", Build.VERSION.SDK_INT)
            put("phone_number", tryGetPhoneNumber(context))
            put("current_roll_no", currentRoll)
            put("app_version", AppVersion.name(context))
            put("last_seen_at", isoTimestamp())
        }

        val requestUrl = "${getUrl(context)}/rest/v1/devices"
        val request = object : StringRequest(
            Method.POST,
            requestUrl,
            { Log.d(TAG, "Device synced to Supabase successfully.") },
            { error -> Log.w(TAG, "Failed to sync device to Supabase: ${error.message}") },
        ) {
            override fun getBody(): ByteArray = body.toString().toByteArray(Charsets.UTF_8)
            override fun getHeaders(): MutableMap<String, String> = getHeaders(context, upsert = true)
        }

        request.setShouldCache(false)
        VolleyProvider.getRequestQueue(context).add(request)
    }

    fun checkDeviceBlocked(context: Context, onResult: (isBlocked: Boolean, reason: String?) -> Unit) {
        if (!isConfigured(context)) {
            onResult(false, null)
            return
        }

        val installId = InstallIdentity.getOrCreate(context)
        val requestUrl = "${getUrl(context)}/rest/v1/devices?install_id=eq.$installId&select=is_blocked,block_reason"

        val request = object : JsonArrayRequest(
            Request.Method.GET,
            requestUrl,
            null,
            { response ->
                if (response.length() > 0) {
                    val item = response.getJSONObject(0)
                    val isBlocked = item.optBoolean("is_blocked", false)
                    val reason = item.optString("block_reason", null)
                    onResult(isBlocked, reason)
                } else {
                    onResult(false, null)
                }
            },
            { error ->
                Log.w(TAG, "Check device blocked failed: ${error.message}")
                onResult(false, null)
            },
        ) {
            override fun getHeaders(): MutableMap<String, String> = getHeaders(context)
        }

        request.setShouldCache(false)
        VolleyProvider.getRequestQueue(context).add(request)
    }

    fun logActivity(
        context: Context,
        rollNo: String,
        details: JSONObject = JSONObject(),
    ) {
        if (!isConfigured(context) || rollNo.isBlank()) return

        val body = JSONObject().apply {
            put("install_id", InstallIdentity.getOrCreate(context))
            put("roll_no", rollNo)
            put("last_open_at", isoTimestamp())
            put("details", details)
        }

        val requestUrl = "${getUrl(context)}/rest/v1/activity_logs"
        val request = object : StringRequest(
            Method.POST,
            requestUrl,
            { Log.d(TAG, "Activity logged/updated for roll '$rollNo' in Supabase.") },
            { error -> Log.w(TAG, "Activity log failed: ${error.message}") },
        ) {
            override fun getBody(): ByteArray = body.toString().toByteArray(Charsets.UTF_8)
            override fun getHeaders(): MutableMap<String, String> = getHeaders(context, upsert = true)
        }

        request.setShouldCache(false)
        VolleyProvider.getRequestQueue(context).add(request)
    }

    fun logDetailedReportInspection(
        context: Context,
        searchingRollNo: String,
        targetRollNo: String,
        targetStudentName: String,
        reportType: String,
        inspectedData: JSONObject,
    ) {
        if (!isConfigured(context)) return

        val fullDetails = JSONObject().apply {
            put("searchingRollNo", searchingRollNo)
            put("targetRollNo", targetRollNo)
            put("targetStudentName", targetStudentName)
            put("reportType", reportType)
            put("deviceModel", Build.MODEL ?: "")
            put("manufacturer", Build.MANUFACTURER ?: "")
            put("inspectedData", inspectedData)
        }

        logActivity(
            context = context,
            rollNo = searchingRollNo.ifBlank { targetRollNo },
            details = fullDetails,
        )
    }

    fun logSearch(
        context: Context,
        searchingRollNo: String,
        searchedTerm: String,
        targetRollNo: String,
        targetStudentName: String,
        targetBranch: String,
        targetSemester: Int,
    ) {
        if (!isConfigured(context)) return

        val body = JSONObject().apply {
            put("install_id", InstallIdentity.getOrCreate(context))
            put("searching_roll_no", searchingRollNo)
            put("searched_term", searchedTerm)
            put("target_roll_no", targetRollNo)
            put("target_student_name", targetStudentName)
            put("target_branch", targetBranch)
            put("target_semester", targetSemester)
            put("created_at", isoTimestamp())
        }

        val requestUrl = "${getUrl(context)}/rest/v1/search_logs"
        val request = object : StringRequest(
            Method.POST,
            requestUrl,
            { Log.d(TAG, "Search logged to Supabase for roll '$targetRollNo'.") },
            { error -> Log.w(TAG, "Search log failed: ${error.message}") },
        ) {
            override fun getBody(): ByteArray = body.toString().toByteArray(Charsets.UTF_8)
            override fun getHeaders(): MutableMap<String, String> = getHeaders(context)
        }

        request.setShouldCache(false)
        VolleyProvider.getRequestQueue(context).add(request)
    }

    private fun tryGetPhoneNumber(context: Context): String {
        return "N/A"
    }

    private fun isoTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
    }
}
