package com.example.gieterp

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object AppControlChecker {
    private var activeDialog: AlertDialog? = null
    private var isChecking = false

    fun checkAccess(activity: AppCompatActivity, onAllowed: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return

        val controlUrl = activity.getString(R.string.app_control_metadata_url).trim()
        if (isChecking) return
        isChecking = true
        val cachedInfo = AppControlInfo.fromPreferences(activity)

        var gitHubInfo: AppControlInfo? = null
        var scriptInfo: AppControlInfo? = null
        var gitHubResolved = controlUrl.isBlank()
        var scriptResolved = false

        fun completeIfReady() {
            if (!gitHubResolved || !scriptResolved) return

            isChecking = false
            val effectiveControl = AppControlInfo.chooseEffective(
                gitHubInfo = gitHubInfo,
                scriptInfo = scriptInfo,
                cachedInfo = cachedInfo,
            )

            if (effectiveControl != null) {
                AppControlInfo.persist(activity, effectiveControl)
            }

            if (effectiveControl?.appEnabled != false) {
                dismissDialogIfVisible()
                onAllowed?.invoke()
            } else {
                showBlockedDialog(activity, effectiveControl)
            }
        }

        RemoteControlService.getAppControl(activity) { remoteInfo ->
            scriptInfo = remoteInfo
            scriptResolved = true
            completeIfReady()
        }

        if (controlUrl.isBlank()) {
            gitHubResolved = true
            completeIfReady()
            return
        }

        val request = object : JsonObjectRequest(
            Request.Method.GET,
            buildFreshControlUrl(controlUrl),
            null,
            { response ->
                gitHubInfo = AppControlInfo.fromJson(response)
                gitHubResolved = true
                completeIfReady()
            },
            {
                gitHubResolved = true
                completeIfReady()
            },
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "Cache-Control" to "no-cache",
                "Pragma" to "no-cache",
            )
        }

        request.setShouldCache(false)
        VolleyProvider.getRequestQueue(activity).add(request)
    }

    private fun buildFreshControlUrl(baseUrl: String): String {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl${separator}ts=${System.currentTimeMillis()}"
    }

    private fun showBlockedDialog(activity: AppCompatActivity, controlInfo: AppControlInfo) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activeDialog?.isShowing == true) return

        activeDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.app_control_block_title)
            .setMessage(controlInfo.maintenanceMessage.ifBlank { activity.getString(R.string.app_control_default_message) })
            .setCancelable(false)
            .setPositiveButton(R.string.app_control_exit) { _, _ ->
                activity.finishAffinity()
            }
            .create()
            .also { it.show() }
    }

    private fun dismissDialogIfVisible() {
        activeDialog?.dismiss()
        activeDialog = null
    }
}
