package com.example.gieterp

import android.content.Context
import android.content.Intent

object AttendanceNotificationIntents {
    fun buildContentIntent(context: Context, payload: AttendanceNotificationPayload): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AppSession.EXTRA_OPEN_ATTENDANCE_FROM_NOTIFICATION, true)
            putExtra(AppSession.EXTRA_ATTENDANCE_EVENT_ID, payload.eventId)
            putExtra(AppSession.EXTRA_ATTENDANCE_SUBJECT, payload.subject)
            putExtra(AppSession.EXTRA_ATTENDANCE_STATUS, payload.status)
            putExtra(AppSession.EXTRA_ATTENDANCE_SEMESTER, payload.semester)
        }
    }

    fun copyExtras(source: Intent?, destination: Intent): Intent {
        if (source == null) {
            return destination
        }

        if (source.getBooleanExtra(AppSession.EXTRA_OPEN_ATTENDANCE_FROM_NOTIFICATION, false)) {
            destination.putExtra(
                AppSession.EXTRA_OPEN_ATTENDANCE_FROM_NOTIFICATION,
                true,
            )
        }
        source.getStringExtra(AppSession.EXTRA_ATTENDANCE_EVENT_ID)?.let {
            destination.putExtra(AppSession.EXTRA_ATTENDANCE_EVENT_ID, it)
        }
        source.getStringExtra(AppSession.EXTRA_ATTENDANCE_SUBJECT)?.let {
            destination.putExtra(AppSession.EXTRA_ATTENDANCE_SUBJECT, it)
        }
        source.getStringExtra(AppSession.EXTRA_ATTENDANCE_STATUS)?.let {
            destination.putExtra(AppSession.EXTRA_ATTENDANCE_STATUS, it)
        }
        if (source.hasExtra(AppSession.EXTRA_ATTENDANCE_SEMESTER)) {
            destination.putExtra(
                AppSession.EXTRA_ATTENDANCE_SEMESTER,
                source.getIntExtra(AppSession.EXTRA_ATTENDANCE_SEMESTER, -1),
            )
        }
        return destination
    }

    fun shouldOpenAttendance(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(AppSession.EXTRA_OPEN_ATTENDANCE_FROM_NOTIFICATION, false) == true
    }
}
