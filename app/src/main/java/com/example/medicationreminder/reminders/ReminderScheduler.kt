package com.example.medicationreminder.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.medicationreminder.MainActivity
import com.example.medicationreminder.data.repository.MedicationRepository
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.ScheduledDose
import com.example.medicationreminder.domain.scheduling.NextDoseCalculator
import java.time.Instant

internal suspend fun nextUndecidedOccurrence(
    dose: ScheduledDose,
    after: Instant,
    isDecided: suspend (DoseOccurrence) -> Boolean,
): DoseOccurrence? {
    var boundary = after
    while (true) {
        val scheduledFor = NextDoseCalculator.nextOccurrence(dose, boundary) ?: return null
        val occurrence = dose.occurrenceAt(scheduledFor)
        if (!isDecided(occurrence)) return occurrence
        boundary = scheduledFor
    }
}

class ReminderScheduler(
    private val context: Context,
    private val repository: MedicationRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun scheduleAll(): ScheduleResult {
        if (!canScheduleExactAlarms()) return ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION

        for (dose in repository.activeScheduledDoses()) {
            if (schedule(dose) == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                return ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION
            }
        }
        return ScheduleResult.SCHEDULED
    }

    suspend fun schedule(dose: ScheduledDose): ScheduleResult =
        scheduleAfter(dose, Instant.now())

    suspend fun scheduleAfter(dose: ScheduledDose, boundary: Instant): ScheduleResult {
        if (!canScheduleExactAlarms()) return ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION

        val occurrence = nextUndecidedOccurrence(dose, boundary) { candidate ->
            repository.hasDoseDecisionOnLocalDay(
                candidate.doseTimeId,
                candidate.scheduledFor,
                candidate.zoneId,
            )
        } ?: return ScheduleResult.SCHEDULED
        return if (scheduleAt(occurrence)) {
            ScheduleResult.SCHEDULED
        } else {
            ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION
        }
    }

    private fun scheduleAt(occurrence: DoseOccurrence): Boolean {
        // SCHEDULE_EXACT_ALARM is special access on Android 12+. It may be revoked
        // between the initial check and this call, so keep the guard at the API
        // boundary and treat the resulting SecurityException as waiting for access.
        if (!canScheduleExactAlarms()) return false
        val triggerAt = occurrence.scheduledFor
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode(occurrence.doseTimeId, SHOW_INTENT_OFFSET),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_MEDICATION_ID, occurrence.medicationId)
                .putExtra(MainActivity.EXTRA_DOSE_TIME_ID, occurrence.doseTimeId)
                .putExtra(MainActivity.EXTRA_SCHEDULED_FOR, occurrence.scheduledFor.toEpochMilli())
                .putExtra(MainActivity.EXTRA_ZONE_ID, occurrence.zoneId.id),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag(),
        )
        // setAlarmClock dispatches at the exact millisecond even under OEM alarm batching and Doze,
        // unlike setExactAndAllowWhileIdle which ColorOS widens into a 1-hour window.
        return try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt.toEpochMilli(), showIntent),
                reminderPendingIntent(occurrence),
            )
            true
        } catch (_: SecurityException) {
            false
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

    fun reminderPendingIntent(occurrence: DoseOccurrence): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(occurrence.doseTimeId, 0),
            Intent(context, ReminderReceiver::class.java)
                .setAction("${ReminderReceiver.ACTION_REMIND}.${occurrence.doseTimeId}")
                .putExtra(ReminderReceiver.EXTRA_MEDICATION_ID, occurrence.medicationId)
                .putExtra(ReminderReceiver.EXTRA_DOSE_TIME_ID, occurrence.doseTimeId)
                .putExtra(ReminderReceiver.EXTRA_SCHEDULED_FOR, occurrence.scheduledFor.toEpochMilli())
                .putExtra(ReminderReceiver.EXTRA_ZONE_ID, occurrence.zoneId.id),
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

enum class ScheduleResult {
    SCHEDULED,
    WAITING_FOR_EXACT_ALARM_PERMISSION,
}
