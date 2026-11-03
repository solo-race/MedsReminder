package com.example.medicationreminder.ui

import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.ScheduledDose
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HomeDetailOccurrenceTest {
    private val morning = dose(1, 11, 8)
    private val noon = dose(1, 12, 12)
    private val evening = dose(1, 13, 20)

    @Test fun pendingMorningWinsOverFutureEvening() = runBlocking {
        assertEquals(at(morning, "2026-10-24T08:00:00Z"), select("2026-10-24T08:05:00Z"))
    }
    @Test fun exactDueBoundaryIsNotSkipped() = runBlocking {
        assertEquals(at(morning, "2026-10-24T08:00:00Z"), select("2026-10-24T08:00:00Z"))
    }
    @Test fun beforeFirstSlotKeepsUpcomingBehavior() = runBlocking {
        assertEquals(at(morning, "2026-10-24T08:00:00Z"), select("2026-10-24T07:59:59Z"))
    }
    @Test fun latestUndecidedDueSlotWinsOverOlderPendingSlot() = runBlocking {
        assertEquals(at(noon, "2026-10-24T12:00:00Z"), select("2026-10-24T13:00:00Z", listOf(morning, noon, evening)))
    }
    @Test fun decidedLatestSlotRevealsEarlierPendingSlotToday() = runBlocking {
        assertEquals(at(morning, "2026-10-24T08:00:00Z"), select("2026-10-24T13:00:00Z", listOf(morning, noon, evening), setOf(at(noon, "2026-10-24T12:00:00Z"))))
    }
    @Test fun allDueSlotsDecidedFallBackToFuture() = runBlocking {
        assertEquals(at(evening, "2026-10-24T20:00:00Z"), select("2026-10-24T13:00:00Z", listOf(morning, noon, evening), setOf(at(morning, "2026-10-24T08:00:00Z"), at(noon, "2026-10-24T12:00:00Z"))))
    }
    @Test fun alreadyDecidedFutureSlotIsSkipped() = runBlocking {
        assertEquals(at(evening, "2026-10-24T20:00:00Z"), select("2026-10-24T07:00:00Z", listOf(morning, evening), setOf(at(morning, "2026-10-24T08:00:00Z"))))
    }
    @Test fun previousDayPendingIsNotSynthesizedAtMidnight() = runBlocking {
        assertEquals(at(morning, "2026-10-25T08:00:00Z"), select("2026-10-25T00:00:00Z", listOf(morning)))
    }
    @Test fun nonScheduledDayDoesNotCreatePendingOccurrence() = runBlocking {
        val monday = morning.copy(weekdays = setOf(DayOfWeek.MONDAY))
        assertEquals(at(monday, "2026-10-26T08:00:00Z"), select("2026-10-24T13:00:00Z", listOf(monday)))
    }
    @Test fun otherMedicationDecisionCannotConsumeSameTimeSlot() = runBlocking {
        val second = dose(2, 21, 8)
        val decisions = setOf(at(morning, "2026-10-24T08:00:00Z"))
        assertEquals(at(second, "2026-10-24T08:00:00Z"), homeDetailOccurrence(listOf(morning, second, evening), 2, Instant.parse("2026-10-24T08:05:00Z")) { it in decisions })
    }
    @Test fun emptyOrUnrelatedSchedulesReturnNullWithoutDecisionReads() = runBlocking {
        assertNull(homeDetailOccurrence(listOf(morning), 99, Instant.parse("2026-10-24T08:05:00Z")) { fail("Unexpected read"); false })
        assertNull(homeDetailOccurrence(emptyList(), 1, Instant.parse("2026-10-24T08:05:00Z")) { fail("Unexpected read"); false })
        assertNull(homeDetailOccurrence(listOf(morning.copy(weekdays = emptySet())), 1, Instant.parse("2026-10-24T08:05:00Z")) { fail("Unexpected read"); false })
    }
    @Test fun scheduleLocalDayNotUtcDayDeterminesPendingWindow() = runBlocking {
        val east = morning.copy(zoneId = ZoneId.of("Asia/Shanghai"))
        assertEquals(at(east, "2026-10-24T00:00:00Z"), select("2026-10-24T00:05:00Z", listOf(east)))
        val west = morning.copy(zoneId = ZoneId.of("America/Los_Angeles"))
        assertEquals(at(west, "2026-10-23T15:00:00Z"), select("2026-10-24T01:00:00Z", listOf(west)))
    }
    @Test fun dstGapUsesSameForwardResolvedInstantAsScheduler() = runBlocking {
        val gap = morning.copy(time = LocalTime.of(2, 30), zoneId = ZoneId.of("America/New_York"))
        assertEquals(at(gap, "2026-03-08T07:30:00Z"), select("2026-03-08T07:35:00Z", listOf(gap)))
    }
    @Test fun dstOverlapKeepsEarlierOffsetIdentity() = runBlocking {
        val overlap = morning.copy(time = LocalTime.of(1, 30), zoneId = ZoneId.of("America/New_York"))
        assertEquals(at(overlap, "2026-11-01T05:30:00Z"), select("2026-11-01T06:35:00Z", listOf(overlap)))
    }
    private suspend fun select(now: String, doses: List<ScheduledDose> = listOf(morning, evening), decided: Set<DoseOccurrence> = emptySet()) =
        homeDetailOccurrence(doses, 1, Instant.parse(now)) { it in decided }
    private fun at(dose: ScheduledDose, instant: String) = dose.occurrenceAt(Instant.parse(instant))
    private fun dose(medicationId: Long, slot: Long, hour: Int) = ScheduledDose(medicationId, "Synthetic", "test", medicationId + 100, slot, LocalTime.of(hour, 0), DayOfWeek.entries.toSet(), ZoneId.of("UTC"), null)
}
