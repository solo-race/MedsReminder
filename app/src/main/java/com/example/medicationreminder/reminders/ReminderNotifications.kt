package com.example.medicationreminder.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.medicationreminder.MainActivity
import com.example.medicationreminder.domain.model.DoseStatus

class ReminderNotifications(private val context: Context) {
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL,
                "Medication reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Reminders to take scheduled medication"
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                TRAVEL_CHANNEL,
                "Schedule time-zone changes",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Questions about manually zoned schedules after travel" },
        )
    }

    fun showReminder(medicationId: Long, doseTimeId: Long, scheduledFor: Long, name: String, dosage: String) {
        if (!canPostNotifications()) return
        val contentIntent = PendingIntent.getActivity(
            context,
            ReminderScheduler.requestCode(doseTimeId, 1),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_MEDICATION_ID, medicationId),
            PendingIntent.FLAG_UPDATE_CURRENT or ReminderScheduler.immutableFlag(),
        )
        val takenIntent = doseActionIntent(medicationId, doseTimeId, scheduledFor, DoseStatus.TAKEN)
        val skippedIntent = doseActionIntent(medicationId, doseTimeId, scheduledFor, DoseStatus.SKIPPED)
        val reminder = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time to take $name")
            .setContentText(dosage)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$name — $dosage"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .addAction(0, "Taken", takenIntent)
            .addAction(0, "Skipped", skippedIntent)
            .build()
        postNotification(reminderNotificationId(doseTimeId), reminder)
    }

    fun cancelReminder(doseTimeId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderNotificationId(doseTimeId))
    }

    fun showTravelQuestion(scheduleId: Long, scheduleName: String) {
        if (!canPostNotifications()) return
        val keep = timeZoneActionIntent(scheduleId, TimeZoneActionReceiver.ACTION_KEEP_MANUAL)
        val useDevice = timeZoneActionIntent(scheduleId, TimeZoneActionReceiver.ACTION_USE_DEVICE)
        val notification = NotificationCompat.Builder(context, TRAVEL_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Time zone changed")
            .setContentText("How should $scheduleName follow time after this trip?")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$scheduleName uses a manual time zone. Keep that zone, or change this schedule to follow your device?",
            ))
            .setAutoCancel(true)
            .addAction(0, "Keep selected zone", keep)
            .addAction(0, "Use device zone", useDevice)
            .build()
        postNotification(travelNotificationId(scheduleId), notification)
    }

    fun cancelTravelQuestion(scheduleId: Long) {
        NotificationManagerCompat.from(context).cancel(travelNotificationId(scheduleId))
    }

    private fun doseActionIntent(
        medicationId: Long,
        doseTimeId: Long,
        scheduledFor: Long,
        status: DoseStatus,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        ReminderScheduler.requestCode(doseTimeId, if (status == DoseStatus.TAKEN) 2 else 3),
        Intent(context, ReminderActionReceiver::class.java)
            .setAction("${ReminderActionReceiver.ACTION_RECORD}.${status.name}.$doseTimeId.$scheduledFor")
            .putExtra(ReminderActionReceiver.EXTRA_MEDICATION_ID, medicationId)
            .putExtra(ReminderActionReceiver.EXTRA_DOSE_TIME_ID, doseTimeId)
            .putExtra(ReminderActionReceiver.EXTRA_SCHEDULED_FOR, scheduledFor)
            .putExtra(ReminderActionReceiver.EXTRA_STATUS, status.name),
        PendingIntent.FLAG_UPDATE_CURRENT or ReminderScheduler.immutableFlag(),
    )

    private fun timeZoneActionIntent(scheduleId: Long, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        ReminderScheduler.requestCode(scheduleId, if (action == TimeZoneActionReceiver.ACTION_KEEP_MANUAL) 4 else 5),
        Intent(context, TimeZoneActionReceiver::class.java)
            .setAction(action)
            .putExtra(TimeZoneActionReceiver.EXTRA_SCHEDULE_ID, scheduleId),
        PendingIntent.FLAG_UPDATE_CURRENT or ReminderScheduler.immutableFlag(),
    )

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun postNotification(id: Int, notification: Notification) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and posting the notification.
        }
    }

    private fun reminderNotificationId(doseTimeId: Long) = ReminderScheduler.requestCode(doseTimeId, 100)
    private fun travelNotificationId(scheduleId: Long) = ReminderScheduler.requestCode(scheduleId, 200)

    private companion object {
        const val REMINDER_CHANNEL = "medication_reminders"
        const val TRAVEL_CHANNEL = "time_zone_changes"
    }
}
