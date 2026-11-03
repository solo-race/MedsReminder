package com.example.medicationreminder.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.room.Room
import com.example.medicationreminder.data.local.MedicationDatabase
import com.example.medicationreminder.data.repository.RoomMedicationRepository
import com.example.medicationreminder.data.settings.AppPreferences
import com.example.medicationreminder.domain.model.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class PendingReminderDeliveryTest {
    private lateinit var database: MedicationDatabase
    private lateinit var repository: RoomMedicationRepository
    private lateinit var notifications: ReminderNotifications
    private lateinit var preferences: AppPreferences
    private lateinit var recovery: MissedReminderReposter
    private lateinit var manager: NotificationManager
    private lateinit var dose: ScheduledDose
    private val due = Instant.parse("2026-11-03T08:00:00Z")

    @Before fun setUp() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        notifications = ReminderNotifications(context).also { it.createChannels() }
        preferences = AppPreferences(context)
        preferences.setReminderSoundEnabled(true)
        database = Room.inMemoryDatabaseBuilder(context, MedicationDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomMedicationRepository(database)
        recovery = MissedReminderReposter(repository, notifications, preferences)
        repository.saveMedication(MedicationDraft(name = "Test", dosageText = "1 tablet", note = "", enabled = true,
            weekdays = emptySet(), timeZoneMode = TimeZoneMode.MANUAL, manualZoneId = "UTC", times = emptyList(),
            scheduleMode = ScheduleMode.INTERVAL, intervalMinutes = 60, anchorEpochMillis = due.toEpochMilli()))
        dose = repository.activeScheduledDoses().single()
    }
    @After fun tearDown() { manager.cancelAll(); database.close() }

    @Test fun preDueAndNextOccurrenceHaveStableDistinctNotificationAndPendingIntentIdentities() = runBlocking {
        val first = dose.occurrenceAt(due)
        val second = dose.occurrenceAt(due.plusSeconds(3600))
        recovery.deliverOccurrence(first, ReminderStage.PRE, alert = true, now = due.minusSeconds(300))
        val before = manager.activeNotifications.single()
        assertTrue(before.notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        recovery.deliverOccurrence(first, ReminderStage.DUE, alert = true, now = due)
        val after = manager.activeNotifications.single()
        assertEquals(before.tag, after.tag)
        assertFalse(after.notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        recovery.deliverOccurrence(second, ReminderStage.PRE, alert = true, now = due.plusSeconds(3300))
        val both = manager.activeNotifications
        assertEquals(2, both.size)
        assertNotEquals(both[0].notification.contentIntent, both[1].notification.contentIntent)
        assertNotEquals(both[0].notification.actions[0].actionIntent, both[1].notification.actions[0].actionIntent)
        notifications.cancelReminder(first)
        assertEquals(ReminderNotifications.occurrenceTag(second), manager.activeNotifications.single().tag)
    }

    @Test fun restoreIsSilentDoesNotConsumeDueTransitionAndDoesNotNeedRepostOptIn() = runBlocking {
        val occurrence = dose.occurrenceAt(due)
        preferences.setRepostMissedReminders(false)
        recovery.deliverOccurrence(occurrence, ReminderStage.PRE, alert = true, now = due.minusSeconds(300))
        manager.cancelAll()
        recovery.restorePending(due.plusSeconds(1))
        assertEquals(1, manager.activeNotifications.size)
        assertTrue(manager.activeNotifications.single().notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(ReminderStage.PRE, repository.pendingReminders().single().stage)
        recovery.deliverOccurrence(occurrence, ReminderStage.DUE, alert = true, now = due.plusSeconds(1))
        assertEquals(ReminderStage.DUE, repository.pendingReminders().single().stage)
        assertTrue(manager.activeNotifications.single().notification.flags and Notification.FLAG_ONLY_ALERT_ONCE == 0)
        recovery.deliverOccurrence(occurrence, ReminderStage.PRE, alert = true, now = due.plusSeconds(2))
        assertEquals(ReminderStage.DUE, repository.pendingReminders().single().stage)
        assertTrue(manager.activeNotifications.single().notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test fun dismissAtDueKeepsPreUntilTheActualDueDelivery() = runBlocking {
        val occurrence = dose.occurrenceAt(due)
        recovery.deliverOccurrence(occurrence, ReminderStage.PRE, alert = true, now = due.minusSeconds(300))
        assertEquals(ReminderStage.PRE, repository.pendingReminders().single().stage)
        manager.cancelAll() // The user clears PRE after 08:00, before DUE receiver execution.
        recovery.restoreOccurrence(occurrence, now = due.plusSeconds(1))
        assertEquals(ReminderStage.PRE, repository.pendingReminders().single().stage)
        val silent = manager.activeNotifications.single().notification
        assertFalse(silent.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(silent.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        recovery.deliverOccurrence(occurrence, ReminderStage.DUE, alert = true, now = due.plusSeconds(2))
        assertEquals(ReminderStage.DUE, repository.pendingReminders().single().stage)
        assertTrue(manager.activeNotifications.single().notification.flags and Notification.FLAG_ONLY_ALERT_ONCE == 0)
    }

    @Test fun soundPreferencePersistsAndRefreshUsesSilentChannelWithoutDroppingPending() = runBlocking {
        assertTrue(preferences.reminderSoundEnabled.first())
        val occurrence = dose.occurrenceAt(due)
        recovery.deliverOccurrence(occurrence, ReminderStage.PRE, alert = true, now = due.minusSeconds(300))
        preferences.setReminderSoundEnabled(false)
        assertFalse(AppPreferences(RuntimeEnvironment.getApplication()).reminderSoundEnabled.first())
        recovery.restorePending(due.minusSeconds(100))
        val notification = manager.activeNotifications.single().notification
        assertEquals(ReminderNotifications.SILENT_CHANNEL, notification.channelId)
        assertEquals(0, notification.defaults and Notification.DEFAULT_SOUND)
        assertEquals(1, repository.pendingReminders().size)
    }

    @Test fun legacyNotificationIsRemovedOnlyAfterItsExplicitOccurrenceIsConverted() = runBlocking {
        val occurrence = dose.occurrenceAt(due)
        val legacyId = ReminderScheduler.requestCode(dose.doseTimeId, 100)
        manager.notify(legacyId, Notification.Builder(RuntimeEnvironment.getApplication(), ReminderNotifications.REMINDER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).build())
        recovery.deliverOccurrence(occurrence, ReminderStage.DUE, alert = false, now = due)
        assertEquals(2, manager.activeNotifications.size)
        recovery.deliverOccurrence(occurrence, ReminderStage.DUE, alert = false, legacy = true, now = due)
        assertEquals(1, manager.activeNotifications.size)
        repository.decideDose(occurrence, DoseStatus.TAKEN)
        recovery.restorePending(due)
        recovery.deliverOccurrence(occurrence, ReminderStage.DUE, alert = true, now = due)
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
