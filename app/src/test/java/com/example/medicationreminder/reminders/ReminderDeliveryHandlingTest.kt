package com.example.medicationreminder.reminders

import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.ScheduledDose
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderDeliveryHandlingTest {
    private val dose = ScheduledDose(
        medicationId = 10,
        medicationName = "Medication",
        dosageText = "1 tablet",
        scheduleId = 20,
        doseTimeId = 30,
        time = LocalTime.of(8, 0),
        weekdays = DayOfWeek.entries.toSet(),
        zoneId = ZoneId.of("UTC"),
        medicationAlias = null,
    )
    private val occurrence = DoseOccurrence(
        medicationId = dose.medicationId,
        doseTimeId = dose.doseTimeId,
        scheduledFor = Instant.parse("2026-09-07T08:00:00Z"),
        zoneId = dose.zoneId,
    )

    @Test
    fun alreadyDecidedOccurrenceIsSuppressedAndAdvancesOnce() = runBlocking {
        var showCount = 0
        var cancelCount = 0
        var scheduleCount = 0
        var scheduledBoundary: Instant? = null

        handleFiredOccurrence(
            occurrence = occurrence,
            activeDoses = listOf(dose),
            isDecided = { true },
            showReminder = { showCount++ },
            cancelVisibleReminder = { cancelCount++ },
            scheduleFollowing = { _, boundary ->
                scheduleCount++
                scheduledBoundary = boundary
            },
        )

        assertEquals(0, showCount)
        assertEquals(1, cancelCount)
        assertEquals(1, scheduleCount)
        assertEquals(occurrence.scheduledFor, scheduledBoundary)
    }

    @Test
    fun normalFireShowsOnceAndAdvancesOnce() = runBlocking {
        var showCount = 0
        var cancelCount = 0
        var scheduleCount = 0

        handleFiredOccurrence(
            occurrence = occurrence,
            activeDoses = listOf(dose),
            isDecided = { false },
            showReminder = { showCount++ },
            cancelVisibleReminder = { cancelCount++ },
            scheduleFollowing = { _, _ -> scheduleCount++ },
        )

        assertEquals(1, showCount)
        assertEquals(0, cancelCount)
        assertEquals(1, scheduleCount)
    }

    @Test
    fun decisionRacingWithNotifyCancelsNewlyPostedReminder() = runBlocking {
        var decisionChecks = 0
        var showCount = 0
        var cancelCount = 0
        var scheduleCount = 0

        handleFiredOccurrence(
            occurrence = occurrence,
            activeDoses = listOf(dose),
            isDecided = {
                decisionChecks++
                decisionChecks >= 2
            },
            showReminder = { showCount++ },
            cancelVisibleReminder = { cancelCount++ },
            scheduleFollowing = { _, _ -> scheduleCount++ },
        )

        assertEquals(2, decisionChecks)
        assertEquals(1, showCount)
        assertEquals(1, cancelCount)
        assertEquals(1, scheduleCount)
    }

    @Test
    fun staleOccurrenceDoesNothing() = runBlocking {
        var showCount = 0
        var cancelCount = 0
        var scheduleCount = 0
        val staleDose = dose.copy(time = LocalTime.of(9, 0))

        handleFiredOccurrence(
            occurrence = occurrence,
            activeDoses = listOf(staleDose),
            isDecided = { false },
            showReminder = { showCount++ },
            cancelVisibleReminder = { cancelCount++ },
            scheduleFollowing = { _, _ -> scheduleCount++ },
        )

        assertEquals(0, showCount)
        assertEquals(0, cancelCount)
        assertEquals(0, scheduleCount)
    }
}
