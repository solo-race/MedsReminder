package com.example.medicationreminder.reminders

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderRedactionTest {
    @Test
    fun `uses fallback when alias is null`() {
        assertEquals("Medication reminder", lockscreenTitle(null, "Medication reminder"))
    }

    @Test
    fun `uses fallback when alias is empty`() {
        assertEquals("Medication reminder", lockscreenTitle("", "Medication reminder"))
    }

    @Test
    fun `uses fallback when alias is blank`() {
        assertEquals("Medication reminder", lockscreenTitle("   ", "Medication reminder"))
    }

    @Test
    fun `uses alias when it has content`() {
        assertEquals("Evening Pill", lockscreenTitle("Evening Pill", "Medication reminder"))
    }

    @Test
    fun `trims alias before displaying it`() {
        assertEquals("Evening Pill", lockscreenTitle("  Evening Pill  ", "Medication reminder"))
    }
}
