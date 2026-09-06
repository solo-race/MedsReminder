package com.example.medicationreminder.reminders

import com.example.medicationreminder.data.repository.MedicationRepository
import com.example.medicationreminder.domain.decision.DoseDecisionEffects
import com.example.medicationreminder.domain.model.DoseOccurrence

internal class ReminderDoseDecisionEffects(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
    private val notifications: ReminderNotifications,
) : DoseDecisionEffects {
    override suspend fun onPersistedDecision(occurrence: DoseOccurrence) {
        notifications.cancelReminder(occurrence.doseTimeId)
        scheduler.cancel(occurrence.doseTimeId)
        val currentDose = repository.activeScheduledDoses().firstOrNull {
            it.medicationId == occurrence.medicationId &&
                it.doseTimeId == occurrence.doseTimeId &&
                it.zoneId == occurrence.zoneId
        }
        if (currentDose != null) {
            scheduler.scheduleAfter(currentDose, occurrence.scheduledFor)
        }
    }

    override suspend fun onStaleOccurrence(occurrence: DoseOccurrence) {
        // Remove only the stale visible notification. Do not cancel the current slot alarm:
        // the same stable doseTimeId may now represent an edited, valid future occurrence.
        notifications.cancelReminder(occurrence.doseTimeId)
    }
}
