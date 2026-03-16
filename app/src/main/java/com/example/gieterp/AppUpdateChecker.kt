package com.example.gieterp

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
    private const val UPDATE_APK_FILE_NAME = "gieterp-update.apk"

    private var isCheckInProgress = false
    private var activeDialog: AlertDialog? = null
    private var activeDownloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null

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
                if (startUpdateDownload(activity, updateInfo.downloadUrl)) {
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

    private fun startUpdateDownload(activity: AppCompatActivity, url: String): Boolean {
        val downloadUri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val downloadManager = activity.getSystemService(DownloadManager::class.java) ?: return false

        return runCatching {
            registerDownloadReceiver(activity.applicationContext, downloadManager)

            val request = DownloadManager.Request(downloadUri)
                .setTitle(activity.getString(R.string.update_download_title))
                .setDescription(activity.getString(R.string.update_download_message))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(
                    activity,
                    Environment.DIRECTORY_DOWNLOADS,
                    UPDATE_APK_FILE_NAME,
                )

            activeDownloadId = downloadManager.enqueue(request)
            Toast.makeText(activity, R.string.update_download_started, Toast.LENGTH_LONG).show()
            true
        }.getOrElse {
            openExternalUpdateUrl(activity, downloadUri)
        }
    }

    private fun openExternalUpdateUrl(activity: AppCompatActivity, updateUri: Uri): Boolean {
        val browserIntent = Intent(Intent.ACTION_VIEW, updateUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return try {
            activity.startActivity(browserIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun registerDownloadReceiver(context: Context, downloadManager: DownloadManager) {
        if (downloadReceiver != null) {
            return
        }

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    return
                }

                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (downloadId != activeDownloadId) {
                    return
                }

                activeDownloadId = -1L
                val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
                if (apkUri == null) {
                    Toast.makeText(receiverContext, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                    unregisterDownloadReceiver(receiverContext)
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !receiverContext.packageManager.canRequestPackageInstalls()
                ) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${receiverContext.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    receiverContext.startActivity(settingsIntent)
                    Toast.makeText(receiverContext, R.string.update_allow_install_permission, Toast.LENGTH_LONG).show()
                    unregisterDownloadReceiver(receiverContext)
                    return
                }

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    receiverContext.startActivity(installIntent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(receiverContext, R.string.update_install_unavailable, Toast.LENGTH_LONG).show()
                } finally {
                    unregisterDownloadReceiver(receiverContext)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(downloadReceiver, filter)
        }
    }

    private fun unregisterDownloadReceiver(context: Context) {
        val receiver = downloadReceiver ?: return
        runCatching {
            context.unregisterReceiver(receiver)
        }
        downloadReceiver = null
    }

    private fun getCurrentVersionCode(activity: AppCompatActivity): Long {
        val packageInfo = getPackageInfo(activity)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

    private fun getPackageInfo(activity: AppCompatActivity) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        activity.packageManager.getPackageInfo(activity.packageName, 0)
    }
}
