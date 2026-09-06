package com.example.medicationreminder.domain.decision

import com.example.medicationreminder.data.repository.DoseDecisionRepository
import com.example.medicationreminder.domain.model.DoseDecisionResult
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseDecisionUseCaseTest {
    private val occurrence = DoseOccurrence(
        medicationId = 10,
        doseTimeId = 20,
        scheduledFor = Instant.parse("2026-09-07T00:00:00Z"),
        zoneId = ZoneId.of("Asia/Singapore"),
    )

    @Test
    fun recordedDecisionRunsPersistedDecisionEffects() = runDecisionTest(
        result = DoseDecisionResult.Recorded(DoseStatus.TAKEN),
        expectedPersistedEffects = 1,
        expectedStaleEffects = 0,
    )

    @Test
    fun alreadyDecidedRunsSameCleanupAndSchedulingEffects() = runDecisionTest(
        result = DoseDecisionResult.AlreadyDecided(DoseStatus.SKIPPED),
        expectedPersistedEffects = 1,
        expectedStaleEffects = 0,
    )

    @Test
    fun staleOccurrenceOnlyRunsStaleCleanupEffects() = runDecisionTest(
        result = DoseDecisionResult.StaleOccurrence,
        expectedPersistedEffects = 0,
        expectedStaleEffects = 1,
    )

    private fun runDecisionTest(
        result: DoseDecisionResult,
        expectedPersistedEffects: Int,
        expectedStaleEffects: Int,
    ) = kotlinx.coroutines.runBlocking {
        val repository = FakeDoseDecisionRepository(result)
        val effects = FakeDoseDecisionEffects()
        val useCase = DoseDecisionUseCase(repository, effects)

        val actual = useCase(occurrence, DoseStatus.TAKEN)

        assertEquals(result, actual)
        assertEquals(listOf(occurrence to DoseStatus.TAKEN), repository.calls)
        assertEquals(expectedPersistedEffects, effects.persistedOccurrences.size)
        assertEquals(expectedStaleEffects, effects.staleOccurrences.size)
        if (expectedPersistedEffects == 1) assertTrue(effects.persistedOccurrences.single() == occurrence)
        if (expectedStaleEffects == 1) assertTrue(effects.staleOccurrences.single() == occurrence)
    }

    private class FakeDoseDecisionRepository(
        private val result: DoseDecisionResult,
    ) : DoseDecisionRepository {
        val calls = mutableListOf<Pair<DoseOccurrence, DoseStatus>>()

        override suspend fun decideDose(occurrence: DoseOccurrence, status: DoseStatus): DoseDecisionResult {
            calls += occurrence to status
            return result
        }
    }

    private class FakeDoseDecisionEffects : DoseDecisionEffects {
        val persistedOccurrences = mutableListOf<DoseOccurrence>()
        val staleOccurrences = mutableListOf<DoseOccurrence>()

        override suspend fun onPersistedDecision(occurrence: DoseOccurrence) {
            persistedOccurrences += occurrence
        }

        override suspend fun onStaleOccurrence(occurrence: DoseOccurrence) {
            staleOccurrences += occurrence
        }
    }
}
