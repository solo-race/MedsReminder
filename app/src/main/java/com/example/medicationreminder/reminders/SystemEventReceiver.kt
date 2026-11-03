package com.example.medicationreminder.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medicationreminder.appContainer
import com.example.medicationreminder.data.settings.AppLanguage
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

internal fun systemEventRequiresReschedule(action: String?): Boolean = action == Intent.ACTION_BOOT_COMPLETED ||
    action == Intent.ACTION_MY_PACKAGE_REPLACED ||
    action == Intent.ACTION_TIME_CHANGED ||
    action == Intent.ACTION_TIMEZONE_CHANGED ||
    action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED

internal fun systemEventCanReschedule(action: String?, exactAlarmsAllowed: Boolean): Boolean =
    systemEventRequiresReschedule(action) && exactAlarmsAllowed

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                if (intent.action == Intent.ACTION_LOCALE_CHANGED) {
                    if (container.preferences.language.first() == AppLanguage.SYSTEM) {
                        container.refreshReminderLanguage()
                    }
                    return@launch
                }
                if (systemEventRequiresReschedule(intent.action)) {
                    val exactAlarmsAllowed = container.scheduler.canScheduleExactAlarms()
                    if (systemEventCanReschedule(intent.action, exactAlarmsAllowed)) {
                        runCatching { container.restoreReminders() }
                            .onSuccess { result ->
                                if (result == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                                    Log.i(TAG, "System-event reminder rebuild is waiting for exact-alarm access")
                                }
                            }
                            .onFailure { error -> Log.e(TAG, "Unable to rebuild reminder alarms", error) }
                    } else {
                        container.missedReminderReposter.restorePending()
                        Log.i(TAG, "Skipping system-event reminder rebuild; waiting for exact-alarm access")
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
                                container.notifications.showTravelQuestion(schedule.id, dose.medicationName,
                                    container.preferences.language.first())
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "System reminder recovery failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SystemEventReceiver"
    }
}
