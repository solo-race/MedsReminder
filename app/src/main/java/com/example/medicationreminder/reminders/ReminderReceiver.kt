package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medicationreminder.appContainer
import com.example.medicationreminder.domain.model.DoseOccurrence
import java.time.Instant
import com.example.medicationreminder.domain.model.ReminderStage
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrence = intent.toDoseOccurrenceOrNull() ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                val phase = intent.getStringExtra(EXTRA_PHASE)?.let { value ->
                    ReminderStage.entries.firstOrNull { it.name == value }
                } ?: ReminderStage.DUE // Pre-upgrade alarms carry the real due instant but no phase.
                val now = Instant.now()
                if (reminderTrigger(occurrence, phase).isAfter(now)) return@launch
                runCatching {
                    container.missedReminderReposter.deliverOccurrence(occurrence, phase, alert = true,
                        legacy = false, now = now)
                }.onFailure { Log.e(TAG, "Unable to persist/post current reminder; advancing its future phase", it) }
                val dose = container.repository.activeScheduledDoses().firstOrNull {
                    it.medicationId == occurrence.medicationId && it.doseTimeId == occurrence.doseTimeId
                }
                if (dose != null) container.scheduler.schedulePhaseAfter(dose, phase,
                    maxOf(now, reminderTrigger(occurrence, phase)))

            } catch (error: Exception) {
                Log.e(TAG, "Unable to deliver reminder occurrence and schedule its successor", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.example.medicationreminder.REMIND"
        const val EXTRA_PHASE = "reminder_phase"
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_DOSE_TIME_ID = "dose_time_id"
        const val EXTRA_SCHEDULED_FOR = "scheduled_for"
        const val EXTRA_ZONE_ID = "zone_id"

        fun Intent.toDoseOccurrenceOrNull(): DoseOccurrence? {
            val medicationId = getLongExtra(EXTRA_MEDICATION_ID, -1)
            val doseTimeId = getLongExtra(EXTRA_DOSE_TIME_ID, -1)
            val scheduledFor = getLongExtra(EXTRA_SCHEDULED_FOR, -1)
            val zoneId = runCatching { ZoneId.of(getStringExtra(EXTRA_ZONE_ID).orEmpty()) }.getOrNull()
            if (medicationId < 0 || doseTimeId < 0 || scheduledFor < 0 || zoneId == null) {
                Log.w(TAG, "Ignoring reminder broadcast with incomplete occurrence identity")
                return null
            }
            return DoseOccurrence(medicationId, doseTimeId, Instant.ofEpochMilli(scheduledFor), zoneId)
        }

        private const val TAG = "ReminderReceiver"
    }
}
