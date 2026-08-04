package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medicationreminder.appContainer
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                container.scheduler.scheduleAll()
                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    val currentZone = ZoneId.systemDefault().id
                    val previousZone = container.preferences.updateAndGetPreviousDeviceZone(currentZone)
                    if (previousZone != null && previousZone != currentZone) {
                        // The notifications only need a schedule id and medicine name; collect one value safely.
                        val manualSchedules = container.repository.manualSchedules()
                        val medications = container.repository.activeScheduledDoses()
                            .associateBy { it.scheduleId }
                        manualSchedules.forEach { schedule ->
                            medications[schedule.id]?.let { dose ->
                                container.notifications.showTravelQuestion(schedule.id, dose.medicationName)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
