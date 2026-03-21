package com.example.gieterp

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.time.OffsetDateTime

data class AppControlInfo(
    val appEnabled: Boolean,
    val maintenanceMessage: String,
    val updatedAt: String,
    val updatedBy: String,
) {
    companion object {
        fun fromJson(json: JSONObject): AppControlInfo {
            val source = json.optJSONObject("appControl") ?: json
            return AppControlInfo(
                appEnabled = source.optBoolean("appEnabled", true),
                maintenanceMessage = source.optString("maintenanceMessage"),
                updatedAt = source.optString("updatedAt"),
                updatedBy = source.optString("updatedBy"),
            )
        }

        fun fromPreferences(context: Context): AppControlInfo? {
            val preferences = context.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE)
            val hasCache = preferences.contains(AppSession.KEY_APP_CONTROL_ENABLED)
            if (!hasCache) return null

            return AppControlInfo(
                appEnabled = preferences.getBoolean(AppSession.KEY_APP_CONTROL_ENABLED, true),
                maintenanceMessage = preferences.getString(AppSession.KEY_APP_CONTROL_MESSAGE, "").orEmpty(),
                updatedAt = preferences.getString(AppSession.KEY_APP_CONTROL_UPDATED_AT, "").orEmpty(),
                updatedBy = preferences.getString(AppSession.KEY_APP_CONTROL_UPDATED_BY, "").orEmpty(),
            )
        }

        fun persist(context: Context, info: AppControlInfo) {
            context.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
                putBoolean(AppSession.KEY_APP_CONTROL_ENABLED, info.appEnabled)
                putString(AppSession.KEY_APP_CONTROL_MESSAGE, info.maintenanceMessage)
                putString(AppSession.KEY_APP_CONTROL_UPDATED_AT, info.updatedAt)
                putString(AppSession.KEY_APP_CONTROL_UPDATED_BY, info.updatedBy)
            }
        }

        fun chooseEffective(gitHubInfo: AppControlInfo?, scriptInfo: AppControlInfo?, cachedInfo: AppControlInfo?): AppControlInfo? {
            val networkInfo = chooseNewer(gitHubInfo, scriptInfo)
            return networkInfo ?: cachedInfo
        }

        private fun chooseNewer(first: AppControlInfo?, second: AppControlInfo?): AppControlInfo? {
            if (first == null) return second
            if (second == null) return first

            val firstInstant = parseTimestamp(first.updatedAt)
            val secondInstant = parseTimestamp(second.updatedAt)
            return when {
                firstInstant != null && secondInstant != null -> {
                    if (secondInstant.isAfter(firstInstant)) second else first
                }
                firstInstant == null && secondInstant != null -> second
                firstInstant != null && secondInstant == null -> first
                !first.appEnabled && second.appEnabled -> first
                first.appEnabled && !second.appEnabled -> second
                second.updatedAt.length > first.updatedAt.length -> second
                else -> first
            }
        }

        private fun parseTimestamp(value: String): OffsetDateTime? {
            if (value.isBlank()) return null
            return runCatching { OffsetDateTime.parse(value) }.getOrNull()
        }
    }
}
