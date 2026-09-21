package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medicationreminder.appContainer
import com.example.medicationreminder.domain.model.DoseOccurrence
import java.time.Instant
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
                handleFiredOccurrence(
                    occurrence = occurrence,
                    activeDoses = container.repository.activeScheduledDoses(),
                    isDecided = {
                        container.repository.hasDoseDecisionOnLocalDay(
                            occurrence.doseTimeId,
                            occurrence.scheduledFor,
                            occurrence.zoneId,
                        )
                    },
                    showReminder = { dose ->
                        container.notifications.showReminder(
                            occurrence = occurrence,
                            alias = dose.medicationAlias,
                            dosage = dose.dosageText,
                        )
                    },
                    cancelVisibleReminder = {
                        container.notifications.cancelReminder(occurrence.doseTimeId)
                    },
                    scheduleFollowing = { dose, boundary ->
                        val result = container.scheduler.scheduleAfter(dose, boundary)
                        if (result == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                            Log.i(TAG, "Next reminder after delivery is waiting for exact-alarm access")
                        }
                    },
                )
                // Optional self-heal: restore reminders the system cleared while the process was frozen.
                if (container.preferences.repostMissedReminders.first()) {
                    container.missedReminderReposter.repostUndecidedOverdue()
                }
            } catch (error: Exception) {
                Log.e(TAG, "Unable to deliver reminder occurrence and schedule its successor", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.example.medicationreminder.REMIND"
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
