package com.example.medicationreminder.data.repository

import com.example.medicationreminder.data.local.DoseTimeEntity
import com.example.medicationreminder.data.local.MedicationEntity
import com.example.medicationreminder.data.local.MedicationScheduleEntity
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.TimeZoneMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseOccurrenceActionValidationTest {
    private val medication = MedicationEntity(
        id = 10,
        name = "Medication",
        dosageText = "1 tablet",
        note = "",
        photoPath = null,
        enabled = true,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
    private val schedule = MedicationScheduleEntity(
        id = 20,
        medicationId = medication.id,
        weekdaysMask = 1 shl 0,
        timeZoneMode = TimeZoneMode.MANUAL,
        manualZoneId = "UTC",
    )
    private val doseTime = DoseTimeEntity(
        id = 30,
        scheduleId = schedule.id,
        minuteOfDay = 8 * 60,
        enabled = true,
    )
    private val occurrence = DoseOccurrence(
        medicationId = medication.id,
        doseTimeId = doseTime.id,
        scheduledFor = Instant.parse("2026-09-07T08:00:00Z"),
        zoneId = ZoneId.of("UTC"),
    )

    @Test
    fun currentEnabledOccurrenceIsActionable() {
        assertTrue(isOccurrenceActionable(medication, schedule, doseTime, occurrence))
    }

    @Test
    fun deletedOrDisabledMedicationFailsClosed() {
        assertFalse(isOccurrenceActionable(null, schedule, doseTime, occurrence))
        assertFalse(isOccurrenceActionable(medication.copy(enabled = false), schedule, doseTime, occurrence))
    }

    @Test
    fun deletedDisabledOrForeignSlotFailsClosed() {
        assertFalse(isOccurrenceActionable(medication, schedule, null, occurrence))
        assertFalse(isOccurrenceActionable(medication, schedule, doseTime.copy(enabled = false), occurrence))
        assertFalse(isOccurrenceActionable(medication, schedule, doseTime.copy(scheduleId = 999), occurrence))
    }

    @Test
    fun scheduleZoneWeekdayOrTimeEditMakesOldOccurrenceStale() {
        assertFalse(
            isOccurrenceActionable(
                medication,
                schedule.copy(manualZoneId = "Asia/Singapore"),
                doseTime,
                occurrence,
            ),
        )
        assertFalse(
            isOccurrenceActionable(
                medication,
                schedule.copy(weekdaysMask = 1 shl 1),
                doseTime,
                occurrence,
            ),
        )
        assertFalse(
            isOccurrenceActionable(
                medication,
                schedule,
                doseTime.copy(minuteOfDay = 9 * 60),
                occurrence,
            ),
        )
    }

    @Test
    fun multipleSlotsCannotCrossTarget() {
        val otherSlot = doseTime.copy(id = 31, minuteOfDay = 9 * 60)
        assertFalse(isOccurrenceActionable(medication, schedule, otherSlot, occurrence))
    }

    @Test
    fun dstGapOccurrenceUsesSameWallClockResolutionAsScheduler() {
        val zone = ZoneId.of("Europe/Berlin")
        val date = LocalDate.of(2026, 3, 29)
        val scheduledFor = date.atTime(LocalTime.of(2, 30)).atZone(zone).toInstant()
        val sundaySchedule = schedule.copy(
            weekdaysMask = 1 shl 6,
            manualZoneId = zone.id,
        )
        val gapDoseTime = doseTime.copy(minuteOfDay = 2 * 60 + 30)
        val gapOccurrence = occurrence.copy(
            scheduledFor = scheduledFor,
            zoneId = zone,
        )

        assertTrue(isOccurrenceActionable(medication, sundaySchedule, gapDoseTime, gapOccurrence))
    }
}
