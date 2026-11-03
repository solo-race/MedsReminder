@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.example.medicationreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.medicationreminder.R
import com.example.medicationreminder.domain.model.DoseEvent
import com.example.medicationreminder.domain.model.DoseSummaryRange
import com.example.medicationreminder.domain.model.summarizeDoses
import java.time.Instant

@Composable
internal fun doseUnitLabel(unit: String?): String = when (unit) {
    "tablet" -> stringResource(R.string.unit_tablet)
    "capsule" -> stringResource(R.string.unit_capsule)
    "mg" -> stringResource(R.string.unit_mg)
    "mL" -> stringResource(R.string.unit_ml)
    null -> stringResource(R.string.summary_unit_unknown)
    else -> unit
}

@Composable
internal fun DoseSummaryScreen(history: List<DoseEvent>, onBack: () -> Unit) {
    var range by rememberSaveable { mutableStateOf(DoseSummaryRange.LAST_7_DAYS) }
    val clock by occurrenceClock(periodMillis = 60_000)
    // A new history emission uses the current time immediately, rather than waiting for a timer tick.
    val summaries = remember(history, range, clock) { summarizeDoses(history, range, Instant.now()) }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text(stringResource(R.string.dose_summary_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } })
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DoseSummaryRange.entries.forEach { option ->
                            FilterChip(selected = option == range, onClick = { range = option }, label = {
                                Text(stringResource(when (option) {
                                    DoseSummaryRange.TODAY -> R.string.summary_today
                                    DoseSummaryRange.LAST_7_DAYS -> R.string.summary_last_seven
                                    DoseSummaryRange.LAST_30_DAYS -> R.string.summary_last_thirty
                                }))
                            })
                        }
                    }
                    Text(stringResource(R.string.summary_time_basis), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.history_retention_notice), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.summary_counts,
                        summaries.sumOf { it.takenCount }, summaries.sumOf { it.skippedCount }),
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            if (summaries.isEmpty()) item { Text(stringResource(R.string.summary_empty)) }
            items(summaries, key = { it.medicationId }) { medication ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(medication.medicationName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.summary_counts, medication.takenCount, medication.skippedCount))
                        if (medication.unknownTakenCount > 0) Text(stringResource(R.string.summary_missing_doses,
                            medication.unknownTakenCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        medication.units.forEach { unit ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(doseUnitLabel(unit.unit), fontWeight = FontWeight.Medium)
                                unit.takenAmount?.let { amount ->
                                    Text(stringResource(R.string.summary_measured_amount,
                                        amount.stripTrailingZeros().toPlainString(), doseUnitLabel(unit.unit)),
                                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                                }
                                Text(stringResource(R.string.summary_counts, unit.takenCount, unit.skippedCount),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
