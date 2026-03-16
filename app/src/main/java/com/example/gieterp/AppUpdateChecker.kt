package com.example.gieterp

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import java.io.File

object AppUpdateChecker {
    private const val UPDATE_PREF_KEY_DISMISSED_VERSION = "dismissedUpdateVersion"
    private const val UPDATE_PREF_KEY_PENDING_INSTALL_VERSION = "pendingInstallVersion"
    private const val ASSET_PREFIX = "asset://"
    private const val UPDATE_APK_FILE_PREFIX = "gieterp-update-v"
    private const val DOWNLOAD_PROGRESS_INTERVAL_MS = 500L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isCheckInProgress = false
    private var activeDialog: AlertDialog? = null
    private var dialogState: UpdateDialogState? = null
    private var activeDownloadId: Long = -1L
    private var activeDownloadManager: DownloadManager? = null
    private var downloadedApkFile: File? = null
    private var progressRunnable: Runnable? = null

    internal const val EXTRA_UPDATE_VERSION_CODE = "extraUpdateVersionCode"

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

        val freshMetadataUrl = buildFreshMetadataUrl(metadataLocation)
        val request = object : JsonObjectRequest(
            Request.Method.GET,
            freshMetadataUrl,
            null,
            { jsonObject ->
                maybeShowUpdateDialog(activity, jsonObject)
            },
            {
                isCheckInProgress = false
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

    private fun buildFreshMetadataUrl(baseUrl: String): String {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl${separator}ts=${System.currentTimeMillis()}"
    }

    private fun buildFreshDownloadUrl(baseUrl: String, versionCode: Long): String {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl${separator}vc=$versionCode&ts=${System.currentTimeMillis()}"
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

        val preferences = getUpdatePreferences(activity)
        val currentVersionCode = getCurrentVersionCode(activity)
        clearCompletedInstallSuppression(preferences, currentVersionCode)

        val pendingInstallVersion = preferences.getLong(UPDATE_PREF_KEY_PENDING_INSTALL_VERSION, -1L)
        if (updateInfo.latestVersionCode <= maxOf(currentVersionCode, pendingInstallVersion)) {
            isCheckInProgress = false
            return
        }

        val dismissedVersion = preferences.getLong(UPDATE_PREF_KEY_DISMISSED_VERSION, -1L)
        if (!updateInfo.forceUpdate && dismissedVersion == updateInfo.latestVersionCode) {
            isCheckInProgress = false
            return
        }

        showUpdateDialog(
            activity = activity,
            updateInfo = updateInfo,
            preferences = preferences,
        )
    }

    private fun showUpdateDialog(
        activity: AppCompatActivity,
        updateInfo: AppUpdateInfo,
        preferences: android.content.SharedPreferences,
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)
        val state = UpdateDialogState(
            titleView = dialogView.findViewById(R.id.updateTitle),
            messageView = dialogView.findViewById(R.id.updateMessage),
            statusView = dialogView.findViewById(R.id.updateStatusText),
            progressBar = dialogView.findViewById(R.id.updateProgressBar),
            primaryButton = dialogView.findViewById(R.id.updatePrimaryButton),
            secondaryButton = dialogView.findViewById(R.id.updateSecondaryButton),
        )

        state.titleView.text = updateInfo.title
        state.messageView.text = updateInfo.message
        renderAvailableState(activity, state, updateInfo)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setCancelable(!updateInfo.forceUpdate)
            .create()

        activeDialog = dialog
        dialogState = state

        state.primaryButton.setOnClickListener {
            when (state.phase) {
                UpdatePhase.AVAILABLE,
                UpdatePhase.FAILED -> {
                    if (startUpdateDownload(activity, updateInfo.downloadUrl, state, updateInfo.latestVersionCode)) {
                        renderDownloadingState(activity, state, 0, isIndeterminate = true)
                    } else {
                        Toast.makeText(activity, R.string.update_link_unavailable, Toast.LENGTH_LONG).show()
                    }
                }

                UpdatePhase.PERMISSION_REQUIRED,
                UpdatePhase.READY_TO_INSTALL -> {
                    installDownloadedApk(activity, state)
                }

                UpdatePhase.DOWNLOADING,
                UpdatePhase.INSTALLING -> Unit
            }
        }

        if (updateInfo.forceUpdate) {
            state.secondaryButton.visibility = View.GONE
        } else {
            state.secondaryButton.setOnClickListener {
                preferences.edit {
                    putLong(UPDATE_PREF_KEY_DISMISSED_VERSION, updateInfo.latestVersionCode)
                }
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener {
            stopProgressUpdates()
            activeDialog = null
            dialogState = null
            isCheckInProgress = false
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun renderAvailableState(
        context: Context,
        state: UpdateDialogState,
        updateInfo: AppUpdateInfo,
    ) {
        state.phase = UpdatePhase.AVAILABLE
        state.statusView.tag = updateInfo.latestVersionCode
        state.statusView.text = context.getString(R.string.update_status_ready, updateInfo.latestVersionName)
        state.progressBar.visibility = View.GONE
        state.progressBar.isIndeterminate = false
        state.progressBar.progress = 0
        state.primaryButton.isEnabled = true
        state.primaryButton.text = context.getString(R.string.update_action_now)
        if (state.secondaryButton.visibility == View.VISIBLE) {
            state.secondaryButton.text = context.getString(R.string.update_action_later)
            state.secondaryButton.isEnabled = true
        }
    }

    private fun renderDownloadingState(
        context: Context,
        state: UpdateDialogState,
        progressPercent: Int,
        isIndeterminate: Boolean,
    ) {
        state.phase = UpdatePhase.DOWNLOADING
        state.progressBar.visibility = View.VISIBLE
        state.progressBar.isIndeterminate = isIndeterminate
        if (!isIndeterminate) {
            state.progressBar.progress = progressPercent
            state.statusView.text = context.getString(R.string.update_status_downloading_percent, progressPercent)
        } else {
            state.statusView.text = context.getString(R.string.update_status_preparing_download)
        }
        state.primaryButton.isEnabled = false
        state.primaryButton.text = context.getString(R.string.update_action_downloading)
        state.secondaryButton.isEnabled = false
    }

    private fun renderReadyToInstallState(context: Context, state: UpdateDialogState) {
        state.phase = UpdatePhase.READY_TO_INSTALL
        state.progressBar.visibility = View.VISIBLE
        state.progressBar.isIndeterminate = false
        state.progressBar.progress = 100
        state.statusView.text = context.getString(R.string.update_status_ready_to_install)
        state.primaryButton.isEnabled = true
        state.primaryButton.text = context.getString(R.string.update_action_install)
        state.secondaryButton.visibility = View.GONE
    }

    private fun renderInstallingState(context: Context, state: UpdateDialogState) {
        state.phase = UpdatePhase.INSTALLING
        state.progressBar.visibility = View.VISIBLE
        state.progressBar.isIndeterminate = true
        state.statusView.text = context.getString(R.string.update_status_installing)
        state.primaryButton.isEnabled = false
        state.primaryButton.text = context.getString(R.string.update_action_installing)
        state.secondaryButton.visibility = View.GONE
    }

    private fun renderFailedState(context: Context, state: UpdateDialogState) {
        state.phase = UpdatePhase.FAILED
        state.progressBar.visibility = View.GONE
        state.statusView.text = context.getString(R.string.update_download_failed)
        state.primaryButton.isEnabled = true
        state.primaryButton.text = context.getString(R.string.update_action_retry)
        state.secondaryButton.isEnabled = true
    }

    private fun renderPermissionRequiredState(context: Context, state: UpdateDialogState) {
        state.phase = UpdatePhase.PERMISSION_REQUIRED
        state.progressBar.visibility = View.GONE
        state.statusView.text = context.getString(R.string.update_allow_install_permission)
        state.primaryButton.isEnabled = true
        state.primaryButton.text = context.getString(R.string.update_action_install)
        state.secondaryButton.visibility = View.GONE
    }

    private fun startUpdateDownload(
        activity: AppCompatActivity,
        url: String,
        state: UpdateDialogState,
        expectedVersionCode: Long,
    ): Boolean {
        val downloadUri = runCatching { Uri.parse(buildFreshDownloadUrl(url, expectedVersionCode)) }.getOrNull() ?: return false
        val downloadManager = activity.getSystemService(DownloadManager::class.java) ?: return false

        return runCatching {
            stopProgressUpdates()
            downloadedApkFile = null
            val downloadsDirectory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
            val destinationFile = File(downloadsDirectory, "$UPDATE_APK_FILE_PREFIX$expectedVersionCode.apk")
            cleanupOldUpdateApks(downloadsDirectory, destinationFile.name)
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

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
                    destinationFile.name,
                )

            activeDownloadManager = downloadManager
            activeDownloadId = downloadManager.enqueue(request)
            downloadedApkFile = destinationFile
            Toast.makeText(activity, R.string.update_download_started, Toast.LENGTH_LONG).show()
            startProgressUpdates(activity.applicationContext, state, expectedVersionCode)
            true
        }.getOrElse {
            false
        }
    }

    private fun startProgressUpdates(context: Context, state: UpdateDialogState, expectedVersionCode: Long) {
        stopProgressUpdates()
        val runnable = object : Runnable {
            override fun run() {
                val downloadManager = activeDownloadManager ?: return
                val downloadId = activeDownloadId
                if (downloadId < 0L) {
                    return
                }

                val query = DownloadManager.Query().setFilterById(downloadId)
                downloadManager.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        renderFailedState(context, state)
                        activeDownloadId = -1L
                        return
                    }

                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_PAUSED,
                        DownloadManager.STATUS_RUNNING -> {
                            val downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            if (totalBytes > 0L) {
                                val progressPercent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                renderDownloadingState(context, state, progressPercent, isIndeterminate = false)
                            } else {
                                renderDownloadingState(context, state, 0, isIndeterminate = true)
                            }
                            mainHandler.postDelayed(this, DOWNLOAD_PROGRESS_INTERVAL_MS)
                        }

                        DownloadManager.STATUS_SUCCESSFUL -> {
                            activeDownloadId = -1L
                            if (isDownloadedApkCurrent(context, expectedVersionCode)) {
                                renderReadyToInstallState(context, state)
                            } else {
                                downloadedApkFile = null
                                renderFailedState(context, state)
                                Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                            }
                        }

                        DownloadManager.STATUS_FAILED -> {
                            activeDownloadId = -1L
                            renderFailedState(context, state)
                        }
                    }
                }
            }
        }
        progressRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let(mainHandler::removeCallbacks)
        progressRunnable = null
    }

    private fun installDownloadedApk(activity: AppCompatActivity, state: UpdateDialogState) {
        installDownloadedApk(activity, state, state.statusView.tag as? Long ?: -1L)
    }

    private fun installDownloadedApk(
        activity: AppCompatActivity,
        state: UpdateDialogState,
        updateVersionCode: Long,
    ) {
        val apkFile = downloadedApkFile
        if (apkFile == null || !apkFile.exists()) {
            renderFailedState(activity, state)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            )
            activity.startActivity(settingsIntent)
            renderPermissionRequiredState(activity, state)
            return
        }

        renderInstallingState(activity, state)

        runCatching {
            markPendingInstallVersion(activity, updateVersionCode)
            val packageInstaller = activity.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(activity.packageName)
            }
            val sessionId = packageInstaller.createSession(params)
            packageInstaller.openSession(sessionId).use { session ->
                apkFile.inputStream().use { inputStream ->
                    session.openWrite("app-update", 0, -1).use { outputStream ->
                        inputStream.copyTo(outputStream)
                        session.fsync(outputStream)
                    }
                }

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val intent = Intent(activity, UpdateInstallReceiver::class.java).apply {
                    action = UpdateInstallReceiver.ACTION_INSTALL_STATUS
                    putExtra(EXTRA_UPDATE_VERSION_CODE, updateVersionCode)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    activity,
                    sessionId,
                    intent,
                    flags,
                )
                session.commit(pendingIntent.intentSender)
            }
        }.onFailure {
            clearPendingInstallVersion(activity)
            renderReadyToInstallState(activity, state)
            Toast.makeText(activity, R.string.update_install_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun getUpdatePreferences(context: Context) =
        context.getSharedPreferences(AppSession.PREFERENCES_NAME, AppCompatActivity.MODE_PRIVATE)

    private fun clearCompletedInstallSuppression(
        preferences: android.content.SharedPreferences,
        currentVersionCode: Long,
    ) {
        val pendingInstallVersion = preferences.getLong(UPDATE_PREF_KEY_PENDING_INSTALL_VERSION, -1L)
        if (pendingInstallVersion > 0L && currentVersionCode >= pendingInstallVersion) {
            preferences.edit {
                remove(UPDATE_PREF_KEY_PENDING_INSTALL_VERSION)
                remove(UPDATE_PREF_KEY_DISMISSED_VERSION)
            }
        }
    }

    private fun markPendingInstallVersion(context: Context, versionCode: Long) {
        getUpdatePreferences(context).edit {
            putLong(UPDATE_PREF_KEY_PENDING_INSTALL_VERSION, versionCode)
        }
    }

    private fun cleanupOldUpdateApks(downloadsDirectory: File, keepFileName: String) {
        downloadsDirectory.listFiles()
            ?.filter { file ->
                (file.name.startsWith(UPDATE_APK_FILE_PREFIX) || file.name == "gieterp-update.apk") &&
                    file.name != keepFileName
            }
            ?.forEach(File::delete)
    }

    private fun isDownloadedApkCurrent(context: Context, expectedVersionCode: Long): Boolean {
        val apkFile = downloadedApkFile ?: return false
        if (!apkFile.exists()) {
            return false
        }

        val archivePackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        } ?: return false

        return archivePackageInfo.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(archivePackageInfo) >= expectedVersionCode
    }

    internal fun markInstalledVersion(context: Context, versionCode: Long) {
        getUpdatePreferences(context).edit {
            putLong(UPDATE_PREF_KEY_PENDING_INSTALL_VERSION, versionCode)
            putLong(UPDATE_PREF_KEY_DISMISSED_VERSION, versionCode)
        }
    }

    internal fun clearPendingInstallVersion(context: Context) {
        getUpdatePreferences(context).edit {
            remove(UPDATE_PREF_KEY_PENDING_INSTALL_VERSION)
        }
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

    private data class UpdateDialogState(
        val titleView: TextView,
        val messageView: TextView,
        val statusView: TextView,
        val progressBar: LinearProgressIndicator,
        val primaryButton: MaterialButton,
        val secondaryButton: MaterialButton,
        var phase: UpdatePhase = UpdatePhase.AVAILABLE,
    )

    private enum class UpdatePhase {
        AVAILABLE,
        DOWNLOADING,
        READY_TO_INSTALL,
        INSTALLING,
        FAILED,
        PERMISSION_REQUIRED,
    }
}
