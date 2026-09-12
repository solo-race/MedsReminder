package com.example.medicationreminder.reminders

import com.example.medicationreminder.domain.model.ScheduledDose
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MissedReminderReposterTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    // 2026-08-25 12:00 local time; the date is a Tuesday.
    private val now = Instant.parse("2026-08-25T04:00:00Z")

    private fun dose(
        id: Long,
        time: LocalTime,
        weekdays: Set<DayOfWeek> = setOf(now.atZone(zone).dayOfWeek),
    ) = ScheduledDose(
        medicationId = id,
        medicationName = "Med$id",
        dosageText = "1 tablet",
        scheduleId = id,
        doseTimeId = id,
        time = time,
        weekdays = weekdays,
        zoneId = zone,
        medicationAlias = null,
    )

    @Test
    fun `plans overdue undecided dose with today occurrence`() = runBlocking {
        val missed = MissedReminderReposter.plan(listOf(dose(1, LocalTime.of(8, 0))), now) { _, _ -> false }
        assertEquals(listOf(1L), missed.map { it.dose.doseTimeId })
        assertEquals(
            now.atZone(zone).toLocalDate().atTime(8, 0).atZone(zone).toInstant(),
            missed.single().scheduledFor,
        )
    }

    @Test
    fun `skips dose that already has a decision on its local day`() = runBlocking {
        val missed = MissedReminderReposter.plan(listOf(dose(1, LocalTime.of(8, 0))), now) { _, _ -> true }
        assertEquals(0, missed.size)
    }

    @Test
    fun `ignores dose time still in the future today`() = runBlocking {
        val missed = MissedReminderReposter.plan(listOf(dose(1, LocalTime.of(20, 0))), now) { _, _ -> false }
        assertEquals(0, missed.size)
    }

    @Test
    fun `ignores dose not scheduled on today weekday`() = runBlocking {
        val otherDay = setOf(now.atZone(zone).dayOfWeek.plus(1))
        val missed = MissedReminderReposter.plan(listOf(dose(1, LocalTime.of(8, 0), otherDay)), now) { _, _ -> false }
        assertEquals(0, missed.size)
    }

    @Test
    fun `keeps only undecided doses among mixed input`() = runBlocking {
        val doses = listOf(dose(1, LocalTime.of(8, 0)), dose(2, LocalTime.of(9, 30)))
        val missed = MissedReminderReposter.plan(doses, now) { dose, _ -> dose.doseTimeId == 1L }
        assertEquals(listOf(2L), missed.map { it.dose.doseTimeId })
        assertEquals(
            now.atZone(zone).toLocalDate().atTime(9, 30).atZone(zone).toInstant(),
            missed.single().scheduledFor,
        )
    }
}
