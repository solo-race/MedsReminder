package com.example.medicationreminder.reminders

import com.example.medicationreminder.data.repository.MedicationRepository
import com.example.medicationreminder.domain.model.ScheduledDose
import com.example.medicationreminder.domain.scheduling.NextDoseCalculator
import java.time.Instant

/**
 * Restores reminders for doses that are overdue today and still have no Taken/Skipped decision,
 * e.g. after the system cleared a posted reminder while the app process was frozen.
 */
class MissedReminderReposter(
    private val repository: MedicationRepository,
    private val notifications: ReminderNotifications,
) {
    suspend fun repostUndecidedOverdue(now: Instant = Instant.now()) {
        plan(repository.activeScheduledDoses(), now) { dose, occurredAt ->
            repository.hasDoseDecisionOnLocalDay(dose.doseTimeId, occurredAt, dose.zoneId)
        }.forEach { missed ->
            notifications.showReminder(
                medicationId = missed.dose.medicationId,
                doseTimeId = missed.dose.doseTimeId,
                scheduledFor = missed.scheduledFor.toEpochMilli(),
                alias = missed.dose.medicationAlias,
                dosage = missed.dose.dosageText,
            )
        }
    }

    companion object {
        /** A dose to remind again, with the exact overdue occurrence it is reminded for. */
        data class MissedDose(val dose: ScheduledDose, val scheduledFor: Instant)

        /** Selection of overdue doses without a decision on their local day. */
        suspend fun plan(
            doses: List<ScheduledDose>,
            now: Instant,
            hasDecisionOnLocalDay: suspend (ScheduledDose, Instant) -> Boolean,
        ): List<MissedDose> = buildList {
            for (dose in doses) {
                val occurredAt = NextDoseCalculator.mostRecentOverdueOccurrence(
                    dose.time,
                    dose.weekdays,
                    dose.zoneId,
                    now,
                ) ?: continue
                if (!hasDecisionOnLocalDay(dose, occurredAt)) add(MissedDose(dose, occurredAt))
            }
        }
    }
}
