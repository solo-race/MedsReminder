package com.example.medicationreminder.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DoseOccurrenceTest {
    @Test
    fun occurrenceAt_preservesExactScheduledInstantAndScheduleZone() {
        val dose = ScheduledDose(
            medicationId = 41,
            medicationName = "Example",
            dosageText = "1 tablet",
            scheduleId = 7,
            doseTimeId = 99,
            time = LocalTime.of(8, 0),
            weekdays = setOf(DayOfWeek.MONDAY),
            zoneId = ZoneId.of("Asia/Singapore"),
            medicationAlias = null,
        )
        val scheduledFor = Instant.parse("2026-09-07T00:00:00Z")

        assertEquals(
            DoseOccurrence(
                medicationId = 41,
                doseTimeId = 99,
                scheduledFor = scheduledFor,
                zoneId = ZoneId.of("Asia/Singapore"),
            ),
            dose.occurrenceAt(scheduledFor),
        )
    }
}
