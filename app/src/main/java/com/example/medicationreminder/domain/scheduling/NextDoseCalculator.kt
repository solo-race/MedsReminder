package com.example.medicationreminder.domain.scheduling

import com.example.medicationreminder.domain.model.ScheduledDose
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

    fun nextOccurrence(dose: ScheduledDose, now: Instant = Instant.now()): Instant? =
        nextOccurrence(dose.time, dose.weekdays, dose.zoneId, now)
}

