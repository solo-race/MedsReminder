package com.example.medicationreminder.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medicationreminder.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeZoneActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1)
        if (scheduleId < 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                if (intent.action == ACTION_USE_DEVICE) {
                    container.repository.setScheduleToDeviceTime(scheduleId)
                    val scheduleResult = container.scheduler.scheduleAll()
                    if (scheduleResult == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                        Log.i(TAG, "Time-zone reminder rebuild is waiting for exact-alarm access")
                    }
                }
                container.notifications.cancelTravelQuestion(scheduleId)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to apply time-zone action and rebuild reminder alarms", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_KEEP_MANUAL = "com.example.medicationreminder.KEEP_MANUAL_ZONE"
        const val ACTION_USE_DEVICE = "com.example.medicationreminder.USE_DEVICE_ZONE"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        private const val TAG = "TimeZoneActionReceiver"
    }
}
