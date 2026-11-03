package com.example.medicationreminder.ui

import com.example.medicationreminder.domain.model.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReminderPresentationTest {
    private val now = Instant.parse("2026-11-03T08:00:00Z")
    private val occurrence = DoseOccurrence(1, 3, now, ZoneId.of("UTC"))
    private val draft = MedicationDraft(id = 1, name = "Test", dosageText = "1 tablet", note = "",
        enabled = true, weekdays = DayOfWeek.entries.toSet(), timeZoneMode = TimeZoneMode.MANUAL,
        manualZoneId = "UTC", times = listOf(LocalTime.of(8, 0)))
    private val plan = MedicationPlan(Medication(1, "Test", "1 tablet", "", null, true, null),
        MedicationSchedule(2, 1, draft.weekdays, draft.timeZoneMode, draft.manualZoneId),
        listOf(DoseTime(3, 2, draft.times.single(), true)))

    @Test fun countdownCountsToTheBoundOccurrenceThenShowsElapsedTime() {
        assertEquals("00:05:00", countdownTime(occurrence, now.minusSeconds(300)))
        assertEquals("00:00:00", countdownTime(occurrence, now))
        assertEquals("00:00:00", countdownTime(occurrence, now.plusNanos(500_000_000)))
        assertEquals("25:01:01", countdownTime(occurrence, now.plusSeconds(25 * 3600 + 61)))
    }

    @Test fun metadataAndDoseEditsKeepTheReminderWhilePlanEditsClearIt() {
        assertFalse(reminderPlanChanged(plan, draft.copy(name = "New", dosageText = "new dose",
            doseAmount = "0.5", doseUnit = "tablet", note = "new", alias = "Alias")))
        assertTrue(reminderPlanChanged(plan, draft.copy(enabled = false)))
        assertTrue(reminderPlanChanged(plan, draft.copy(times = listOf(LocalTime.NOON))))
        assertTrue(reminderPlanChanged(plan, draft.copy(weekdays = setOf(DayOfWeek.MONDAY))))
        assertTrue(reminderPlanChanged(plan, draft.copy(manualZoneId = "Asia/Tokyo")))
    }

    @Test fun intervalDisplayZoneChangesKeepTheSequenceButAnchorAndPeriodEditsReplaceIt() {
        val intervalPlan = plan.copy(schedule = plan.schedule.copy(scheduleMode = ScheduleMode.INTERVAL,
            intervalMinutes = 720, anchorEpochMillis = now.toEpochMilli()))
        val intervalDraft = draft.copy(scheduleMode = ScheduleMode.INTERVAL, intervalMinutes = 720,
            anchorEpochMillis = now.toEpochMilli(), manualZoneId = "Asia/Tokyo", times = emptyList(), weekdays = emptySet())
        assertFalse(reminderPlanChanged(intervalPlan, intervalDraft))
        assertTrue(reminderPlanChanged(intervalPlan, intervalDraft.copy(intervalMinutes = 600)))
        assertTrue(reminderPlanChanged(intervalPlan, intervalDraft.copy(anchorEpochMillis = now.plusSeconds(60).toEpochMilli())))
    }
}
