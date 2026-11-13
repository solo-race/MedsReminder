package com.example.medicationreminder.ui

import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MedicationEarlyDecisionTest {
    private val occurrence = DoseOccurrence(
        medicationId = 4,
        doseTimeId = 13,
        scheduledFor = Instant.parse("2026-11-13T08:00:00Z"),
        zoneId = ZoneId.of("Asia/Shanghai"),
    )

    @Test
    fun preNotificationRequestOpensOnDetailAndIsNotConsumedByDisplay() {
        val viewModel = viewModel()
        val entry = ReminderEntry(4, occurrence, DoseStatus.TAKEN)

        viewModel.onReminderIntent(entry, reapplied = false)
        assertEquals(entry, viewModel.reminderEntryOpened())

        val pending = PendingEarlyDecision(occurrence, DoseStatus.TAKEN)
        assertEquals(pending, viewModel.pendingEarlyDecision.value)
        assertEquals(pending, viewModel.pendingEarlyDecision.value)
    }

    @Test
    fun directContentTapDropsAnOlderEarlyRequest() {
        val viewModel = viewModel()
        viewModel.requestEarlyDecision(occurrence, DoseStatus.SKIPPED)

        viewModel.onReminderIntent(ReminderEntry(4, occurrence), reapplied = false)
        viewModel.reminderEntryOpened()

        assertNull(viewModel.pendingEarlyDecision.value)
    }

    @Test
    fun clearingOnlyRetiresTheMatchingRequest() {
        val viewModel = viewModel()
        val first = PendingEarlyDecision(occurrence, DoseStatus.TAKEN)
        val other = PendingEarlyDecision(occurrence.copy(doseTimeId = 14), DoseStatus.SKIPPED)

        viewModel.requestEarlyDecision(first.occurrence, first.status)
        viewModel.clearPendingEarlyDecision(other)
        assertEquals(first, viewModel.pendingEarlyDecision.value)

        viewModel.clearPendingEarlyDecision(first)
        assertNull(viewModel.pendingEarlyDecision.value)
    }

    @Test
    fun openingDetailFromHomeClearsTheEarlyRequest() {
        val viewModel = viewModel()
        viewModel.requestEarlyDecision(occurrence, DoseStatus.TAKEN)

        viewModel.clearReminderOccurrence()

        assertNull(viewModel.pendingEarlyDecision.value)
    }

    private fun viewModel(): MedicationViewModel =
        MedicationViewModel(RuntimeEnvironment.getApplication())
}
