package com.example.medicationreminder.reminders

import android.Manifest
import android.app.NotificationManager
import com.example.medicationreminder.MainActivity
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.ReminderStage
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderEarlyActionTest {
    private lateinit var manager: NotificationManager
    private lateinit var notifications: ReminderNotifications
    private val occurrence = DoseOccurrence(
        medicationId = 7,
        doseTimeId = 11,
        scheduledFor = Instant.parse("2026-11-13T08:00:00Z"),
        zoneId = ZoneId.of("Asia/Shanghai"),
    )

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        notifications = ReminderNotifications(context).also { it.createChannels() }
    }

    @Test
    fun preAlarmActionsOpenDetailWithTheRequestedStatus() {
        notifications.showReminder(occurrence, alias = null, dosage = "1 tablet", stage = ReminderStage.PRE)

        val actions = manager.activeNotifications.single().notification.actions
        assertEquals(2, actions.size)
        actions.forEach { action ->
            val pending = action.actionIntent
            assertTrue("PRE action must open the Activity", shadowOf(pending).isActivity)
            val saved = shadowOf(pending).savedIntent
            assertEquals(MainActivity::class.java.name, saved.component?.className)
            assertTrue(saved.getBooleanExtra(ReminderNotifications.EXTRA_MODERN_NOTIFICATION, false))
            val status = saved.getStringExtra(MainActivity.EXTRA_REQUESTED_STATUS)
            assertNotNull(status)
            assertTrue(saved.data.toString().endsWith("/$status"))
        }
    }

    @Test
    fun dueAlarmActionsStillRecordThroughTheReceiver() {
        notifications.showReminder(occurrence, alias = null, dosage = "1 tablet", stage = ReminderStage.DUE)

        manager.activeNotifications.single().notification.actions.forEach { action ->
            val pending = action.actionIntent
            assertTrue("DUE action must keep the receiver path", shadowOf(pending).isBroadcast)
            assertEquals(ReminderActionReceiver::class.java.name, shadowOf(pending).savedIntent.component?.className)
        }
    }
}
