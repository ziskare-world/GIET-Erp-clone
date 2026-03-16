package com.example.gieterp

import android.app.Application
import com.google.android.gms.ads.MobileAds

class GietErpApp : Application() {
    @Volatile
    private var adsInitialized = false

    override fun onCreate() {
        super.onCreate()
        AttendanceNotificationManager.ensureChannel(this)
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
