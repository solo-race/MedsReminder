package com.example.medicationreminder.domain.scheduling

import com.example.medicationreminder.domain.model.ScheduledDose
import com.example.medicationreminder.domain.model.ScheduleMode
import java.time.Duration
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Calculates the next valid wall-clock occurrence, including daylight-saving transitions. */
object NextDoseCalculator {
    fun nextOccurrence(
        time: LocalTime,
        weekdays: Set<DayOfWeek>,
        zoneId: ZoneId,
        now: Instant = Instant.now(),
    ): Instant? {
        if (weekdays.isEmpty()) return null
        val today = now.atZone(zoneId).toLocalDate()
        for (offset in 0..7) {
            val date: LocalDate = today.plusDays(offset.toLong())
            if (date.dayOfWeek !in weekdays) continue
            // atZone resolves skipped local times forward and chooses the earlier offset on overlaps.
            val candidate = date.atTime(time).atZone(zoneId).toInstant()
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }
    fun mostRecentOverdueOccurrence(
        time: LocalTime,
        weekdays: Set<DayOfWeek>,
        zoneId: ZoneId,
        now: Instant,
    ): Instant? {
        if (weekdays.isEmpty()) return null
        val today = now.atZone(zoneId).toLocalDate()
        if (today.dayOfWeek !in weekdays) return null
        val candidate = today.atTime(time).atZone(zoneId).toInstant()
        return if (candidate.isAfter(now)) null else candidate
    }

    fun nextOccurrence(dose: ScheduledDose, now: Instant = Instant.now()): Instant? =
        if (dose.scheduleMode == ScheduleMode.INTERVAL) intervalOccurrence(dose, now, next = true)
        else nextOccurrence(dose.time, dose.weekdays, dose.zoneId, now)

    fun mostRecentOverdueOccurrence(dose: ScheduledDose, now: Instant): Instant? =
        if (dose.scheduleMode == ScheduleMode.INTERVAL) intervalOccurrence(dose, now, next = false)
        else mostRecentOverdueOccurrence(dose.time, dose.weekdays, dose.zoneId, now)

    private fun intervalOccurrence(dose: ScheduledDose, now: Instant, next: Boolean): Instant? {
        val anchor = dose.anchorEpochMillis?.let(Instant::ofEpochMilli) ?: return null
        val minutes = dose.intervalMinutes?.takeIf { it in 1..Long.MAX_VALUE / 60 } ?: return null
        if (now.isBefore(anchor)) return if (next) anchor else null
        val elapsed = Duration.between(anchor, now)
        val remainder = elapsed.seconds % (minutes * 60)
        return try {
            val candidate = if (next) now.plusSeconds(minutes * 60 - remainder)
                else now.minusSeconds(remainder)
            candidate.minusNanos(elapsed.nano.toLong()).also { it.toEpochMilli() }
        } catch (_: ArithmeticException) {
            null // No successor representable in persisted epoch milliseconds.
        } catch (_: DateTimeException) {
            null
        }
    }
}

