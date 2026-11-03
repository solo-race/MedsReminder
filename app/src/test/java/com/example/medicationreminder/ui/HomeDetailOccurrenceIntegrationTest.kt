package com.example.medicationreminder.ui

import android.Manifest
import android.app.NotificationManager
import com.example.medicationreminder.MainActivity
import com.example.medicationreminder.MedicationReminderApplication
import com.example.medicationreminder.domain.model.DoseDecisionResult
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.MedicationDraft
import com.example.medicationreminder.domain.model.TimeZoneMode
import com.example.medicationreminder.navigationDoseOccurrenceOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real Room/notification payload/ViewModel integration with synthetic data; not Compose UI clicks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeDetailOccurrenceIntegrationTest {
    private val app get() = RuntimeEnvironment.getApplication() as MedicationReminderApplication
    private val container get() = app.container
    private val repository get() = container.repository
    private val now = Instant.parse("2026-10-24T08:05:00Z")

    @Test fun notificationTakenThenHomeSecondMedicationKeepsSamePendingSlot() = runBlocking {
        val firstId = repository.saveMedication(draft("Synthetic A")).medicationId
        val secondId = repository.saveMedication(draft("Synthetic B")).medicationId
        val first = morning(firstId)
        val second = morning(secondId)
        val viewModel = MedicationViewModel(app)
        val firstEntry = notificationEntry(first)
        viewModel.onReminderIntent(firstEntry, reapplied = false)
        assertEquals(firstEntry, viewModel.reminderEntryOpened())
        assertEquals(first, viewModel.reminderOccurrence.value)
        assertEquals(DoseDecisionResult.Recorded(DoseStatus.TAKEN), container.doseDecisionUseCase(first, DoseStatus.TAKEN))
        val historyAfterTakingFirst = repository.observeDoseHistory().first()
        assertEquals(1, historyAfterTakingFirst.size)

        // These are the same state transitions as Home's route callback, without UI clicks.
        viewModel.clearReminderOccurrence()
        assertNull(viewModel.reminderOccurrence.value)
        repeat(2) { assertEquals(second, viewModel.nextActionableOccurrence(secondId, now)) }
        assertEquals(historyAfterTakingFirst, repository.observeDoseHistory().first())
        assertEquals(DoseDecisionResult.AlreadyDecided(DoseStatus.TAKEN), container.doseDecisionUseCase(first, DoseStatus.SKIPPED))
        assertEquals(historyAfterTakingFirst, repository.observeDoseHistory().first())
        assertEquals(second, viewModel.nextActionableOccurrence(secondId, now))

        val secondEntry = notificationEntry(second)
        viewModel.onReminderIntent(secondEntry, reapplied = false)
        viewModel.reminderEntryOpened()
        assertEquals(second, viewModel.reminderOccurrence.value)
        viewModel.onReminderIntent(secondEntry, reapplied = true)
        assertNull(viewModel.reminderEntry.value)
        assertEquals(second, viewModel.reminderOccurrence.value)
        assertEquals(historyAfterTakingFirst, repository.observeDoseHistory().first())
    }

    @Test fun futureSlotOnlySelectedWhenNoPendingDueAndNavigationDoesNotWriteHistory() = runBlocking {
        val id = repository.saveMedication(draft("Synthetic")).medicationId
        val due = morning(id)
        val viewModel = MedicationViewModel(app)
        assertEquals(due, viewModel.nextActionableOccurrence(id, Instant.parse("2026-10-24T07:59:59Z")))
        assertEquals(due, viewModel.nextActionableOccurrence(id, due.scheduledFor))
        assertEquals(due, viewModel.nextActionableOccurrence(id, now))
        assertTrue(repository.observeDoseHistory().first().isEmpty())
        assertEquals(DoseDecisionResult.Recorded(DoseStatus.SKIPPED), container.doseDecisionUseCase(due, DoseStatus.SKIPPED))
        val next = repository.activeScheduledDoses().single { it.medicationId == id && it.time == LocalTime.of(20, 0) }
            .occurrenceAt(Instant.parse("2026-10-24T20:00:00Z"))
        assertEquals(next, viewModel.nextActionableOccurrence(id, now))
        assertEquals(1, repository.observeDoseHistory().first().size)
    }

    @Test fun mostRecentPendingTodayWinsButOlderExplicitNotificationStaysPinned() = runBlocking {
        val id = repository.saveMedication(draft("Synthetic").copy(times = listOf(LocalTime.of(8, 0), LocalTime.NOON, LocalTime.of(20, 0)))).medicationId
        val doses = repository.activeScheduledDoses()
        val noon = doses.single { it.time == LocalTime.NOON }.occurrenceAt(Instant.parse("2026-10-24T12:00:00Z"))
        val viewModel = MedicationViewModel(app)
        val afternoon = Instant.parse("2026-10-24T13:00:00Z")
        assertEquals(noon, viewModel.nextActionableOccurrence(id, afternoon))
        container.doseDecisionUseCase(noon, DoseStatus.TAKEN)
        assertEquals(morning(id), viewModel.nextActionableOccurrence(id, afternoon))
        val old = morning(id).copy(scheduledFor = Instant.parse("2026-10-23T08:00:00Z"))
        viewModel.onReminderIntent(notificationEntry(old), reapplied = false)
        viewModel.reminderEntryOpened()
        assertEquals(old, viewModel.reminderOccurrence.value)
        val history = repository.observeDoseHistory().first()
        viewModel.clearReminderOccurrence()
        assertEquals(morning(id).copy(scheduledFor = Instant.parse("2026-10-25T08:00:00Z")), viewModel.nextActionableOccurrence(id, Instant.parse("2026-10-25T00:00:00Z")))
        assertEquals(history, repository.observeDoseHistory().first())
    }

    @Test fun deviceZoneUsesLocalMorningInsteadOfLaterUtcSlot() = runBlocking {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val id = repository.saveMedication(draft("Synthetic device zone").copy(timeZoneMode = TimeZoneMode.DEVICE, manualZoneId = null)).medicationId
            val due = repository.activeScheduledDoses().single { it.time == LocalTime.of(8, 0) }
                .occurrenceAt(Instant.parse("2026-10-24T00:00:00Z"))
            assertEquals(due, MedicationViewModel(app).nextActionableOccurrence(id, Instant.parse("2026-10-24T00:05:00Z")))
            assertTrue(repository.observeDoseHistory().first().isEmpty())
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun disabledOrDeletedMedicationHasNoHomeActionAndDoesNotCreateDecisions() = runBlocking {
        val draft = draft("Synthetic disabled")
        val id = repository.saveMedication(draft).medicationId
        val viewModel = MedicationViewModel(app)
        val prior = morning(id)
        repository.saveMedication(draft.copy(id = id, enabled = false))
        assertNull(viewModel.nextActionableOccurrence(id, now))
        assertFalse(isOccurrenceCompatibleWithPlan(repository.observeMedicationPlans().first().single(), prior))
        repository.deleteMedication(id)
        assertNull(viewModel.nextActionableOccurrence(id, now))
        assertTrue(repository.observeDoseHistory().first().isEmpty())
    }

    private suspend fun morning(id: Long) = repository.activeScheduledDoses()
        .single { it.medicationId == id && it.time == LocalTime.of(8, 0) }
        .occurrenceAt(Instant.parse("2026-10-24T08:00:00Z"))

    private fun notificationEntry(occurrence: DoseOccurrence): ReminderEntry {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container.notifications.showReminder(occurrence, "Synthetic", "test")
        val notification = app.getSystemService(NotificationManager::class.java).activeNotifications
            .single { shadowOf(it.notification.contentIntent).savedIntent.getLongExtra(MainActivity.EXTRA_DOSE_TIME_ID, -1) == occurrence.doseTimeId }
        val intent = shadowOf(notification.notification.contentIntent).savedIntent
        val id = intent.getLongExtra(MainActivity.EXTRA_MEDICATION_ID, -1)
        return ReminderEntry(id, navigationDoseOccurrenceOrNull(id,
            intent.getLongExtra(MainActivity.EXTRA_DOSE_TIME_ID, -1),
            intent.getLongExtra(MainActivity.EXTRA_SCHEDULED_FOR, -1),
            intent.getStringExtra(MainActivity.EXTRA_ZONE_ID)))
    }

    private fun draft(name: String) = MedicationDraft(name = name, dosageText = "test", note = "", enabled = true,
        weekdays = DayOfWeek.entries.toSet(), timeZoneMode = TimeZoneMode.MANUAL, manualZoneId = "UTC",
        times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
}
