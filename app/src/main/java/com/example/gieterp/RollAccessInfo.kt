package com.example.gieterp

import org.json.JSONObject

data class RollAccessInfo(
    val canViewDetails: Boolean,
    val isBlocked: Boolean,
    val message: String,
    val blockMode: String,
) {
    companion object {
        fun allow(defaultMessage: String = ""): RollAccessInfo {
            return RollAccessInfo(
                canViewDetails = true,
                isBlocked = false,
                message = defaultMessage,
                blockMode = "",
            )
        }

        fun fromJson(json: JSONObject, fallbackMessage: String): RollAccessInfo {
            val isBlocked = json.optBoolean("isBlocked", false)
            return RollAccessInfo(
                canViewDetails = json.optBoolean("canViewDetails", !isBlocked),
                isBlocked = isBlocked,
                message = json.optString("message").ifBlank { fallbackMessage },
                blockMode = json.optString("blockMode"),
            )
        }
    }
}
