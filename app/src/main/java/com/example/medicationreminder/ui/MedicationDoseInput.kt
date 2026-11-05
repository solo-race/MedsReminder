package com.example.medicationreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.medicationreminder.R

internal data class EditorDose(val dosageText: String, val amount: String?, val unit: String?)

/** Existing text-only doses remain unknown until the user explicitly supplies amount and unit. */
internal fun resolveEditorDose(
    amountInput: String,
    unitInput: String,
    displayUnit: String,
    legacyDosageText: String? = null,
): EditorDose? {
    val amount = amountInput.trim()
    val unit = unitInput.trim()
    if (amount.isEmpty() && unit.isEmpty()) {
        return legacyDosageText?.let { EditorDose(it, null, null) }
    }
    val decimal = amount.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
    if (unit.isEmpty()) return null
    val normalizedAmount = decimal.stripTrailingZeros().toPlainString()
    return EditorDose("$normalizedAmount ${displayUnit.trim()}", normalizedAmount, unit)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MedicationDoseInput(
    amount: String,
    unit: String,
    resolvedDose: EditorDose?,
    hasLegacyDose: Boolean,
    onAmountChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
) {
    val units = listOf(
        "tablet" to stringResource(R.string.unit_tablet),
        "capsule" to stringResource(R.string.unit_capsule),
        "mg" to stringResource(R.string.unit_mg),
        "mL" to stringResource(R.string.unit_ml),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.amount_dosage), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.structured_dose_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        OutlinedTextField(
            value = units.firstOrNull { it.first == unit }?.second ?: unit,
            onValueChange = { value -> onUnitChange(units.firstOrNull { it.second == value }?.first ?: value) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.structured_dose_unit)) },
            singleLine = true,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            units.forEach { (value, label) ->
                FilterChip(selected = unit == value, onClick = { onUnitChange(value) }, label = { Text(label) })
            }
        }
        resolvedDose?.let { Text(stringResource(R.string.dose_display_preview, it.dosageText)) }
        Text(
            stringResource(if (hasLegacyDose && amount.isBlank() && unit.isBlank()) {
                R.string.dose_legacy_hint
            } else {
                R.string.dose_input_hint
            }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
