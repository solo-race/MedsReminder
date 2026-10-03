package com.example.medicationreminder.reminders

import com.example.medicationreminder.domain.model.ScheduledDose
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DecisionAwareSchedulingTest {
    @Test
    fun earlyDecisionSkipsUpcomingOccurrence() = runBlocking {
        val dose = dailyDose(LocalTime.of(8, 0), ZoneId.of("UTC"))
        val decided = Instant.parse("2026-09-07T08:00:00Z")

        val result = nextUndecidedOccurrence(
            dose = dose,
            after = Instant.parse("2026-09-07T07:30:00Z"),
            isDecided = { it.scheduledFor == decided },
        )

        assertEquals(Instant.parse("2026-09-08T08:00:00Z"), result?.scheduledFor)
    }

    @Test
    fun repeatedDecisionsAdvanceUntilFirstEligibleOccurrence() = runBlocking {
        val dose = dailyDose(LocalTime.of(8, 0), ZoneId.of("UTC"))
        val decided = setOf(
            Instant.parse("2026-09-07T08:00:00Z"),
            Instant.parse("2026-09-08T08:00:00Z"),
        )

        val result = nextUndecidedOccurrence(
            dose = dose,
            after = Instant.parse("2026-09-07T07:30:00Z"),
            isDecided = { it.scheduledFor in decided },
        )

        assertEquals(Instant.parse("2026-09-09T08:00:00Z"), result?.scheduledFor)
    }

    @Test
    fun firedBoundaryNeverReschedulesSameOccurrence() = runBlocking {
        val dose = dailyDose(LocalTime.of(8, 0), ZoneId.of("UTC"))

        val result = nextUndecidedOccurrence(
            dose = dose,
            after = Instant.parse("2026-09-07T08:00:00Z"),
            isDecided = { false },
        )

        assertEquals(Instant.parse("2026-09-08T08:00:00Z"), result?.scheduledFor)
    }

    @Test
    fun dstGapKeepsNextDoseCalculatorResolution() = runBlocking {
        val dose = weeklyDose(
            time = LocalTime.of(2, 30),
            zoneId = ZoneId.of("Europe/Berlin"),
            weekday = DayOfWeek.SUNDAY,
        )

        val result = nextUndecidedOccurrence(
            dose = dose,
            after = Instant.parse("2026-03-28T23:00:00Z"),
            isDecided = { false },
        )

        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), result?.scheduledFor)
    }

    @Test
    fun dstOverlapKeepsEarlierOffsetResolution() = runBlocking {
        val dose = weeklyDose(
            time = LocalTime.of(2, 30),
            zoneId = ZoneId.of("Europe/Berlin"),
            weekday = DayOfWeek.SUNDAY,
        )

        val result = nextUndecidedOccurrence(
            dose = dose,
            after = Instant.parse("2026-10-24T22:00:00Z"),
            isDecided = { false },
        )

        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), result?.scheduledFor)
    }

    private fun dailyDose(time: LocalTime, zoneId: ZoneId): ScheduledDose = ScheduledDose(
        medicationId = 10,
        medicationName = "Medication",
        dosageText = "1 tablet",
        scheduleId = 20,
        doseTimeId = 30,
        time = time,
        weekdays = DayOfWeek.entries.toSet(),
        zoneId = zoneId,
        medicationAlias = null,
    )

    private fun weeklyDose(time: LocalTime, zoneId: ZoneId, weekday: DayOfWeek): ScheduledDose =
        dailyDose(time, zoneId).copy(weekdays = setOf(weekday))
}
