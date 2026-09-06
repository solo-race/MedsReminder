package com.example.medicationreminder

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityOccurrenceParsingTest {
    @Test
    fun delayedNotificationOpenKeepsOriginalScheduledInstant() {
        val eightAm = Instant.parse("2026-09-07T00:00:00Z")
        val occurrence = navigationDoseOccurrenceOrNull(
            medicationId = 10,
            doseTimeId = 20,
            scheduledForEpochMillis = eightAm.toEpochMilli(),
            zoneIdValue = "Asia/Singapore",
        )

        requireNotNull(occurrence)
        assertEquals(10L, occurrence.medicationId)
        assertEquals(20L, occurrence.doseTimeId)
        assertEquals(eightAm, occurrence.scheduledFor)
        assertEquals(ZoneId.of("Asia/Singapore"), occurrence.zoneId)
    }

    @Test
    fun navigationParsingFailsClosedForIncompleteOrMalformedIdentity() {
        assertNull(navigationDoseOccurrenceOrNull(-1, 20, 1_000, "UTC"))
        assertNull(navigationDoseOccurrenceOrNull(10, -1, 1_000, "UTC"))
        assertNull(navigationDoseOccurrenceOrNull(10, 20, -1, "UTC"))
        assertNull(navigationDoseOccurrenceOrNull(10, 20, 1_000, null))
        assertNull(navigationDoseOccurrenceOrNull(10, 20, 1_000, "not/a-zone"))
    }
}
