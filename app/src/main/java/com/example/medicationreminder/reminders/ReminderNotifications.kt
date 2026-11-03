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
import android.net.Uri
import com.example.medicationreminder.data.settings.AppLanguage
import com.example.medicationreminder.data.settings.withAppLanguage
import java.time.Instant
import com.example.medicationreminder.domain.model.ReminderStage
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.medicationreminder.MainActivity
import com.example.medicationreminder.R
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus

fun lockscreenTitle(alias: String?, fallback: String): String =
    alias?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

class ReminderNotifications(private val context: Context) {
    fun createChannels(language: AppLanguage = AppLanguage.SYSTEM) {
        val resourcesContext = context.withAppLanguage(language)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL,
                resourcesContext.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = resourcesContext.getString(R.string.reminder_channel_description)
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(NotificationChannel(SILENT_CHANNEL,
            resourcesContext.getString(R.string.reminder_silent_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        })
        manager.createNotificationChannel(
            NotificationChannel(
                TRAVEL_CHANNEL,
                resourcesContext.getString(R.string.travel_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = resourcesContext.getString(R.string.travel_channel_description) },
        )
    }

    fun showReminder(occurrence: DoseOccurrence, alias: String?, dosage: String,
        stage: ReminderStage = ReminderStage.DUE, soundEnabled: Boolean = true, alert: Boolean = true,
        language: AppLanguage = AppLanguage.SYSTEM) {
        if (!canPostNotifications()) return
        val resourcesContext = context.withAppLanguage(language)
        val title = lockscreenTitle(alias, resourcesContext.getString(R.string.notification_generic_title))
        val contentIntent = PendingIntent.getActivity(
            context,
            ReminderScheduler.requestCode(occurrence.doseTimeId, 1),
            Intent(context, MainActivity::class.java)
                .setAction("OPEN.${occurrenceTag(occurrence)}")
                .setData(Uri.parse("meds://reminder/${occurrence.doseTimeId}/${occurrence.scheduledFor.toEpochMilli()}"))
                .putExtra(EXTRA_MODERN_NOTIFICATION, true)
                .putExtra(MainActivity.EXTRA_MEDICATION_ID, occurrence.medicationId)
                .putExtra(MainActivity.EXTRA_DOSE_TIME_ID, occurrence.doseTimeId)
                .putExtra(MainActivity.EXTRA_SCHEDULED_FOR, occurrence.scheduledFor.toEpochMilli())
                .putExtra(MainActivity.EXTRA_ZONE_ID, occurrence.zoneId.id),
            PendingIntent.FLAG_UPDATE_CURRENT or ReminderScheduler.immutableFlag(),
        )
        val takenIntent = doseActionIntent(occurrence, DoseStatus.TAKEN)
        val skippedIntent = doseActionIntent(occurrence, DoseStatus.SKIPPED)
        val reminder = NotificationCompat.Builder(context, if (soundEnabled) REMINDER_CHANNEL else SILENT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("$dosage • ${resourcesContext.getString(if (stage == ReminderStage.PRE) R.string.reminder_due_soon else R.string.reminder_due_now)}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title — $dosage"))
            .setWhen(occurrence.scheduledFor.toEpochMilli())
            .setUsesChronometer(stage == ReminderStage.PRE)
            .setChronometerCountDown(stage == ReminderStage.PRE)
            .setSilent(!soundEnabled || !alert)
            .setOnlyAlertOnce(!alert)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setDeleteIntent(dismissedIntent(occurrence))
            .addAction(0, resourcesContext.getString(R.string.status_taken), takenIntent)
            .addAction(0, resourcesContext.getString(R.string.status_skipped), skippedIntent)
            .build()
        try { NotificationManagerCompat.from(context).notify(occurrenceTag(occurrence), OCCURRENCE_NOTIFICATION_ID, reminder) }
        catch (_: SecurityException) { }
    }

    fun cancelReminder(occurrence: DoseOccurrence) {
        NotificationManagerCompat.from(context).cancel(occurrenceTag(occurrence), OCCURRENCE_NOTIFICATION_ID)
    }

    /** Slot cleanup is only for an explicit plan change, disable or deletion. */
    fun cancelReminder(doseTimeId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag?.startsWith("dose:$doseTimeId:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
        cancelLegacyReminder(doseTimeId)
    }

    fun cancelLegacyReminder(doseTimeId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderNotificationId(doseTimeId))
    }

    fun showTravelQuestion(scheduleId: Long, scheduleName: String, language: AppLanguage = AppLanguage.SYSTEM) {
        if (!canPostNotifications()) return
        val resourcesContext = context.withAppLanguage(language)
        val keep = timeZoneActionIntent(scheduleId, TimeZoneActionReceiver.ACTION_KEEP_MANUAL)
        val useDevice = timeZoneActionIntent(scheduleId, TimeZoneActionReceiver.ACTION_USE_DEVICE)
        val notification = NotificationCompat.Builder(context, TRAVEL_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(resourcesContext.getString(R.string.travel_notification_title))
            .setContentText(resourcesContext.getString(R.string.travel_notification_text, scheduleName))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                resourcesContext.getString(R.string.travel_notification_detail, scheduleName),
            ))
            .setAutoCancel(true)
            .addAction(0, resourcesContext.getString(R.string.travel_keep_zone), keep)
            .addAction(0, resourcesContext.getString(R.string.travel_use_device_zone), useDevice)
            .build()
        postNotification(travelNotificationId(scheduleId), notification)
    }

    fun cancelTravelQuestion(scheduleId: Long) {
        NotificationManagerCompat.from(context).cancel(travelNotificationId(scheduleId))
    }

    private fun doseActionIntent(occurrence: DoseOccurrence, status: DoseStatus): PendingIntent = PendingIntent.getBroadcast(
        context,
        ReminderScheduler.requestCode(occurrence.doseTimeId, if (status == DoseStatus.TAKEN) 2 else 3),
        Intent(context, ReminderActionReceiver::class.java)
            .setAction("${ReminderActionReceiver.ACTION_RECORD}.${status.name}.${occurrenceTag(occurrence)}")
            .putExtra(EXTRA_MODERN_NOTIFICATION, true)
            .putExtra(ReminderActionReceiver.EXTRA_MEDICATION_ID, occurrence.medicationId)
            .putExtra(ReminderActionReceiver.EXTRA_DOSE_TIME_ID, occurrence.doseTimeId)
            .putExtra(ReminderActionReceiver.EXTRA_SCHEDULED_FOR, occurrence.scheduledFor.toEpochMilli())
            .putExtra(ReminderActionReceiver.EXTRA_ZONE_ID, occurrence.zoneId.id)
            .putExtra(ReminderActionReceiver.EXTRA_STATUS, status.name),
        PendingIntent.FLAG_UPDATE_CURRENT or ReminderScheduler.immutableFlag(),
    )

    private fun dismissedIntent(occurrence: DoseOccurrence): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ReminderScheduler.requestCode(occurrence.doseTimeId, 6),
            Intent(context, ReminderDismissReceiver::class.java)
                .setAction("${ReminderDismissReceiver.ACTION_DISMISSED}.${occurrenceTag(occurrence)}")
                .putExtra(EXTRA_MODERN_NOTIFICATION, true)
                .putExtra(ReminderDismissReceiver.EXTRA_MEDICATION_ID, occurrence.medicationId)
                .putExtra(ReminderDismissReceiver.EXTRA_DOSE_TIME_ID, occurrence.doseTimeId)
                .putExtra(ReminderDismissReceiver.EXTRA_SCHEDULED_FOR, occurrence.scheduledFor.toEpochMilli())
                .putExtra(ReminderDismissReceiver.EXTRA_ZONE_ID, occurrence.zoneId.id),
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

    companion object {
        const val EXTRA_MODERN_NOTIFICATION = "modern_occurrence_notification"
        const val OCCURRENCE_NOTIFICATION_ID = 1
        fun occurrenceTag(occurrence: DoseOccurrence) = "dose:${occurrence.doseTimeId}:${occurrence.scheduledFor.toEpochMilli()}"
        const val SILENT_CHANNEL = "medication_reminders_silent"
        const val REMINDER_CHANNEL = "medication_reminders"
        const val TRAVEL_CHANNEL = "time_zone_changes"
    }
}
