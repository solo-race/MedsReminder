package com.example.medicationreminder.domain.scheduling

import com.example.medicationreminder.domain.model.ScheduledDose
import com.example.medicationreminder.domain.model.ScheduleMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextDoseCalculatorTest {
    @Test
    fun `returns later dose on the same selected day`() {
        val result = NextDoseCalculator.nextOccurrence(
            time = LocalTime.of(14, 30),
            weekdays = setOf(DayOfWeek.MONDAY),
            zoneId = ZoneId.of("Asia/Shanghai"),
            now = Instant.parse("2025-01-06T04:00:00Z"), // Monday noon in Shanghai
        )

        assertEquals(Instant.parse("2025-01-06T06:30:00Z"), result)
    }

    @Test
    fun `moves to the next selected weekday after a passed dose`() {
        val result = NextDoseCalculator.nextOccurrence(
            time = LocalTime.of(8, 0),
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            zoneId = ZoneId.of("UTC"),
            now = Instant.parse("2025-01-06T09:00:00Z"),
        )

        assertEquals(Instant.parse("2025-01-08T08:00:00Z"), result)
    }

    @Test
    fun `uses the schedule manual zone rather than device zone`() {
        val result = NextDoseCalculator.nextOccurrence(
            time = LocalTime.of(8, 0),
            weekdays = DayOfWeek.entries.toSet(),
            zoneId = ZoneId.of("America/New_York"),
            now = Instant.parse("2025-01-06T12:30:00Z"),
        )

        assertEquals(Instant.parse("2025-01-06T13:00:00Z"), result)
    }

    @Test
    fun `resolves a daylight saving gap to the next valid local time`() {
        val result = NextDoseCalculator.nextOccurrence(
            time = LocalTime.of(2, 30),
            weekdays = setOf(DayOfWeek.SUNDAY),
            zoneId = ZoneId.of("America/New_York"),
            now = Instant.parse("2025-03-09T05:00:00Z"),
        )

        assertEquals(Instant.parse("2025-03-09T07:30:00Z"), result)
    }

    @Test
    fun `returns no occurrence when no weekday is selected`() {
        val result = NextDoseCalculator.nextOccurrence(
            time = LocalTime.NOON,
            weekdays = emptySet(),
            zoneId = ZoneId.of("UTC"),
            now = Instant.parse("2025-01-01T00:00:00Z"),
        )

        assertNull(result)
    }

    @Test
    fun `returns no overdue occurrence when today's selected dose is still upcoming`() {
        val result = NextDoseCalculator.mostRecentOverdueOccurrence(
            time = LocalTime.of(14, 30),
            weekdays = setOf(DayOfWeek.MONDAY),
            zoneId = ZoneId.of("Asia/Shanghai"),
            now = Instant.parse("2025-01-06T04:00:00Z"),
        )

        assertNull(result)
    }

    @Test
    fun `includes an overdue occurrence at the exact boundary`() {
        val result = NextDoseCalculator.mostRecentOverdueOccurrence(
            time = LocalTime.of(14, 30),
            weekdays = setOf(DayOfWeek.MONDAY),
            zoneId = ZoneId.of("Asia/Shanghai"),
            now = Instant.parse("2025-01-06T06:30:00Z"),
        )

        assertEquals(Instant.parse("2025-01-06T06:30:00Z"), result)
    }

    @Test
    fun `returns no overdue occurrence on a non-selected weekday`() {
        val result = NextDoseCalculator.mostRecentOverdueOccurrence(
            time = LocalTime.of(14, 30),
            weekdays = setOf(DayOfWeek.TUESDAY),
            zoneId = ZoneId.of("Asia/Shanghai"),
            now = Instant.parse("2025-01-06T06:30:00Z"),
        )

        assertNull(result)
    }

    @Test
    fun `honors the manual zone when finding an overdue occurrence`() {
        val result = NextDoseCalculator.mostRecentOverdueOccurrence(
            time = LocalTime.of(8, 0),
            weekdays = setOf(DayOfWeek.MONDAY),
            zoneId = ZoneId.of("UTC"),
            now = Instant.parse("2025-01-06T09:00:00Z"),
        )

        assertEquals(Instant.parse("2025-01-06T08:00:00Z"), result)
    }
    @Test
    fun intervalTwelveHoursKeepsAnchorAndIsStrictAtBoundaries() {
        val dose = intervalDose(720)
        val anchor = Instant.ofEpochMilli(dose.anchorEpochMillis!!)
        assertEquals(anchor, NextDoseCalculator.nextOccurrence(dose, anchor.minusSeconds(1)))
        assertNull(NextDoseCalculator.mostRecentOverdueOccurrence(dose, anchor.minusSeconds(1)))
        val evening = anchor.plusSeconds(12 * 3600)
        assertEquals(evening, NextDoseCalculator.nextOccurrence(dose, anchor))
        assertEquals(anchor, NextDoseCalculator.mostRecentOverdueOccurrence(dose, anchor))
        assertEquals(evening, NextDoseCalculator.nextOccurrence(dose, anchor.plusNanos(1)))
        assertEquals(evening, NextDoseCalculator.mostRecentOverdueOccurrence(dose, evening.plusNanos(1)))
        assertEquals(anchor.plusSeconds(24 * 3600), NextDoseCalculator.nextOccurrence(dose, evening))
    }

    @Test
    fun tenHourIntervalCrossesDaysAndTravelDoesNotChangeItsInstants() {
        val dose = intervalDose(600)
        val anchor = Instant.ofEpochMilli(dose.anchorEpochMillis!!)
        val now = anchor.plusSeconds(23 * 3600)
        val expected = anchor.plusSeconds(30 * 3600)
        assertEquals(expected, NextDoseCalculator.nextOccurrence(dose, now))
        assertEquals(anchor.plusSeconds(20 * 3600), NextDoseCalculator.mostRecentOverdueOccurrence(dose, now))
        assertEquals(expected, NextDoseCalculator.nextOccurrence(dose.copy(zoneId = ZoneId.of("America/New_York")), now))
    }

    @Test
    fun intervalPreservesSubsecondAnchorAndRejectsUnrepresentableSuccessor() {
        val dose = intervalDose(1).copy(anchorEpochMillis = 123L)
        assertEquals(Instant.ofEpochMilli(60_123), NextDoseCalculator.nextOccurrence(dose, Instant.ofEpochMilli(124)))
        assertEquals(Instant.ofEpochMilli(123), NextDoseCalculator.mostRecentOverdueOccurrence(dose, Instant.ofEpochMilli(124)))
        assertNull(NextDoseCalculator.nextOccurrence(dose.copy(intervalMinutes = 0), Instant.EPOCH))
        assertNull(NextDoseCalculator.nextOccurrence(dose.copy(intervalMinutes = Long.MAX_VALUE), Instant.EPOCH))
        assertNull(NextDoseCalculator.nextOccurrence(dose.copy(anchorEpochMillis = Long.MAX_VALUE), Instant.ofEpochMilli(Long.MAX_VALUE)))
    }

    private fun intervalDose(minutes: Long) = ScheduledDose(
        medicationId = 1, medicationName = "Interval", dosageText = "0.5 tablet",
        scheduleId = 2, doseTimeId = 3, time = LocalTime.MIDNIGHT,
        weekdays = emptySet(), zoneId = ZoneId.of("UTC"), medicationAlias = null,
        scheduleMode = ScheduleMode.INTERVAL, intervalMinutes = minutes,
        anchorEpochMillis = Instant.parse("2026-11-03T08:00:00Z").toEpochMilli(),
    )

}

