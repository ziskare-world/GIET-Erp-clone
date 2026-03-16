package com.example.gieterp

import android.app.Application

class GietErpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AttendanceNotificationManager.ensureChannel(this)
    }
}
