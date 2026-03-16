package com.example.gieterp

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AttendanceMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        AttendancePushTokenSync.saveLatestToken(applicationContext, token)
        AttendancePushTokenSync.syncCurrentTokenIfPossible(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val payload = AttendanceNotificationPayload.fromData(message.data) ?: run {
            Log.d("AttendanceMessaging", "Ignoring unsupported push payload")
            return
        }

        val activeRollNo = getSharedPreferences(AppSession.PREFERENCES_NAME, MODE_PRIVATE)
            .getString(AppSession.KEY_ROLL_NO, null)
        if (!payload.matchesRollNo(activeRollNo)) {
            Log.d("AttendanceMessaging", "Ignoring payload for a different roll number")
            return
        }

        AttendanceNotificationManager.showAttendanceUpdatedNotification(applicationContext, payload)
    }
}
