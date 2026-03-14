package com.example.gieterp

import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
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

    private var isCheckInProgress = false
    private var activeDialog: AlertDialog? = null

    fun checkForUpdates(activity: AppCompatActivity) {
        if (isCheckInProgress || activeDialog?.isShowing == true) {
            return
        }

        val metadataLocation = activity.getString(R.string.update_metadata_url).trim()
        if (metadataLocation.isBlank()) {
            return
        }

        isCheckInProgress = true
        if (metadataLocation.startsWith(ASSET_PREFIX)) {
            val assetName = metadataLocation.removePrefix(ASSET_PREFIX)
            val assetJson = runCatching {
                activity.assets.open(assetName).bufferedReader().use { it.readText() }
            }.getOrNull()

            if (assetJson == null) {
                isCheckInProgress = false
                return
            }

            val jsonObject = runCatching { JSONObject(assetJson) }.getOrNull()
            if (jsonObject == null) {
                isCheckInProgress = false
                return
            }

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
                isCheckInProgress = false
            },
        )
        VolleyProvider.getRequestQueue(activity).add(request)
    }

    private fun maybeShowUpdateDialog(activity: AppCompatActivity, jsonObject: JSONObject) {
        if (activity.isFinishing || activity.isDestroyed) {
            isCheckInProgress = false
            return
        }

        val updateInfo = AppUpdateInfo.fromJson(
            json = jsonObject,
            defaultTitle = activity.getString(R.string.update_available_title),
            defaultMessage = activity.getString(R.string.update_available_message),
        )

        if (updateInfo == null) {
            isCheckInProgress = false
            return
        }

        val currentVersionCode = getCurrentVersionCode(activity)
        if (updateInfo.latestVersionCode <= currentVersionCode) {
            isCheckInProgress = false
            return
        }

        val preferences = activity.getSharedPreferences(AppSession.PREFERENCES_NAME, AppCompatActivity.MODE_PRIVATE)
        val dismissedVersion = preferences.getLong(UPDATE_PREF_KEY_DISMISSED_VERSION, -1L)
        if (!updateInfo.forceUpdate && dismissedVersion == updateInfo.latestVersionCode) {
            isCheckInProgress = false
            return
        }

        val dialogBuilder = MaterialAlertDialogBuilder(activity)
            .setTitle(updateInfo.title)
            .setMessage(updateInfo.message)
            .setCancelable(false)
            .setPositiveButton(R.string.update_action_now, null)

        if (!updateInfo.forceUpdate) {
            dialogBuilder.setNegativeButton(R.string.update_action_later, null)
        }

        val dialog = dialogBuilder.create()
        activeDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (openUpdateUrl(activity, updateInfo.downloadUrl)) {
                    dialog.dismiss()
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
        dialog.setOnDismissListener {
            activeDialog = null
            isCheckInProgress = false
        }
        dialog.show()
    }

    private fun openUpdateUrl(activity: AppCompatActivity, url: String): Boolean {
        val parsedUri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            return false
        }
    }

    private fun getCurrentVersionCode(activity: AppCompatActivity): Long {
        val packageInfo = getPackageInfo(activity)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
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
