package com.example.medicationreminder.reminders

import android.Manifest
import android.app.NotificationManager
import androidx.room.Room
import com.example.medicationreminder.MainActivity
import com.example.medicationreminder.data.local.MedicationDatabase
import com.example.medicationreminder.data.repository.MedicationRepository
import com.example.medicationreminder.data.repository.RoomMedicationRepository
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.MedicationDraft
import com.example.medicationreminder.domain.model.TimeZoneMode
import com.example.medicationreminder.reminders.ReminderActionReceiver.Companion.toDoseOccurrenceOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MissedReminderReposterIntegrationTest {
    private lateinit var database: MedicationDatabase
    private lateinit var repository: RoomMedicationRepository
    private lateinit var notifications: ReminderNotifications
    private lateinit var manager: NotificationManager
    private lateinit var occurrence: DoseOccurrence
    private val now = Instant.parse("2026-09-07T04:00:00Z")

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        notifications = ReminderNotifications(context)
        notifications.createChannels()
        database = Room.inMemoryDatabaseBuilder(context, MedicationDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomMedicationRepository(database)
        runBlocking {
            repository.saveMedication(
                MedicationDraft(
                    name = "Medication",
                    alias = "Morning reminder",
                    dosageText = "1 tablet",
                    note = "",
                    enabled = true,
                    weekdays = DayOfWeek.entries.toSet(),
                    timeZoneMode = TimeZoneMode.MANUAL,
                    manualZoneId = "Asia/Shanghai",
                    times = listOf(LocalTime.of(8, 0)),
                ),
            )
            occurrence = repository.activeScheduledDoses().single()
                .occurrenceAt(Instant.parse("2026-09-07T00:00:00Z"))
        }
    }

    @After
    fun tearDown() {
        manager.cancelAll()
        database.close()
    }

    @Test
    fun restoredNotificationPreservesExactOverdueOccurrenceAcrossAllIntents() = runBlocking {
        MissedReminderReposter(repository, notifications).repostUndecidedOverdue(now)

        val notification = manager.activeNotifications.single().notification
        val content = shadowOf(notification.contentIntent).savedIntent
        assertEquals(occurrence.medicationId, content.getLongExtra(MainActivity.EXTRA_MEDICATION_ID, -1))
        assertEquals(occurrence.doseTimeId, content.getLongExtra(MainActivity.EXTRA_DOSE_TIME_ID, -1))
        assertEquals(occurrence.scheduledFor.toEpochMilli(), content.getLongExtra(MainActivity.EXTRA_SCHEDULED_FOR, -1))
        assertEquals(occurrence.zoneId.id, content.getStringExtra(MainActivity.EXTRA_ZONE_ID))
        assertEquals(2, notification.actions.size)
        notification.actions.forEach { action ->
            assertEquals(occurrence, shadowOf(action.actionIntent).savedIntent.toDoseOccurrenceOrNull())
        }
        val dismissed = shadowOf(notification.deleteIntent).savedIntent
        assertEquals(occurrence.medicationId, dismissed.getLongExtra(ReminderDismissReceiver.EXTRA_MEDICATION_ID, -1))
        assertEquals(occurrence.doseTimeId, dismissed.getLongExtra(ReminderDismissReceiver.EXTRA_DOSE_TIME_ID, -1))
        assertEquals(occurrence.scheduledFor.toEpochMilli(), dismissed.getLongExtra(ReminderDismissReceiver.EXTRA_SCHEDULED_FOR, -1))
        assertEquals(occurrence.zoneId.id, dismissed.getStringExtra(ReminderDismissReceiver.EXTRA_ZONE_ID))
    }

    @Test
    fun persistedDecisionPreventsRestoration() = runBlocking {
        repository.decideDose(occurrence, DoseStatus.TAKEN)

        MissedReminderReposter(repository, notifications).repostUndecidedOverdue(now)

        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun decisionCommittedAfterPlanningPreventsPosting() = runBlocking {
        verifyDecisionDuringRestoration(decisionCheck = 2)
    }

    @Test
    fun decisionCommittedDuringPostingCancelsRestoredNotification() = runBlocking {
        verifyDecisionDuringRestoration(decisionCheck = 3)
    }

    private suspend fun verifyDecisionDuringRestoration(decisionCheck: Int) {
        var checks = 0
        val racingRepository = object : MedicationRepository by repository {
            override suspend fun hasDoseDecisionOnLocalDay(doseTimeId: Long, scheduledFor: Instant, zoneId: ZoneId): Boolean {
                checks++
                if (checks == decisionCheck) {
                    assertEquals(if (decisionCheck == 3) 1 else 0, manager.activeNotifications.size)
                    repository.decideDose(occurrence, DoseStatus.SKIPPED)
                }
                return repository.hasDoseDecisionOnLocalDay(doseTimeId, scheduledFor, zoneId)
            }
        }

        MissedReminderReposter(racingRepository, notifications).repostUndecidedOverdue(now)

        assertEquals(decisionCheck, checks)
        assertEquals(0, manager.activeNotifications.size)
    }
}
