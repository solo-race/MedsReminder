package com.example.medicationreminder.ui

import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseTime
import com.example.medicationreminder.domain.model.Medication
import com.example.medicationreminder.domain.model.MedicationPlan
import com.example.medicationreminder.domain.model.MedicationSchedule
import com.example.medicationreminder.domain.model.TimeZoneMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationDetailLogicTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val plan = MedicationPlan(
        medication = Medication(
            id = 10L,
            name = "Medication",
            dosageText = "1 tablet",
            note = "",
            photoPath = null,
            enabled = true,
            alias = null,
        ),
        schedule = MedicationSchedule(
            id = 20L,
            medicationId = 10L,
            weekdays = DayOfWeek.entries.toSet(),
            timeZoneMode = TimeZoneMode.MANUAL,
            manualZoneId = zone.id,
        ),
        times = listOf(DoseTime(id = 30L, scheduleId = 20L, time = LocalTime.of(8, 0), enabled = true)),
    )

    @Test
    fun explicitOccurrenceWinsOverCalculatedUpcomingOccurrence() {
        val explicit = occurrence("2026-09-07T00:00:00Z")
        val upcoming = occurrence("2026-09-08T00:00:00Z")

        assertSame(explicit, preferredDetailOccurrence(explicit, upcoming))
    }

    @Test
    fun matchingOccurrenceIsActionableForCurrentPlan() {
        assertTrue(isOccurrenceCompatibleWithPlan(plan, occurrence("2026-09-07T00:00:00Z")))
    }

    @Test
    fun deletedDoseSlotMakesNotificationOccurrenceStale() {
        val withoutSlot = plan.copy(times = emptyList())

        assertFalse(isOccurrenceCompatibleWithPlan(withoutSlot, occurrence("2026-09-07T00:00:00Z")))
    }

    @Test
    fun scheduleZoneChangeMakesOldNotificationOccurrenceStale() {
        val moved = plan.copy(
            schedule = plan.schedule.copy(manualZoneId = "Pacific/Honolulu"),
        )

        assertFalse(isOccurrenceCompatibleWithPlan(moved, occurrence("2026-09-07T00:00:00Z")))
    }

    private fun occurrence(instant: String) = DoseOccurrence(
        medicationId = 10L,
        doseTimeId = 30L,
        scheduledFor = Instant.parse(instant),
        zoneId = zone,
    )
}
