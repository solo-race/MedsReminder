package com.example.medicationreminder.ui

import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.ScheduledDose
import com.example.medicationreminder.domain.model.ScheduleMode
import com.example.medicationreminder.domain.scheduling.NextDoseCalculator
import com.example.medicationreminder.reminders.nextUndecidedOccurrence
import java.time.Instant
import androidx.lifecycle.repeatOnLifecycle

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
    val due = medicationDoses.filter { it.scheduleMode == ScheduleMode.FIXED_TIMES }.mapNotNull { dose ->
        NextDoseCalculator.mostRecentOverdueOccurrence(dose, now)
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


/** Only presentation time ticks; occurrence selection and database access do not. */
@androidx.compose.runtime.Composable
internal fun occurrenceClock(): androidx.compose.runtime.State<Instant> {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    return androidx.compose.runtime.produceState(Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) { value = Instant.now(); kotlinx.coroutines.delay(1_000) }
        }
    }
}

internal fun countdownTime(occurrence: DoseOccurrence, now: Instant): String {
    val seconds = java.time.Duration.between(now, occurrence.scheduledFor).abs().seconds
    return java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@androidx.compose.runtime.Composable
internal fun occurrenceCountdown(occurrence: DoseOccurrence, now: Instant): String =
    androidx.compose.ui.res.stringResource(if (occurrence.scheduledFor.isAfter(now))
        com.example.medicationreminder.R.string.countdown_until else com.example.medicationreminder.R.string.countdown_overdue,
        countdownTime(occurrence, now))
