package com.example.medicationreminder.reminders

import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.ScheduledDose
import java.time.Instant

internal suspend fun deliverReminderIfUndecided(
    dose: ScheduledDose,
    occurrence: DoseOccurrence,
    isDecided: suspend () -> Boolean,
    showReminder: () -> Unit,
    cancelVisibleReminder: () -> Unit,
    persistedPending: Boolean = false,
): Boolean {
    if (!dose.matchesOccurrence(occurrence, persistedPending)) return false
    if (isDecided()) {
        cancelVisibleReminder()
        return false
    }

    showReminder()

    // Close the ordering where a decision commits after the pre-display check but
    // its cancellation runs before notify(). A second persisted-state check makes
    // that newly posted reminder self-cancel instead of surviving the race.
    if (isDecided()) {
        cancelVisibleReminder()
        return false
    }
    return true
}

internal suspend fun handleFiredOccurrence(
    occurrence: DoseOccurrence,
    activeDoses: List<ScheduledDose>,
    isDecided: suspend () -> Boolean,
    showReminder: (ScheduledDose) -> Unit,
    cancelVisibleReminder: () -> Unit,
    scheduleFollowing: suspend (ScheduledDose, Instant) -> Unit,
    persistedPending: Boolean = false,
) {
    val dose = activeDoses.firstOrNull { it.matchesOccurrence(occurrence, persistedPending) } ?: return
    deliverReminderIfUndecided(
        dose = dose,
        occurrence = occurrence,
        isDecided = isDecided,
        showReminder = { showReminder(dose) },
        cancelVisibleReminder = cancelVisibleReminder,
        persistedPending = persistedPending,
    )
    scheduleFollowing(dose, occurrence.scheduledFor)
}
