package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medicationreminder.appContainer
import com.example.medicationreminder.domain.model.DoseOccurrence
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrence = intent.toDoseOccurrenceOrNull() ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                val currentDose = container.repository.activeScheduledDoses()
                    .firstOrNull {
                        it.medicationId == occurrence.medicationId &&
                            it.doseTimeId == occurrence.doseTimeId &&
                            it.zoneId == occurrence.zoneId
                    }
                if (currentDose != null) {
                    container.notifications.showReminder(
                        occurrence = occurrence,
                        alias = currentDose.medicationAlias,
                        dosage = currentDose.dosageText,
                    )
                    // One alarm per dose is kept in the system. Phase 4 will make this reschedule path decision-aware.
                    container.scheduler.schedule(currentDose)
                }
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
            if (medicationId < 0 || doseTimeId < 0 || scheduledFor < 0 || zoneId == null) return null
            return DoseOccurrence(medicationId, doseTimeId, Instant.ofEpochMilli(scheduledFor), zoneId)
        }
    }
}
