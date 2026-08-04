package com.example.medicationreminder.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.medicationreminder.data.repository.MedicationRepository
import com.example.medicationreminder.domain.model.ScheduledDose
import com.example.medicationreminder.domain.scheduling.NextDoseCalculator

class ReminderScheduler(
    private val context: Context,
    private val repository: MedicationRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun scheduleAll() {
        repository.activeScheduledDoses().forEach(::schedule)
    }

    fun schedule(dose: ScheduledDose) {
        val triggerAt = NextDoseCalculator.nextOccurrence(dose) ?: return
        val pendingIntent = reminderPendingIntent(dose, triggerAt.toEpochMilli())
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMilli(), pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMilli(), pendingIntent)
        }
    }

    fun cancel(doseTimeId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("${ReminderReceiver.ACTION_REMIND}.$doseTimeId")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(doseTimeId, 0),
            intent,
            PendingIntent.FLAG_NO_CREATE or immutableFlag(),
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun reminderPendingIntent(dose: ScheduledDose, scheduledFor: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(dose.doseTimeId, 0),
            Intent(context, ReminderReceiver::class.java)
                .setAction("${ReminderReceiver.ACTION_REMIND}.${dose.doseTimeId}")
                .putExtra(ReminderReceiver.EXTRA_MEDICATION_ID, dose.medicationId)
                .putExtra(ReminderReceiver.EXTRA_DOSE_TIME_ID, dose.doseTimeId)
                .putExtra(ReminderReceiver.EXTRA_SCHEDULED_FOR, scheduledFor),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )

    companion object {
        fun requestCode(id: Long, offset: Int): Int =
            ((id xor (id ushr 32)).toInt() and Int.MAX_VALUE).let { it + offset }

        fun immutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}

