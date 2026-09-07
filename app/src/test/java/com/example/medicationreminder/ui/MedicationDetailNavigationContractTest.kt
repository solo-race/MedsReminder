package com.example.medicationreminder.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationDetailNavigationContractTest {
    private val source: String by lazy {
        Files.readString(Path.of("src/main/java/com/example/medicationreminder/ui/MedicationApp.kt"))
    }

    @Test
    fun homeCardRoutesToDetailRatherThanEditor() {
        assertTrue(source.contains("onOpenDetail = { medicationId ->"))
        assertTrue(source.contains("navController.navigate(Routes.detail(medicationId))"))
        assertTrue(source.contains("MedicationCard(plan, now, onOpenDetail = onOpenDetail)"))
    }

    @Test
    fun notificationTargetRoutesToDetailWithExplicitOccurrencePreserved() {
        assertTrue(source.contains("detailOccurrence = notificationOccurrence"))
        assertTrue(source.contains("navController.navigate(Routes.detail(notificationMedicationId))"))
        assertTrue(source.contains("explicitOccurrence = detailOccurrence?.takeIf { it.medicationId == id }"))
    }

    @Test
    fun detailEditActionRoutesToExistingEditor() {
        assertTrue(source.contains("onEdit = { navController.navigate(Routes.edit(it)) }"))
        assertTrue(source.contains("composable(\n            route = Routes.EDIT,"))
        assertTrue(source.contains("EditMedicationScreen("))
    }

    @Test
    fun detailTakenAndSkippedUseSharedViewModelDecisionPath() {
        assertTrue(source.contains("viewModel.decideDose(occurrence, DoseStatus.TAKEN"))
        assertTrue(source.contains("viewModel.decideDose(occurrence, DoseStatus.SKIPPED"))
    }
}
