package com.example.medicationreminder.ui

import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DoseConfirmationStateTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val scheduledFor = Instant.parse("2026-11-13T08:00:00Z")
    private val occurrence = DoseOccurrence(7, 11, scheduledFor, zone)

    @Test
    fun phaseIsEarlyUntilTheScheduledInstant() {
        assertEquals(DoseConfirmationPhase.EARLY,
            doseConfirmationPhase(occurrence, scheduledFor.minusMillis(1)))
        assertEquals(DoseConfirmationPhase.DUE,
            doseConfirmationPhase(occurrence, scheduledFor))
        assertEquals(DoseConfirmationPhase.DUE,
            doseConfirmationPhase(occurrence, scheduledFor.plusMillis(1)))
    }

    @Test
    fun loadingStaleUnavailableAndDecidedStayDistinct() {
        val now = scheduledFor.minusSeconds(60)

        assertEquals(DetailDosePresentation.Loading, detailDosePresentation(null, now))
        assertEquals(DetailDosePresentation.Stale,
            detailDosePresentation(DetailDoseState(occurrence = occurrence, stale = true), now))
        assertEquals(DetailDosePresentation.Unavailable,
            detailDosePresentation(DetailDoseState(), now))
        assertEquals(DetailDosePresentation.Decided(DoseStatus.TAKEN),
            detailDosePresentation(
                DetailDoseState(occurrence = occurrence, decided = true, decidedStatus = DoseStatus.TAKEN), now))
    }

    @Test
    fun actionablePresentationCarriesTheConfirmationPhase() {
        val state = DetailDoseState(occurrence = occurrence)

        assertEquals(DetailDosePresentation.Actionable(occurrence, DoseConfirmationPhase.EARLY),
            detailDosePresentation(state, scheduledFor.minusSeconds(1)))
        assertEquals(DetailDosePresentation.Actionable(occurrence, DoseConfirmationPhase.DUE),
            detailDosePresentation(state, scheduledFor))
    }

    @Test
    fun explicitReminderAlwaysKeepsTheReminderHeading() {
        assertEquals(DetailDoseHeading.REMINDER,
            detailDoseHeading(occurrence, occurrence, scheduledFor.plusSeconds(600)))
        assertEquals(DetailDoseHeading.REMINDER,
            detailDoseHeading(occurrence, null, scheduledFor.minusSeconds(600)))
    }

    @Test
    fun pastOccurrenceStaysUnconfirmedEvenWhenDecidedOrStale() {
        assertEquals(DetailDoseHeading.UNCONFIRMED,
            detailDoseHeading(null, occurrence, scheduledFor))
        assertEquals(DetailDoseHeading.UNCONFIRMED,
            detailDoseHeading(null, occurrence, scheduledFor.plusSeconds(3600)))
        assertEquals(DetailDoseHeading.NEXT,
            detailDoseHeading(null, occurrence, scheduledFor.minusMillis(1)))
        assertEquals(DetailDoseHeading.NEXT, detailDoseHeading(null, null, scheduledFor))
    }
}
