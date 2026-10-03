package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medicationreminder.appContainer
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrence = intent.toDoseOccurrenceOrNull() ?: return
        val status = runCatching { DoseStatus.valueOf(intent.getStringExtra(EXTRA_STATUS).orEmpty()) }.getOrNull() ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.appContainer.doseDecisionUseCase(occurrence, status)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RECORD = "com.example.medicationreminder.RECORD_DOSE"
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_DOSE_TIME_ID = "dose_time_id"
        const val EXTRA_SCHEDULED_FOR = "scheduled_for"
        const val EXTRA_ZONE_ID = "zone_id"
        const val EXTRA_STATUS = "status"

        internal fun doseOccurrenceOrNull(
            medicationId: Long,
            doseTimeId: Long,
            scheduledForEpochMillis: Long,
            zoneIdValue: String?,
        ): DoseOccurrence? {
            val zoneId = runCatching { ZoneId.of(zoneIdValue.orEmpty()) }.getOrNull()
            if (medicationId < 0 || doseTimeId < 0 || scheduledForEpochMillis < 0 || zoneId == null) return null
            return DoseOccurrence(
                medicationId = medicationId,
                doseTimeId = doseTimeId,
                scheduledFor = Instant.ofEpochMilli(scheduledForEpochMillis),
                zoneId = zoneId,
            )
        }

        fun Intent.toDoseOccurrenceOrNull(): DoseOccurrence? = doseOccurrenceOrNull(
            medicationId = getLongExtra(EXTRA_MEDICATION_ID, -1),
            doseTimeId = getLongExtra(EXTRA_DOSE_TIME_ID, -1),
            scheduledForEpochMillis = getLongExtra(EXTRA_SCHEDULED_FOR, -1),
            zoneIdValue = getStringExtra(EXTRA_ZONE_ID),
        )
    }
}
