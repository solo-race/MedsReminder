package com.example.medicationreminder.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medicationreminder.appContainer
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                if (systemEventRequiresReschedule(intent.action)) {
                    val exactAlarmsAllowed = container.scheduler.canScheduleExactAlarms()
                    if (systemEventCanReschedule(intent.action, exactAlarmsAllowed)) {
                        runCatching { container.scheduler.scheduleAll() }
                            .onSuccess { result ->
                                if (result == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                                    Log.i(TAG, "System-event reminder rebuild is waiting for exact-alarm access")
                                }
                            }
                            .onFailure { error -> Log.e(TAG, "Unable to rebuild reminder alarms", error) }
                    } else {
                        Log.i(TAG, "Skipping system-event reminder rebuild; waiting for exact-alarm access")
                    }
                }
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    runCatching { container.missedReminderReposter.repostUndecidedOverdue() }
                        .onFailure { error -> Log.e(TAG, "Unable to repost missed reminders", error) }
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
