package com.example.medicationreminder.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationDetailNavigationContractTest {
    private val appSource: String by lazy {
        Files.readString(Path.of("src/main/java/com/example/medicationreminder/ui/MedicationApp.kt"))
    }
    private val detailSource: String by lazy {
        Files.readString(Path.of("src/main/java/com/example/medicationreminder/ui/MedicationDetailScreen.kt"))
    }

    @Test
    fun homeCardRoutesToDetailRatherThanEditor() {
        assertTrue(appSource.contains("onOpenDetail = { medicationId ->"))
        assertTrue(appSource.contains("navController.navigate(Routes.detail(medicationId))"))
        assertTrue(appSource.contains("MedicationCard(plan, now, onOpenDetail = onOpenDetail)"))
    }

    @Test
    fun notificationTargetRoutesToDetailWithExplicitOccurrencePreserved() {
        assertTrue(appSource.contains("detailOccurrence = notificationOccurrence"))
        assertTrue(appSource.contains("navController.navigate(Routes.detail(notificationMedicationId))"))
        assertTrue(appSource.contains("explicitOccurrence = detailOccurrence?.takeIf { it.medicationId == id }"))
    }

    @Test
    fun detailEditActionRoutesToExistingEditor() {
        assertTrue(appSource.contains("onEdit = { navController.navigate(Routes.edit(it)) }"))
        assertTrue(appSource.contains("composable(\n            route = Routes.EDIT,"))
        assertTrue(appSource.contains("EditMedicationScreen("))
    }

    @Test
    fun detailTakenAndSkippedUseSharedViewModelDecisionPath() {
        assertTrue(detailSource.contains("viewModel.decideDose(occurrence, DoseStatus.TAKEN"))
        assertTrue(detailSource.contains("viewModel.decideDose(occurrence, DoseStatus.SKIPPED"))
    }
}
