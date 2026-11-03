package com.example.medicationreminder.reminders

import android.util.Log
import com.example.medicationreminder.data.repository.MedicationRepository
import com.example.medicationreminder.domain.decision.DoseDecisionEffects
import com.example.medicationreminder.domain.model.DoseOccurrence

internal class ReminderDoseDecisionEffects(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
    private val notifications: ReminderNotifications,
) : DoseDecisionEffects {
    override suspend fun onPersistedDecision(occurrence: DoseOccurrence) {
        notifications.cancelReminder(occurrence)
        scheduler.cancel(occurrence.doseTimeId)
        val currentDose = repository.activeScheduledDoses().firstOrNull {
            it.medicationId == occurrence.medicationId &&
                it.doseTimeId == occurrence.doseTimeId
        }
        if (currentDose != null) {
            runCatching { scheduler.scheduleAfter(currentDose, java.time.Instant.now()) }
                .onSuccess { result ->
                    if (result == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                        Log.i(TAG, "Next reminder after a dose decision is waiting for exact-alarm access")
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "Unable to schedule the next reminder after a dose decision", error)
                }
        }
    }

    override suspend fun onStaleOccurrence(occurrence: DoseOccurrence) {
        // Remove only the stale visible notification. Do not cancel the current slot alarm:
        // the same stable doseTimeId may now represent an edited, valid future occurrence.
        notifications.cancelReminder(occurrence)
    }

    private companion object {
        const val TAG = "ReminderDoseDecisionEffects"
    }
}
