package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medicationreminder.appContainer
import com.example.medicationreminder.domain.scheduling.NextDoseCalculator
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun systemEventRequiresReschedule(action: String?): Boolean = action == Intent.ACTION_BOOT_COMPLETED ||
    action == Intent.ACTION_MY_PACKAGE_REPLACED ||
    action == Intent.ACTION_TIME_CHANGED ||
    action == Intent.ACTION_TIMEZONE_CHANGED

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                if (systemEventRequiresReschedule(intent.action)) {
                    container.scheduler.scheduleAll()
                }
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    val now = Instant.now()
                    container.repository.activeScheduledDoses().forEach { dose ->
                        val occurredAt = NextDoseCalculator.mostRecentOverdueOccurrence(
                            dose.time,
                            dose.weekdays,
                            dose.zoneId,
                            now,
                        ) ?: return@forEach
                        val occurrence = dose.occurrenceAt(occurredAt)
                        deliverReminderIfUndecided(
                            dose = dose,
                            occurrence = occurrence,
                            isDecided = {
                                container.repository.hasDoseDecisionOnLocalDay(
                                    occurrence.doseTimeId,
                                    occurrence.scheduledFor,
                                    occurrence.zoneId,
                                )
                            },
                            showReminder = {
                                container.notifications.showReminder(
                                    occurrence = occurrence,
                                    alias = dose.medicationAlias,
                                    dosage = dose.dosageText,
                                )
                            },
                            cancelVisibleReminder = {
                                container.notifications.cancelReminder(occurrence.doseTimeId)
                            },
                        )
                    }
                }
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
