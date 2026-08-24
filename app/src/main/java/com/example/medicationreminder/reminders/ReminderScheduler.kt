package com.example.medicationreminder.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.medicationreminder.MainActivity
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
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode(dose.doseTimeId, SHOW_INTENT_OFFSET),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_MEDICATION_ID, dose.medicationId),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )
        // setAlarmClock dispatches at the exact millisecond even under OEM alarm batching and Doze,
        // unlike setExactAndAllowWhileIdle which ColorOS widens into a 1-hour window.
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt.toEpochMilli(), showIntent),
            reminderPendingIntent(dose, triggerAt.toEpochMilli()),
        )
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
        const val SHOW_INTENT_OFFSET = 7

        fun requestCode(id: Long, offset: Int): Int =
            ((id xor (id ushr 32)).toInt() and Int.MAX_VALUE).let { it + offset }

        fun immutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}

