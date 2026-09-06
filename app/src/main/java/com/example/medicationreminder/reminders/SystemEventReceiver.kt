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
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
                intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
                ) {
                    container.missedReminderReposter.repostUndecidedOverdue()
                }

                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    val currentZone = ZoneId.systemDefault().id
                    container.repository.updateDeviceZone(currentZone)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
