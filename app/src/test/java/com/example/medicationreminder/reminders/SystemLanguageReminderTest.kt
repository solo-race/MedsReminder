package com.example.medicationreminder.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import com.example.medicationreminder.appContainer
import com.example.medicationreminder.data.settings.AppLanguage
import com.example.medicationreminder.domain.model.*
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemLanguageReminderTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val container get() = context.appContainer
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private var medicationId: Long? = null

    @Before fun setUp() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container.preferences.setLanguage(AppLanguage.SYSTEM)
        container.preferences.setRepostMissedReminders(false)
        manager.cancelAll()
    }

    @After fun tearDown() = runBlocking {
        medicationId?.let { container.repository.deleteMedication(it) }
        manager.cancelAll()
        container.preferences.setLanguage(AppLanguage.SYSTEM)
    }

    @Test fun localeBroadcastSilentlyRefreshesTheSamePendingReminder() = runBlocking {
        RuntimeEnvironment.setQualifiers("zh-rCN")
        val occurrence = createPending()
        container.refreshReminderLanguage()
        val before = manager.activeNotifications.single()
        assertEquals("用药提醒", before.notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("已服用", before.notification.actions[0].title.toString())
        assertEquals("用药提醒", manager.getNotificationChannel(ReminderNotifications.REMINDER_CHANNEL).name.toString())

        RuntimeEnvironment.setQualifiers("en-rUS")
        sendLocaleChange()
        val after = manager.activeNotifications.single()
        assertEquals("Medication reminder", after.notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(before.tag, after.tag)
        assertEquals(before.id, after.id)
        assertEquals(before.notification.contentIntent, after.notification.contentIntent)
        assertEquals("Taken", after.notification.actions[0].title.toString())
        assertTrue(after.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(ReminderStage.PRE, container.repository.pendingReminders().single().stage)
        assertEquals(occurrence, container.repository.pendingReminders().single().occurrence)
        assertEquals(AppLanguage.SYSTEM, container.preferences.language.first())
    }

    @Test fun explicitChoiceSurvivesLocaleBroadcastAndUnsupportedSystemUsesEnglish() = runBlocking {
        RuntimeEnvironment.setQualifiers("zh-rCN")
        createPending()
        container.preferences.setLanguage(AppLanguage.ENGLISH)
        container.refreshReminderLanguage()
        assertEquals("Medication reminder", manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TITLE))
        sendLocaleChange()
        assertEquals(AppLanguage.ENGLISH, container.preferences.language.first())
        assertEquals("Medication reminder", manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TITLE))
        RuntimeEnvironment.setQualifiers("fr-rFR")
        container.preferences.setLanguage(AppLanguage.SYSTEM)
        container.refreshReminderLanguage()
        assertEquals("Medication reminder", manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test fun travelNotificationUsesResolvedLanguageAndPreservesUserText() {
        RuntimeEnvironment.setQualifiers("zh-rCN")
        container.notifications.createChannels()
        container.notifications.showTravelQuestion(987, "User medicine")
        val notification = manager.activeNotifications.single().notification
        assertEquals("时区已变更", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT)!!.contains("User medicine"))
        assertEquals("保留所选时区", notification.actions[0].title.toString())
    }

    private suspend fun createPending(): DoseOccurrence {
        val due = Instant.ofEpochMilli(System.currentTimeMillis() - 1_000)
        medicationId = container.repository.saveMedication(MedicationDraft(name = "Language test", dosageText = "User dose", note = "",
            enabled = true, weekdays = emptySet(), timeZoneMode = TimeZoneMode.MANUAL, manualZoneId = "UTC",
            times = emptyList(), scheduleMode = ScheduleMode.INTERVAL, intervalMinutes = 60,
            anchorEpochMillis = due.toEpochMilli())).medicationId
        val dose = container.repository.activeScheduledDoses().single { it.medicationId == medicationId }
        return dose.occurrenceAt(due).also {
            assertTrue(container.repository.registerPendingReminder(it, ReminderStage.PRE))
        }
    }

    private suspend fun sendLocaleChange() {
        val finished = CompletableDeferred<Unit>()
        context.sendOrderedBroadcast(Intent(Intent.ACTION_LOCALE_CHANGED)
            .setComponent(ComponentName(context, SystemEventReceiver::class.java)), null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) { finished.complete(Unit) }
            }, null, Activity.RESULT_OK, null, null)
        withTimeout(5_000) {
            while (!finished.isCompleted) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }
    }
}
