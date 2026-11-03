package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medicationreminder.appContainer
import com.example.medicationreminder.domain.model.DoseOccurrence
import java.time.Instant
import android.util.Log
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1)
        val doseTimeId = intent.getLongExtra(EXTRA_DOSE_TIME_ID, -1)
        val scheduledFor = intent.getLongExtra(EXTRA_SCHEDULED_FOR, -1)
        val zoneId = runCatching { ZoneId.of(intent.getStringExtra(EXTRA_ZONE_ID).orEmpty()) }.getOrNull()
        if (medicationId < 0 || doseTimeId < 0 || scheduledFor < 0 || zoneId == null) return
        val occurrence = DoseOccurrence(medicationId, doseTimeId, Instant.ofEpochMilli(scheduledFor), zoneId)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                container.missedReminderReposter.restoreOccurrence(occurrence,
                    legacy = !intent.getBooleanExtra(ReminderNotifications.EXTRA_MODERN_NOTIFICATION, false))
            } catch (error: Exception) {
                Log.e("ReminderDismiss", "Unable to restore dismissed reminder", error)
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
        const val EXTRA_ZONE_ID = "zone_id"
    }
}
