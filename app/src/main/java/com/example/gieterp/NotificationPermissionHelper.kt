package com.example.gieterp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

object NotificationPermissionHelper {
    private const val REQUEST_CODE_NOTIFICATIONS = 2306

    fun requestIfNeeded(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        val preferences = activity.getSharedPreferences(AppSession.PREFERENCES_NAME, AppCompatActivity.MODE_PRIVATE)
        if (preferences.getBoolean(AppSession.KEY_NOTIFICATION_PERMISSION_REQUESTED, false)) {
            return
        }

        preferences.edit {
            putBoolean(AppSession.KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
        }
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE_NOTIFICATIONS,
        )
    }
}
