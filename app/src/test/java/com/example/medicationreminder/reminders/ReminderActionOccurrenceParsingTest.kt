package com.example.medicationreminder.reminders

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderActionOccurrenceParsingTest {
    @Test
    fun actionParsingPreservesExactInstantAndZone() {
        val scheduledFor = Instant.parse("2026-09-07T00:00:00Z")
        val occurrence = ReminderActionReceiver.doseOccurrenceOrNull(
            medicationId = 10,
            doseTimeId = 20,
            scheduledForEpochMillis = scheduledFor.toEpochMilli(),
            zoneIdValue = "Asia/Singapore",
        )

        requireNotNull(occurrence)
        assertEquals(10L, occurrence.medicationId)
        assertEquals(20L, occurrence.doseTimeId)
        assertEquals(scheduledFor, occurrence.scheduledFor)
        assertEquals(ZoneId.of("Asia/Singapore"), occurrence.zoneId)
    }

    @Test
    fun actionParsingFailsClosedForIncompleteOrMalformedIdentity() {
        assertNull(ReminderActionReceiver.doseOccurrenceOrNull(-1, 20, 1_000, "UTC"))
        assertNull(ReminderActionReceiver.doseOccurrenceOrNull(10, -1, 1_000, "UTC"))
        assertNull(ReminderActionReceiver.doseOccurrenceOrNull(10, 20, -1, "UTC"))
        assertNull(ReminderActionReceiver.doseOccurrenceOrNull(10, 20, 1_000, null))
        assertNull(ReminderActionReceiver.doseOccurrenceOrNull(10, 20, 1_000, "not/a-zone"))
    }

    @Test
    fun delayedActionStillTargetsOriginalScheduledInstant() {
        val eightAm = Instant.parse("2026-09-07T00:00:00Z")
        val occurrence = ReminderActionReceiver.doseOccurrenceOrNull(
            medicationId = 10,
            doseTimeId = 20,
            scheduledForEpochMillis = eightAm.toEpochMilli(),
            zoneIdValue = "Asia/Singapore",
        )

        requireNotNull(occurrence)
        assertEquals(eightAm, occurrence.scheduledFor)
    }
}
