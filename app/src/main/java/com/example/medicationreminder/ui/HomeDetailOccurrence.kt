package com.example.medicationreminder.ui

import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.ScheduledDose
import com.example.medicationreminder.domain.scheduling.NextDoseCalculator
import com.example.medicationreminder.reminders.nextUndecidedOccurrence
import java.time.Instant

/**
 * Home selects today's most recent undecided due occurrence before any future occurrence.
 * "Today" is local to each schedule, matching missed-reminder recovery. This is a read-only
 * selection, not a new history/backlog policy; explicit notification occurrences bypass it.
 * Do not use this selector for AlarmManager, which must continue scheduling strictly in the future.
 */
internal suspend fun homeDetailOccurrence(
    doses: List<ScheduledDose>,
    medicationId: Long,
    now: Instant,
    isDecided: suspend (DoseOccurrence) -> Boolean,
): DoseOccurrence? {
    val medicationDoses = doses.filter { it.medicationId == medicationId }
    val due = medicationDoses.mapNotNull { dose ->
        NextDoseCalculator.mostRecentOverdueOccurrence(dose.time, dose.weekdays, dose.zoneId, now)
            ?.let(dose::occurrenceAt)
    }.sortedWith(compareByDescending<DoseOccurrence> { it.scheduledFor }.thenBy { it.doseTimeId })
    for (occurrence in due) {
        if (!isDecided(occurrence)) return occurrence
    }
    var earliest: DoseOccurrence? = null
    for (dose in medicationDoses) {
        val occurrence = nextUndecidedOccurrence(dose, now, isDecided) ?: continue
        if (earliest == null || occurrence.scheduledFor < earliest.scheduledFor) earliest = occurrence
    }
    return earliest
}
