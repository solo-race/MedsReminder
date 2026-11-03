package com.example.medicationreminder.reminders

import com.example.medicationreminder.domain.model.ReminderStage
import com.example.medicationreminder.domain.model.ScheduleMode
import com.example.medicationreminder.domain.model.ScheduledDose
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPhasePlanningTest {
    private val anchor = Instant.parse("2026-11-03T08:00:00Z")
    private val fixed = ScheduledDose(1, "Test", "1 tablet", 2, 3, LocalTime.of(8, 0),
        DayOfWeek.entries.toSet(), ZoneId.of("UTC"), null)
    private fun interval(minutes: Long) = fixed.copy(scheduleMode = ScheduleMode.INTERVAL,
        intervalMinutes = minutes, anchorEpochMillis = anchor.toEpochMilli(), weekdays = emptySet())

    @Test fun preAndDueKeepTheSameDoseInstantButHaveSeparateTriggers() = runBlocking {
        val now = anchor.minusSeconds(1800)
        val pre = nextPhaseOccurrence(fixed, ReminderStage.PRE, now) { false }!!
        val due = nextPhaseOccurrence(fixed, ReminderStage.DUE, now) { false }!!
        assertEquals(anchor, pre.scheduledFor)
        assertEquals(pre, due)
        assertEquals(anchor.minusSeconds(300), reminderTrigger(pre, ReminderStage.PRE))
        assertEquals(anchor, reminderTrigger(due, ReminderStage.DUE))
    }

    @Test fun oneMinuteIntervalsAdvancePreWithoutReplacingTheCurrentDue() = runBlocking {
        val dose = interval(1)
        val first = dose.occurrenceAt(anchor)
        val nextPre = nextPhaseOccurrence(dose, ReminderStage.PRE, anchor.minusSeconds(300)) { false }!!
        assertEquals(anchor.plusSeconds(60), nextPre.scheduledFor)
        assertTrue(reminderTrigger(nextPre, ReminderStage.PRE).isBefore(reminderTrigger(first, ReminderStage.DUE)))
    }

    @Test fun rebuildCapturesAllFiveUnprocessedOccurrencesInThePreWindow() = runBlocking {
        val dose = interval(1)
        val now = anchor.minusSeconds(1)
        val window = preWindowOccurrences(dose, now) { false }
        assertEquals(5, window.size)
        assertEquals(anchor, window.first().scheduledFor)
        assertEquals(anchor.plusSeconds(240), window.last().scheduledFor)
        val decided = window[2]
        assertEquals(window.filter { it != decided }, preWindowOccurrences(dose, now) { it == decided })
    }

    @Test fun silentDueRepostPreservesPreAndLeavesTheRealDueTransitionAvailable() {
        val repost = pendingStageForDelivery(ReminderStage.PRE, ReminderStage.DUE, alert = false, isDue = true)
        assertEquals(ReminderStage.PRE, repost)
        val actualDue = pendingStageForDelivery(repost, ReminderStage.DUE, alert = true, isDue = true)
        assertEquals(ReminderStage.DUE, actualDue)
        assertTrue(actualDue != repost)
        assertEquals(ReminderStage.DUE,
            pendingStageForDelivery(actualDue, ReminderStage.PRE, alert = false, isDue = true))
    }

    @Test fun earlyDecisionSuppressesBothPhasesAndLateHandlingUsesNow() = runBlocking {
        val dose = interval(720)
        for (phase in ReminderStage.entries) {
            val next = nextPhaseOccurrence(dose, phase, anchor.minusSeconds(600)) { it.scheduledFor == anchor }!!
            assertEquals(anchor.plusSeconds(12 * 3600), next.scheduledFor)
        }
        val late = anchor.plusSeconds(13 * 3600)
        for (phase in ReminderStage.entries) {
            val next = nextPhaseOccurrence(dose, phase, late) { false }!!
            assertTrue(reminderTrigger(next, phase).isAfter(late))
            assertEquals(anchor.plusSeconds(24 * 3600), next.scheduledFor)
        }
    }
}
