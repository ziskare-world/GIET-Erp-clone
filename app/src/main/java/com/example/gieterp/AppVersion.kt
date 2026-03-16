package com.example.gieterp

import android.content.Context
import android.os.Build

object AppVersion {
    fun name(context: Context): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

        return packageInfo.versionName.orEmpty().ifBlank { "1.0" }
    }
}
