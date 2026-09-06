package com.example.medicationreminder.domain.decision

import com.example.medicationreminder.data.repository.DoseDecisionRepository
import com.example.medicationreminder.domain.model.DoseDecisionResult
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus

internal interface DoseDecisionEffects {
    suspend fun onPersistedDecision(occurrence: DoseOccurrence)
    suspend fun onStaleOccurrence(occurrence: DoseOccurrence)
}

class DoseDecisionUseCase internal constructor(
    private val repository: DoseDecisionRepository,
    private val effects: DoseDecisionEffects,
) {
    suspend operator fun invoke(occurrence: DoseOccurrence, status: DoseStatus): DoseDecisionResult {
        val result = repository.decideDose(occurrence, status)
        when (result) {
            is DoseDecisionResult.Recorded,
            is DoseDecisionResult.AlreadyDecided -> effects.onPersistedDecision(occurrence)
            DoseDecisionResult.StaleOccurrence -> effects.onStaleOccurrence(occurrence)
        }
        return result
    }
}
