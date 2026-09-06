package com.example.medicationreminder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.medicationreminder.domain.model.DoseOccurrence

/**
 * Activity-to-Compose entry contract for a notification-originated dose occurrence.
 *
 * Phase 2 deliberately keeps the existing editor navigation intact. The explicit
 * occurrence is retained in Compose state here so the Detail route introduced in
 * a later phase can consume the exact reminder identity instead of deriving one
 * from the current clock.
 */
internal val LocalNotificationDoseOccurrence = staticCompositionLocalOf<DoseOccurrence?> { null }

@Composable
fun MedicationAppEntry(
    viewModel: MedicationViewModel,
    notificationOccurrence: DoseOccurrence?,
    notificationMedicationId: Long,
) {
    CompositionLocalProvider(LocalNotificationDoseOccurrence provides notificationOccurrence) {
        MedicationApp(
            viewModel = viewModel,
            notificationMedicationId = notificationOccurrence?.medicationId ?: notificationMedicationId,
        )
    }
}
