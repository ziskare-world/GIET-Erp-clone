package com.example.gieterp

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class AppControlMonitor(
    private val activity: AppCompatActivity,
    private val intervalMs: Long = 10_000L,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (activity.isFinishing || activity.isDestroyed) return
            AppControlChecker.checkAccess(activity)
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        stop()
        handler.postDelayed(monitorRunnable, intervalMs)
    }

    fun stop() {
        handler.removeCallbacks(monitorRunnable)
    }
}
