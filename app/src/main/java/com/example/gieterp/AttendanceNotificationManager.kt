package com.example.gieterp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

object AttendanceNotificationManager {
    private const val CHANNEL_ID = "attendance_updates"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existingChannel = manager.getNotificationChannel(CHANNEL_ID)
        if (existingChannel != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.attendance_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.attendance_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun showAttendanceUpdatedNotification(context: Context, payload: AttendanceNotificationPayload) {
        if (isDuplicateEvent(context, payload.eventId)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(
            context,
            payload.eventId.hashCode(),
            AttendanceNotificationIntents.buildContentIntent(context, payload),
            pendingIntentFlags,
        )

        val bodyText = if (payload.subject.isNotBlank()) {
            context.getString(R.string.attendance_notification_body_format, payload.subject, payload.status)
        } else {
            context.getString(R.string.attendance_notification_status_only, payload.status)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_attendance_notification)
            .setContentTitle(context.getString(R.string.attendance_notification_title))
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(ContextCompat.getColor(context, R.color.accent_secondary))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(payload.eventId.hashCode(), notification)
        markHandled(context, payload.eventId)
    }

    private fun isDuplicateEvent(context: Context, eventId: String): Boolean {
        return context.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(AppSession.KEY_LAST_ATTENDANCE_EVENT_ID, null) == eventId
    }

    private fun markHandled(context: Context, eventId: String) {
        context.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            putString(AppSession.KEY_LAST_ATTENDANCE_EVENT_ID, eventId)
        }
    }

    fun clearHandledEvent(context: Context) {
        context.getSharedPreferences(AppSession.PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            remove(AppSession.KEY_LAST_ATTENDANCE_EVENT_ID)
        }
    }
}
