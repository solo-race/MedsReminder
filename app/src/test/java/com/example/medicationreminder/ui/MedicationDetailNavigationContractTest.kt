package com.example.medicationreminder.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationDetailNavigationContractTest {
    private val appSource: String by lazy {
        Files.readString(Path.of("src/main/java/com/example/medicationreminder/ui/MedicationApp.kt"))
            .replace("\r\n", "\n")
    }
    private val detailSource: String by lazy {
        Files.readString(Path.of("src/main/java/com/example/medicationreminder/ui/MedicationDetailScreen.kt"))
            .replace("\r\n", "\n")
    }

    @Test
    fun homeCardRoutesToDetailRatherThanEditor() {
        assertTrue(appSource.contains("onOpenDetail = { medicationId ->"))
        assertTrue(appSource.contains("navController.navigate(Routes.detail(medicationId))"))
        assertTrue(appSource.contains("MedicationCard(plan, now, onOpenDetail = onOpenDetail)"))
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
