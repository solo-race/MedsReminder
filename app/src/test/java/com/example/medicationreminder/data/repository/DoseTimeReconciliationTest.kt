package com.example.medicationreminder.data.repository

import com.example.medicationreminder.data.local.DoseTimeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseTimeReconciliationTest {
    @Test
    fun metadataOnlyEditPreservesAllIds() {
        val existing = listOf(
            dose(id = 11, minute = 480),
            dose(id = 12, minute = 780),
        )

        val result = reconcileDoseTimes(
            scheduleId = 7,
            existing = existing,
            desiredMinutes = listOf(480, 780),
        )

        assertTrue(result.toInsert.isEmpty())
        assertTrue(result.toUpdate.isEmpty())
        assertTrue(result.idsToDelete.isEmpty())
    }

    @Test
    fun reorderedInputPreservesIds() {
        val existing = listOf(
            dose(id = 21, minute = 480),
            dose(id = 22, minute = 780),
        )

        val result = reconcileDoseTimes(
            scheduleId = 7,
            existing = existing,
            desiredMinutes = listOf(780, 480),
        )

        assertTrue(result.toInsert.isEmpty())
        assertTrue(result.toUpdate.isEmpty())
        assertTrue(result.idsToDelete.isEmpty())
    }

    @Test
    fun addingOneTimeKeepsExistingAndInsertsExactlyOne() {
        val existing = listOf(
            dose(id = 31, minute = 480),
            dose(id = 32, minute = 780),
        )

        val result = reconcileDoseTimes(
            scheduleId = 7,
            existing = existing,
            desiredMinutes = listOf(480, 720, 780),
        )

        assertEquals(
            listOf(DoseTimeEntity(scheduleId = 7, minuteOfDay = 720, enabled = true)),
            result.toInsert,
        )
        assertTrue(result.toUpdate.isEmpty())
        assertTrue(result.idsToDelete.isEmpty())
    }

    @Test
    fun removingOneTimeDeletesOnlyItsId() {
        val existing = listOf(
            dose(id = 41, minute = 480),
            dose(id = 42, minute = 780),
        )

        val result = reconcileDoseTimes(
            scheduleId = 7,
            existing = existing,
            desiredMinutes = listOf(480),
        )

        assertTrue(result.toInsert.isEmpty())
        assertTrue(result.toUpdate.isEmpty())
        assertEquals(listOf(42L), result.idsToDelete)
    }

    @Test
    fun duplicateDesiredTimesCollapseToOneSlot() {
        val result = reconcileDoseTimes(
            scheduleId = 7,
            existing = emptyList(),
            desiredMinutes = listOf(480, 480, 480),
        )

        assertEquals(
            listOf(DoseTimeEntity(scheduleId = 7, minuteOfDay = 480, enabled = true)),
            result.toInsert,
        )
        assertTrue(result.toUpdate.isEmpty())
        assertTrue(result.idsToDelete.isEmpty())
    }

    @Test
    fun duplicateExistingRowsKeepOneStableIdAndDeleteTheRest() {
        val existing = listOf(
            dose(id = 52, minute = 480),
            dose(id = 51, minute = 480),
        )

        val result = reconcileDoseTimes(
            scheduleId = 7,
            existing = existing,
            desiredMinutes = listOf(480),
        )

        assertTrue(result.toInsert.isEmpty())
        assertTrue(result.toUpdate.isEmpty())
        assertEquals(listOf(52L), result.idsToDelete)
    }

    @Test
    fun matchingDisabledSlotRetainsIdAndIsReenabled() {
        val existing = listOf(dose(id = 61, minute = 480, enabled = false))

        val result = reconcileDoseTimes(
            scheduleId = 7,
            existing = existing,
            desiredMinutes = listOf(480),
        )

        assertTrue(result.toInsert.isEmpty())
        assertEquals(listOf(dose(id = 61, minute = 480, enabled = true)), result.toUpdate)
        assertTrue(result.idsToDelete.isEmpty())
    }

    private fun dose(id: Long, minute: Int, enabled: Boolean = true) = DoseTimeEntity(
        id = id,
        scheduleId = 7,
        minuteOfDay = minute,
        enabled = enabled,
    )
}
