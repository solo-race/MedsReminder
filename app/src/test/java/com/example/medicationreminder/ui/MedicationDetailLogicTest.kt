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
import org.junit.Assert.assertNotEquals
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
    fun returningFromEditInvalidatesRememberedDoseStateForRelevantPlanChanges() {
        val explicit = occurrence("2026-09-07T00:00:00Z")
        val originalKey = detailDoseStateKey(plan, explicit)
        val editedPlans = listOf(
            plan.copy(medication = plan.medication.copy(enabled = false)),
            plan.copy(times = listOf(plan.times.single().copy(time = LocalTime.of(9, 0)))),
            plan.copy(
                schedule = plan.schedule.copy(
                    weekdays = DayOfWeek.entries.toSet() - DayOfWeek.MONDAY,
                ),
            ),
            plan.copy(schedule = plan.schedule.copy(manualZoneId = "Pacific/Honolulu")),
        )

        editedPlans.forEach { editedPlan ->
            assertNotEquals(originalKey, detailDoseStateKey(editedPlan, explicit))
            assertFalse(isOccurrenceCompatibleWithPlan(editedPlan, explicit))
        }
    }

    @Test
    fun deletedDoseSlotMakesNotificationOccurrenceStale() {
        val withoutSlot = plan.copy(times = emptyList())

        assertFalse(isOccurrenceCompatibleWithPlan(withoutSlot, occurrence("2026-09-07T00:00:00Z")))
    }

    @Test
    fun disabledDoseSlotMakesNotificationOccurrenceStale() {
        val disabledSlot = plan.copy(times = plan.times.map { it.copy(enabled = false) })

        assertFalse(isOccurrenceCompatibleWithPlan(disabledSlot, occurrence("2026-09-07T00:00:00Z")))
    }

    @Test
    fun disabledMedicationMakesNotificationOccurrenceStale() {
        val disabledMedication = plan.copy(medication = plan.medication.copy(enabled = false))

        assertFalse(isOccurrenceCompatibleWithPlan(disabledMedication, occurrence("2026-09-07T00:00:00Z")))
    }

    @Test
    fun scheduleZoneChangeMakesOldNotificationOccurrenceStale() {
        val moved = plan.copy(
            schedule = plan.schedule.copy(manualZoneId = "Pacific/Honolulu"),
        )

        assertFalse(isOccurrenceCompatibleWithPlan(moved, occurrence("2026-09-07T00:00:00Z")))
    }

    @Test
    fun scheduleTimeChangeMakesOldNotificationOccurrenceStale() {
        val moved = plan.copy(times = listOf(plan.times.single().copy(time = LocalTime.of(9, 0))))

        assertFalse(isOccurrenceCompatibleWithPlan(moved, occurrence("2026-09-07T00:00:00Z")))
    }

    @Test
    fun scheduleWeekdayChangeMakesOldNotificationOccurrenceStale() {
        val mondayOccurrence = occurrence("2026-09-07T00:00:00Z")
        val withoutMonday = plan.copy(
            schedule = plan.schedule.copy(weekdays = DayOfWeek.entries.toSet() - DayOfWeek.MONDAY),
        )

        assertFalse(isOccurrenceCompatibleWithPlan(withoutMonday, mondayOccurrence))
    }

    @Test
    fun occurrenceTargetsExactDoseSlotWhenMedicationHasMultipleDailyTimes() {
        val multiSlot = plan.copy(
            times = listOf(
                plan.times.single(),
                DoseTime(id = 31L, scheduleId = 20L, time = LocalTime.of(12, 0), enabled = true),
            ),
        )

        assertTrue(isOccurrenceCompatibleWithPlan(multiSlot, occurrence("2026-09-07T00:00:00Z")))
        assertFalse(
            isOccurrenceCompatibleWithPlan(
                multiSlot,
                occurrence("2026-09-07T00:00:00Z").copy(doseTimeId = 31L),
            ),
        )
    }

    @Test
    fun occurrenceFromAnotherMedicationIsStale() {
        assertFalse(
            isOccurrenceCompatibleWithPlan(
                plan,
                occurrence("2026-09-07T00:00:00Z").copy(medicationId = 99L),
            ),
        )
    }

    private fun occurrence(instant: String) = DoseOccurrence(
        medicationId = 10L,
        doseTimeId = 30L,
        scheduledFor = Instant.parse(instant),
        zoneId = zone,
    )
}
