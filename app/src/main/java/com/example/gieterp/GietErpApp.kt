package com.example.gieterp

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.TimeUnit

class GietErpApp : Application() {
    companion object {
        private const val UPDATE_CHECK_WORK_NAME = "app_update_check"
    }

    @Volatile
    private var adsInitialized = false

    override fun onCreate() {
        super.onCreate()
        AttendanceNotificationManager.ensureChannel(this)
        scheduleUpdateCheck()
    }

    private fun scheduleUpdateCheck() {
        val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UPDATE_CHECK_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            updateRequest,
        )
    }

    fun initializeAdsIfNeeded() {
        if (adsInitialized) {
            return
        }

        synchronized(this) {
            if (adsInitialized) {
                return
            }
            adsInitialized = true
        }

        Thread {
            MobileAds.initialize(this) {}
        }.start()
    }
}
