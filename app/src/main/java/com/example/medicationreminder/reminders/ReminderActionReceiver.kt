package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medicationreminder.appContainer
import com.example.medicationreminder.domain.model.DoseStatus
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1)
        val doseTimeId = intent.getLongExtra(EXTRA_DOSE_TIME_ID, -1)
        val scheduledFor = intent.getLongExtra(EXTRA_SCHEDULED_FOR, -1)
        val status = runCatching { DoseStatus.valueOf(intent.getStringExtra(EXTRA_STATUS).orEmpty()) }.getOrNull()
        if (medicationId < 0 || doseTimeId < 0 || scheduledFor < 0 || status == null) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                container.repository.recordDose(medicationId, doseTimeId, Instant.ofEpochMilli(scheduledFor), status)
                container.notifications.cancelReminder(doseTimeId)
                container.scheduler.scheduleAll()
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
        const val EXTRA_STATUS = "status"
    }
}

