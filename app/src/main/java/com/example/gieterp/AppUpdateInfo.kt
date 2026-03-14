package com.example.gieterp

import org.json.JSONObject

data class AppUpdateInfo(
    val latestVersionCode: Long,
    val latestVersionName: String,
    val downloadUrl: String,
    val title: String,
    val message: String,
    val changelog: List<String>,
    val forceUpdate: Boolean,
) {
    companion object {
        fun fromJson(
            json: JSONObject,
            defaultTitle: String,
            defaultMessage: String,
        ): AppUpdateInfo? {
            val latestVersionCode = json.longForKeys("latestVersionCode", "latest_version_code", "versionCode", "version_code")
            if (latestVersionCode <= 0L) {
                return null
            }

            val latestVersionName = json.stringForKeys("latestVersionName", "latest_version_name", "versionName", "version_name")
                .ifBlank { latestVersionCode.toString() }
            val downloadUrl = json.stringForKeys("downloadUrl", "download_url", "apkUrl", "apk_url", "playStoreUrl", "play_store_url")
            if (downloadUrl.isBlank()) {
                return null
            }

            val changelog = json.arrayForKeys("changelog", "releaseNotes", "release_notes")
                ?: json.stringForKeys("changelogText", "changelog_text", "releaseNotesText", "release_notes_text")
                    .lines()
                    .map { it.trim().removePrefix("-").removePrefix("•").trim() }
                    .filter { it.isNotBlank() }

            return AppUpdateInfo(
                latestVersionCode = latestVersionCode,
                latestVersionName = latestVersionName,
                downloadUrl = downloadUrl,
                title = json.stringForKeys("title", "dialogTitle", "dialog_title").ifBlank { defaultTitle },
                message = json.stringForKeys("message", "dialogMessage", "dialog_message").ifBlank { defaultMessage },
                changelog = changelog.orEmpty(),
                forceUpdate = json.booleanForKeys("forceUpdate", "force_update", "required", "mandatory"),
            )
        }

        private fun JSONObject.stringForKeys(vararg keys: String): String {
            keys.forEach { key ->
                val value = optString(key).trim()
                if (value.isNotEmpty()) {
                    return value
                }
            }
            return ""
        }

        private fun JSONObject.longForKeys(vararg keys: String): Long {
            keys.forEach { key ->
                if (has(key)) {
                    return optLong(key, -1L)
                }
            }
            return -1L
        }

        private fun JSONObject.booleanForKeys(vararg keys: String): Boolean {
            keys.forEach { key ->
                if (has(key)) {
                    return optBoolean(key, false)
                }
            }
            return false
        }

        private fun JSONObject.arrayForKeys(vararg keys: String): List<String>? {
            keys.forEach { key ->
                val array = optJSONArray(key) ?: return@forEach
                return buildList {
                    for (index in 0 until array.length()) {
                        val value = array.optString(index).trim()
                        if (value.isNotEmpty()) {
                            add(value)
                        }
                    }
                }
            }
            return null
        }
    }
}
