package com.example.medicationreminder.domain.scheduling

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
}
