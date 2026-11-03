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
import com.example.medicationreminder.domain.model.ReminderStage

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

internal fun reminderTrigger(occurrence: DoseOccurrence, phase: ReminderStage): Instant =
    if (phase == ReminderStage.PRE) occurrence.scheduledFor.minusSeconds(300) else occurrence.scheduledFor

internal suspend fun nextPhaseOccurrence(dose: ScheduledDose, phase: ReminderStage, after: Instant,
    isDecided: suspend (DoseOccurrence) -> Boolean): DoseOccurrence? =
    nextUndecidedOccurrence(dose, if (phase == ReminderStage.PRE) after.plusSeconds(300) else after, isDecided)

internal suspend fun preWindowOccurrences(dose: ScheduledDose, now: Instant,
    isDecided: suspend (DoseOccurrence) -> Boolean): List<DoseOccurrence> = buildList {
    var occurrence = nextUndecidedOccurrence(dose, now, isDecided)
    while (occurrence != null && !occurrence.scheduledFor.isAfter(now.plusSeconds(300))) {
        add(occurrence)
        occurrence = nextUndecidedOccurrence(dose, occurrence.scheduledFor, isDecided)
    }
}

class ReminderScheduler(
    private val context: Context,
    private val repository: MedicationRepository,
    private val onWindowOccurrence: (suspend (DoseOccurrence) -> Unit)? = null,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun scheduleAll(): ScheduleResult {
        if (!canScheduleExactAlarms()) return ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION

        for (dose in repository.activeScheduledDoses()) {
            // A single current-generation PRE/DUE pair replaces the legacy slot alarm.
            cancelLegacy(dose.doseTimeId)
            if (schedule(dose) == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                return ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION
            }
        }
        return ScheduleResult.SCHEDULED
    }

    suspend fun schedule(dose: ScheduledDose): ScheduleResult {
        val now = Instant.now()
        val result = scheduleAfter(dose, now, includeBoundary = true)
        // Recover every future occurrence already inside its five-minute PRE window.
        for (occurrence in preWindowOccurrences(dose, now) { repository.hasDoseDecision(it) })
            onWindowOccurrence?.invoke(occurrence)
        return result
    }

    suspend fun scheduleAfter(dose: ScheduledDose, boundary: Instant, includeBoundary: Boolean = false): ScheduleResult {
        for (phase in ReminderStage.entries) {
            // The initial exact-now DUE is explicit; the calculator itself remains strictly future.
            val after = if (includeBoundary && phase == ReminderStage.DUE) boundary.minusMillis(1) else boundary
            if (schedulePhaseAfter(dose, phase, after) == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION)
                return ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION
        }
        return ScheduleResult.SCHEDULED
    }

    suspend fun schedulePhaseAfter(dose: ScheduledDose, phase: ReminderStage, after: Instant): ScheduleResult {
        if (!canScheduleExactAlarms()) return ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION
        val occurrence = nextPhaseOccurrence(dose, phase, after) { repository.hasDoseDecision(it) }
            ?: return ScheduleResult.SCHEDULED
        return if (scheduleAt(occurrence, phase)) ScheduleResult.SCHEDULED
            else ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION
    }

    private fun scheduleAt(occurrence: DoseOccurrence, phase: ReminderStage): Boolean {
        // SCHEDULE_EXACT_ALARM is special access on Android 12+. It may be revoked
        // between the initial check and this call, so keep the guard at the API
        // boundary and treat the resulting SecurityException as waiting for access.
        if (!canScheduleExactAlarms()) return false
        val triggerAt = reminderTrigger(occurrence, phase)
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode(occurrence.doseTimeId, SHOW_INTENT_OFFSET),
            Intent(context, MainActivity::class.java)
                .setAction("ALARM_OPEN.${ReminderNotifications.occurrenceTag(occurrence)}")
                .putExtra(ReminderNotifications.EXTRA_MODERN_NOTIFICATION, true)
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
                reminderPendingIntent(occurrence, phase),
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(doseTimeId: Long) {
        cancelLegacy(doseTimeId)
        ReminderStage.entries.forEach { cancelAction(doseTimeId, "${ReminderReceiver.ACTION_REMIND}.$doseTimeId.${it.name}") }
    }

    private fun cancelLegacy(doseTimeId: Long) = cancelAction(doseTimeId, "${ReminderReceiver.ACTION_REMIND}.$doseTimeId")

    private fun cancelAction(doseTimeId: Long, action: String) {
        val pending = PendingIntent.getBroadcast(context, requestCode(doseTimeId, 0),
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_NO_CREATE or immutableFlag()) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun reminderPendingIntent(occurrence: DoseOccurrence, phase: ReminderStage = ReminderStage.DUE): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(occurrence.doseTimeId, 0),
            Intent(context, ReminderReceiver::class.java)
                .setAction("${ReminderReceiver.ACTION_REMIND}.${occurrence.doseTimeId}.${phase.name}")
                .putExtra(ReminderReceiver.EXTRA_PHASE, phase.name)
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
