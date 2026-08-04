package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medicationreminder.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1)
        val doseTimeId = intent.getLongExtra(EXTRA_DOSE_TIME_ID, -1)
        val scheduledFor = intent.getLongExtra(EXTRA_SCHEDULED_FOR, -1)
        if (medicationId < 0 || doseTimeId < 0 || scheduledFor < 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                val currentDose = container.repository.activeScheduledDoses()
                    .firstOrNull { it.medicationId == medicationId && it.doseTimeId == doseTimeId }
                if (currentDose != null) {
                    container.notifications.showReminder(
                        medicationId = medicationId,
                        doseTimeId = doseTimeId,
                        scheduledFor = scheduledFor,
                        name = currentDose.medicationName,
                        dosage = currentDose.dosageText,
                    )
                    // One alarm per dose is kept in the system. Schedule tomorrow/next weekday after firing.
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
    }
}

