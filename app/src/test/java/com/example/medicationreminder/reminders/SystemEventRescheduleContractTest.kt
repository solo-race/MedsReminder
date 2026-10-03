package com.example.medicationreminder.reminders

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemEventRescheduleContractTest {
    @Test
    fun recoveryAndTimeChangeActionsAllRequireDecisionAwareReschedule() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        ).forEach { action ->
            assertTrue("Expected reschedule for $action", systemEventRequiresReschedule(action))
        }
    }

    @Test
    fun unrelatedOrMissingActionDoesNotTriggerScheduleRebuild() {
        assertFalse(systemEventRequiresReschedule(Intent.ACTION_SCREEN_ON))
        assertFalse(systemEventRequiresReschedule(null))
    }
}
