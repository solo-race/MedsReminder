package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import com.example.medicationreminder.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1)
        val doseTimeId = intent.getLongExtra(EXTRA_DOSE_TIME_ID, -1)
        val scheduledFor = intent.getLongExtra(EXTRA_SCHEDULED_FOR, -1)
        if (medicationId < 0 || doseTimeId < 0 || scheduledFor < 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                val dose = container.repository.activeScheduledDoses()
                    .firstOrNull { it.medicationId == medicationId && it.doseTimeId == doseTimeId }
                    ?: return@launch
                if (container.repository.hasDoseDecisionOnLocalDay(doseTimeId, Instant.ofEpochMilli(scheduledFor), dose.zoneId)) return@launch
                container.notifications.showReminder(
                    medicationId = medicationId,
                    doseTimeId = doseTimeId,
                    scheduledFor = scheduledFor,
                    alias = dose.medicationAlias,
                    dosage = dose.dosageText,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DISMISSED = "com.example.medicationreminder.NOTIFICATION_DISMISSED"
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_DOSE_TIME_ID = "dose_time_id"
        const val EXTRA_SCHEDULED_FOR = "scheduled_for"
    }
}
