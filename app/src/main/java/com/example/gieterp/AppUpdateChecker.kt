package com.example.gieterp

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject

object AppUpdateChecker {
    private const val UPDATE_PREF_KEY_DISMISSED_VERSION = "dismissedUpdateVersion"
    private const val ASSET_PREFIX = "asset://"

    private var hasCheckedThisProcess = false

    fun checkForUpdates(activity: AppCompatActivity) {
        if (hasCheckedThisProcess) {
            return
        }

        val metadataLocation = activity.getString(R.string.update_metadata_url).trim()
        if (metadataLocation.isBlank()) {
            return
        }

        hasCheckedThisProcess = true
        if (metadataLocation.startsWith(ASSET_PREFIX)) {
            val assetName = metadataLocation.removePrefix(ASSET_PREFIX)
            val assetJson = runCatching {
                activity.assets.open(assetName).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return

            val jsonObject = runCatching { JSONObject(assetJson) }.getOrNull() ?: return
            maybeShowUpdateDialog(activity, jsonObject)
            return
        }

        val request = JsonObjectRequest(
            Request.Method.GET,
            metadataLocation,
            null,
            { jsonObject ->
                maybeShowUpdateDialog(activity, jsonObject)
            },
            {
            },
        )
        VolleyProvider.getRequestQueue(activity).add(request)
    }

    private fun maybeShowUpdateDialog(activity: AppCompatActivity, jsonObject: JSONObject) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        val updateInfo = AppUpdateInfo.fromJson(
            json = jsonObject,
            defaultTitle = activity.getString(R.string.update_available_title),
            defaultMessage = activity.getString(R.string.update_available_message),
        ) ?: return

        val currentVersionCode = getCurrentVersionCode(activity)
        if (updateInfo.latestVersionCode <= currentVersionCode) {
            return
        }

        val preferences = activity.getSharedPreferences(AppSession.PREFERENCES_NAME, AppCompatActivity.MODE_PRIVATE)
        val dismissedVersion = preferences.getLong(UPDATE_PREF_KEY_DISMISSED_VERSION, -1L)
        if (!updateInfo.forceUpdate && dismissedVersion == updateInfo.latestVersionCode) {
            return
        }

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)
        bindDialogContent(
            activity = activity,
            dialogView = dialogView,
            updateInfo = updateInfo,
        )

        val dialogBuilder = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setCancelable(!updateInfo.forceUpdate)
            .setPositiveButton(R.string.update_action_now, null)

        if (!updateInfo.forceUpdate) {
            dialogBuilder.setNegativeButton(R.string.update_action_later, null)
        }

        val dialog = dialogBuilder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (openUpdateUrl(activity, updateInfo.downloadUrl)) {
                    dialog.dismiss()
                    if (updateInfo.forceUpdate) {
                        activity.finishAffinity()
                    }
                } else {
                    Toast.makeText(activity, R.string.update_link_unavailable, Toast.LENGTH_LONG).show()
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                preferences.edit {
                    putLong(UPDATE_PREF_KEY_DISMISSED_VERSION, updateInfo.latestVersionCode)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun bindDialogContent(
        activity: AppCompatActivity,
        dialogView: View,
        updateInfo: AppUpdateInfo,
    ) {
        val currentVersionName = getCurrentVersionName(activity)
        val titleView = dialogView.findViewById<TextView>(R.id.updateTitle)
        val badgeView = dialogView.findViewById<TextView>(R.id.updateVersionBadge)
        val messageView = dialogView.findViewById<TextView>(R.id.updateMessage)
        val headingView = dialogView.findViewById<TextView>(R.id.updateChangelogHeading)
        val changelogView = dialogView.findViewById<TextView>(R.id.updateChangelog)

        titleView.text = updateInfo.title
        badgeView.text = activity.getString(
            R.string.update_version_summary,
            currentVersionName,
            updateInfo.latestVersionName,
        )
        messageView.text = updateInfo.message

        if (updateInfo.changelog.isEmpty()) {
            headingView.visibility = View.GONE
            changelogView.visibility = View.GONE
        } else {
            changelogView.text = updateInfo.changelog.joinToString("\n") { "• $it" }
        }
    }

    private fun openUpdateUrl(activity: AppCompatActivity, url: String): Boolean {
        val parsedUri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW, parsedUri)
        val canResolve = intent.resolveActivity(activity.packageManager) != null
        if (!canResolve) {
            return false
        }
        activity.startActivity(intent)
        return true
    }

    private fun getCurrentVersionCode(activity: AppCompatActivity): Long {
        val packageInfo = getPackageInfo(activity)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

    private fun getCurrentVersionName(activity: AppCompatActivity): String {
        return getPackageInfo(activity).versionName.orEmpty().ifBlank { getCurrentVersionCode(activity).toString() }
    }

    private fun getPackageInfo(activity: AppCompatActivity) = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        activity.packageManager.getPackageInfo(activity.packageName, 0)
    }
}
