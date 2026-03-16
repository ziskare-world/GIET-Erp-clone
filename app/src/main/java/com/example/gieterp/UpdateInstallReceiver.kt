package com.example.gieterp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) {
            return
        }

        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmationIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmationIntent != null) {
                    context.startActivity(confirmationIntent)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                val installedVersionCode = intent.getLongExtra(AppUpdateChecker.EXTRA_UPDATE_VERSION_CODE, -1L)
                if (installedVersionCode > 0L) {
                    AppUpdateChecker.markInstalledVersion(context, installedVersionCode)
                }
                Toast.makeText(context, R.string.update_install_success, Toast.LENGTH_LONG).show()
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }

            else -> {
                AppUpdateChecker.clearPendingInstallVersion(context)
                val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: context.getString(R.string.update_install_failed)
                Toast.makeText(
                    context,
                    context.getString(R.string.update_install_failed_format, statusMessage),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.example.gieterp.action.UPDATE_INSTALL_STATUS"
    }
}
