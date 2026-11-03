package com.example.medicationreminder.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseSummaryTest {
    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val utc = ZoneId.of("UTC")
    private fun event(id: Long, amount: String? = "0.5", unit: String? = "tablet",
        status: DoseStatus = DoseStatus.TAKEN, medicationId: Long = 1,
        actionedAt: Instant = now, scheduledFor: Instant = now) = DoseEvent(
        id = id, medicationName = "Same name", dosageText = "Current free text must not supply old quantities",
        scheduledFor = scheduledFor, status = status, actionedAt = actionedAt, medicationId = medicationId,
        doseAmountSnapshot = amount, doseUnitSnapshot = unit,
        dosageTextSnapshot = amount?.let { "Historical dose" })

    @Test fun twoHalfTabletsSkippedDoseAndUnknownTakenProduceTheSpecifiedSummary() {
        val summary = summarizeDoses(listOf(event(1), event(2), event(3, amount = "1", status = DoseStatus.SKIPPED),
            event(4, amount = null, unit = null)), DoseSummaryRange.LAST_7_DAYS, now, utc).single()
        assertEquals(3, summary.takenCount)
        assertEquals(1, summary.skippedCount)
        assertEquals(1, summary.unknownTakenCount)
        val tablets = summary.units.single { it.unit == "tablet" }
        assertEquals("1", tablets.takenAmount!!.stripTrailingZeros().toPlainString())
        assertEquals(2, tablets.takenCount)
        assertEquals(1, tablets.skippedCount)
        assertNull(summary.units.single { it.unit == null }.takenAmount)
    }

    @Test fun decimalAmountsAreExactAndDifferentMedicinesAndUnitsNeverMerge() {
        val summaries = summarizeDoses(listOf(event(1, "0.1"), event(2, "0.2"),
            event(3, "5", "mg"), event(4, "2", "mL"), event(5, "9", medicationId = 2)),
            DoseSummaryRange.LAST_7_DAYS, now, utc)
        assertEquals(2, summaries.size) // Identical names still represent different medicines.
        val first = summaries.single { it.medicationId == 1L }
        assertEquals(setOf("tablet", "mg", "mL"), first.units.map { it.unit }.toSet())
        assertEquals("0.3", first.units.single { it.unit == "tablet" }.takenAmount!!.toPlainString())
        assertEquals("9", summaries.single { it.medicationId == 2L }.units.single().takenAmount!!.toPlainString())
    }

    @Test fun windowsUseConfirmationTimeWithInclusiveStartAndExcludeFutureConfirmations() {
        val cutoff = now.minus(Duration.ofDays(7))
        val summary = summarizeDoses(listOf(
            event(1, actionedAt = cutoff, scheduledFor = now.minus(Duration.ofDays(90))),
            event(2, actionedAt = cutoff.minusMillis(1)),
            event(3, actionedAt = now.plusMillis(1)),
            event(4, status = DoseStatus.SKIPPED)), DoseSummaryRange.LAST_7_DAYS, now, utc).single()
        assertEquals(1, summary.takenCount)
        assertEquals(1, summary.skippedCount)
    }

    @Test fun historyAndThirtyDaySummaryExcludeExpiredRowsEvenBeforeDatabaseCleanupRuns() {
        val cutoff = now.minus(DOSE_HISTORY_RETENTION)
        val events = listOf(event(1, actionedAt = cutoff), event(2, actionedAt = cutoff.minusMillis(1)),
            event(3, actionedAt = now.plusMillis(1)))
        assertEquals(listOf(1L), retainedDoseHistory(events, now).map { it.id })
        assertEquals(1, summarizeDoses(events, DoseSummaryRange.LAST_30_DAYS, now, utc).single().takenCount)
    }

    @Test fun todayFollowsDeviceZoneAndHandlesTheTwentyFiveHourDstDay() {
        val endOfDay = Instant.parse("2026-11-02T04:30:00Z") // Nov 1, 23:30 after New York's fall-back.
        val events = listOf(event(1, actionedAt = Instant.parse("2026-11-01T04:15:00Z")),
            event(2, actionedAt = Instant.parse("2026-11-01T03:59:59Z")))
        assertEquals(1, summarizeDoses(events, DoseSummaryRange.TODAY, endOfDay,
            ZoneId.of("America/New_York")).single().takenCount)
        assertTrue(summarizeDoses(events, DoseSummaryRange.TODAY, endOfDay, utc).isEmpty())
        assertEquals(2, summarizeDoses(events, DoseSummaryRange.LAST_7_DAYS, endOfDay, utc).single().takenCount)
    }

    @Test fun advancingAcrossMidnightRecomputesTodayWithoutChangingTheRollingWindow() {
        val confirmed = Instant.parse("2026-11-03T23:59:00Z")
        val records = listOf(event(1, actionedAt = confirmed))
        assertEquals(1, summarizeDoses(records, DoseSummaryRange.TODAY, confirmed.plusSeconds(30), utc).size)
        assertTrue(summarizeDoses(records, DoseSummaryRange.TODAY, confirmed.plusSeconds(60), utc).isEmpty())
        assertEquals(1, summarizeDoses(records, DoseSummaryRange.LAST_7_DAYS, confirmed.plusSeconds(60), utc).size)
    }

    @Test fun unknownHistoricalDoseIsNeverInferredFromCurrentFreeTextAndEmptyIsNotZeroAmount() {
        assertTrue(summarizeDoses(emptyList(), DoseSummaryRange.LAST_7_DAYS, now, utc).isEmpty())
        val summary = summarizeDoses(listOf(event(1, amount = null, unit = null),
            event(2, amount = null, unit = null, status = DoseStatus.SKIPPED)),
            DoseSummaryRange.LAST_7_DAYS, now, utc).single()
        assertEquals(1, summary.unknownTakenCount)
        assertEquals(1, summary.skippedCount)
        assertNull(summary.units.single().takenAmount)
    }
}
