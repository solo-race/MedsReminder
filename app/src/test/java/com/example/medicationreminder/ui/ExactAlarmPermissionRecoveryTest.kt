package com.example.medicationreminder.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmPermissionRecoveryTest {
    @Test
    fun appResumeOnlyReschedulesOnFalseToTrueTransition() {
        assertTrue(exactAlarmPermissionRestored(wasAllowed = false, isAllowed = true))
        assertFalse(exactAlarmPermissionRestored(wasAllowed = false, isAllowed = false))
        assertFalse(exactAlarmPermissionRestored(wasAllowed = true, isAllowed = true))
        assertFalse(exactAlarmPermissionRestored(wasAllowed = true, isAllowed = false))
    }
}
