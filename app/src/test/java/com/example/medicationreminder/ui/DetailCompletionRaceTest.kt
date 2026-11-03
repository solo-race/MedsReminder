package com.example.medicationreminder.ui

import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.Medication
import com.example.medicationreminder.domain.model.MedicationPlan
import com.example.medicationreminder.domain.model.MedicationSchedule
import com.example.medicationreminder.domain.model.TimeZoneMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DetailCompletionRaceTest {
    private fun occurrence(hour: Int) = DoseOccurrence(1, 3,
        Instant.parse("2026-11-03T${hour.toString().padStart(2, '0')}:00:00Z"), ZoneId.of("UTC"))
    private val morning = occurrence(8)
    private val noon = occurrence(12)
    private val evening = occurrence(20)
    private val plan = MedicationPlan(Medication(1, "Test", "1 tablet", "", null, true, null),
        MedicationSchedule(2, 1, DayOfWeek.entries.toSet(), TimeZoneMode.MANUAL, "UTC"), emptyList())

    @Test fun oldDecisionCannotClearANewerQueuedOrOpenedNotification() = runBlocking {
        for (opened in listOf(false, true)) {
            val bound = MutableStateFlow<DoseOccurrence?>(morning)
            val queued = MutableStateFlow<ReminderEntry?>(null)
            val originalKey = detailDoseStateKey(plan, morning)
            val completed = CompletableDeferred<Unit>()
            var advances = 0
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                completed.await()
                // Composition may still expose the 08:00 key; live VM state must guard this gap.
                if (detailCompletionIsCurrent(originalKey, morning, originalKey, morning) &&
                    reminderContextMatches(morning, bound.value, queued.value)) {
                    if (clearReminderBindingIfCurrent(morning, bound, queued)) advances++
                }
            }
            queued.value = ReminderEntry(evening.medicationId, evening)
            if (opened) { bound.value = evening; queued.value = null }
            completed.complete(Unit)
            job.join()
            assertEquals(0, advances)
            assertFalse(clearReminderBindingIfCurrent(morning, bound, queued))
            assertEquals(if (opened) evening else morning, bound.value)
            assertEquals(if (opened) null else evening, queued.value?.occurrence)
        }
    }

    @Test fun delayedNextLoadCannotReplaceAnOpenedEveningOccurrenceWithNoon() = runBlocking {
        val requestedKey = detailDoseStateKey(plan, null)
        var currentKey = requestedKey
        var visible: DoseOccurrence? = morning
        val bound = MutableStateFlow<DoseOccurrence?>(null)
        val queued = MutableStateFlow<ReminderEntry?>(null)
        val loaded = CompletableDeferred<DoseOccurrence?>()
        var applications = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            loadNextOccurrenceIfCurrent({ loaded.await() }, isCurrent = {
                detailCompletionIsCurrent(requestedKey, morning, currentKey, visible) &&
                    reminderContextMatches(null, bound.value, queued.value)
            }) { visible = it; applications++ }
        }
        bound.value = evening
        currentKey = detailDoseStateKey(plan, evening)
        visible = evening
        loaded.complete(noon)
        job.join()
        assertEquals(evening, visible)
        assertEquals(evening, bound.value)
        assertEquals(0, applications)
    }

    @Test fun queuedIntentBlocksNextLoadBeforeCompositionAndBindingCatchUp() = runBlocking {
        val key = detailDoseStateKey(plan, null)
        var visible: DoseOccurrence? = morning
        val bound = MutableStateFlow<DoseOccurrence?>(null)
        val queued = MutableStateFlow<ReminderEntry?>(null)
        val loaded = CompletableDeferred<DoseOccurrence?>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            loadNextOccurrenceIfCurrent({ loaded.await() }, isCurrent = {
                detailCompletionIsCurrent(key, morning, key, visible) &&
                    reminderContextMatches(null, bound.value, queued.value)
            }) { visible = it }
        }
        queued.value = ReminderEntry(evening.medicationId, evening)
        loaded.complete(noon)
        job.join()
        assertEquals(morning, visible)
        assertEquals(evening, queued.value?.occurrence)
    }

    @Test fun unchangedInputStillAcceptsItsNextOccurrenceButRejectsAnOlderResultAfterward() = runBlocking {
        val key = detailDoseStateKey(plan, null)
        var visible: DoseOccurrence? = morning
        loadNextOccurrenceIfCurrent({ noon }, isCurrent = {
            detailCompletionIsCurrent(key, morning, key, visible)
        }) { visible = it }
        assertEquals(noon, visible)
        assertFalse(detailCompletionIsCurrent(key, morning, key, visible))
    }
}
