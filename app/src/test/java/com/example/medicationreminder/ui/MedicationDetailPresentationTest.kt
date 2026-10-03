package com.example.medicationreminder.ui

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class MedicationDetailPresentationTest {
    @Test fun chineseTimesAreUnambiguous24HourValues() {
        assertEquals("08:00", formatDetailTime(LocalTime.of(8, 0), Locale.SIMPLIFIED_CHINESE))
        assertEquals("12:39", formatDetailTime(LocalTime.of(12, 39), Locale.SIMPLIFIED_CHINESE))
        assertEquals("20:00", formatDetailTime(LocalTime.of(20, 0), Locale.SIMPLIFIED_CHINESE))
        assertEquals("00:00", formatDetailTime(LocalTime.MIDNIGHT, Locale.SIMPLIFIED_CHINESE))
    }
    @Test fun englishTimesRetainAmPm() {
        assertEquals("8:00 PM", formatDetailTime(LocalTime.of(20, 0), Locale.US))
    }
    @Test fun everyDayRequiresAllSevenDays() {
        assertTrue(isEveryDay(DayOfWeek.entries.toSet()))
        assertFalse(isEveryDay(setOf(DayOfWeek.MONDAY)))
        assertFalse(isEveryDay(emptySet()))
    }
}
