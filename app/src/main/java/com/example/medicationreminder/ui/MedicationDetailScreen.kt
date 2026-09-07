@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.medicationreminder.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.medicationreminder.R
import com.example.medicationreminder.domain.model.DoseDecisionResult
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.MedicationPlan
import com.example.medicationreminder.domain.model.TimeZoneMode
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal data class DetailDoseState(
    val occurrence: DoseOccurrence? = null,
    val stale: Boolean = false,
    val decided: Boolean = false,
    val decidedStatus: DoseStatus? = null,
)

internal data class DetailDoseStateKey(
    val plan: MedicationPlan?,
    val explicitOccurrence: DoseOccurrence?,
)

internal fun detailDoseStateKey(
    plan: MedicationPlan?,
    explicitOccurrence: DoseOccurrence?,
): DetailDoseStateKey = DetailDoseStateKey(plan, explicitOccurrence)

internal fun preferredDetailOccurrence(
    explicitOccurrence: DoseOccurrence?,
    upcomingOccurrence: DoseOccurrence?,
): DoseOccurrence? = explicitOccurrence ?: upcomingOccurrence

internal fun isOccurrenceCompatibleWithPlan(plan: MedicationPlan, occurrence: DoseOccurrence): Boolean {
    if (!plan.medication.enabled || plan.medication.id != occurrence.medicationId) return false
    val zoneId = plan.schedule.zoneIdForDetail()
    if (zoneId != occurrence.zoneId) return false
    val doseTime = plan.times.firstOrNull { it.id == occurrence.doseTimeId && it.enabled } ?: return false
    val local = occurrence.scheduledFor.atZone(zoneId)
    if (local.dayOfWeek !in plan.schedule.weekdays) return false
    return local.toLocalTime() == doseTime.time
}

@Composable
internal fun MedicationDetailScreen(
    plan: MedicationPlan?,
    explicitOccurrence: DoseOccurrence?,
    viewModel: MedicationViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val now = remember { Instant.now() }
    val initialState by produceState<DetailDoseState?>(initialValue = null, plan, explicitOccurrence) {
        value = when {
            plan == null -> DetailDoseState(stale = true)
            explicitOccurrence != null -> {
                if (!isOccurrenceCompatibleWithPlan(plan, explicitOccurrence)) {
                    DetailDoseState(occurrence = explicitOccurrence, stale = true)
                } else {
                    DetailDoseState(
                        occurrence = explicitOccurrence,
                        decided = viewModel.isDoseDecided(explicitOccurrence),
                    )
                }
            }
            else -> DetailDoseState(
                occurrence = viewModel.nextActionableOccurrence(plan.medication.id, now),
            )
        }
    }
    val stateKey = detailDoseStateKey(plan, explicitOccurrence)
    var state by remember(stateKey) { mutableStateOf<DetailDoseState?>(null) }
    LaunchedEffect(initialState) {
        if (initialState != null) state = initialState
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.medication_detail_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                actions = {
                    if (plan != null) {
                        TextButton(onClick = { onEdit(plan.medication.id) }) {
                            Text(stringResource(R.string.edit_medication_action))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (plan == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.medication_no_longer_available), style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        val doseState = state ?: initialState
        val actionable = doseState?.occurrence != null && doseState.stale.not() && doseState.decided.not()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    plan.medication.photoPath?.let {
                        DetailMedicationImage(Uri.fromFile(File(it)), Modifier.size(72.dp))
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(plan.medication.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        plan.medication.alias?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(plan.medication.dosageText, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (plan.medication.note.isNotBlank()) {
                item {
                    Text(stringResource(R.string.detail_note_label), fontWeight = FontWeight.Medium)
                    Text(plan.medication.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Text(stringResource(R.string.detail_schedule_label), fontWeight = FontWeight.Medium)
                Text(
                    plan.schedule.weekdays.sortedBy { it.value }
                        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    plan.times.filter { it.enabled }.sortedBy { it.time }
                        .joinToString(", ") { it.time.format(detailTimeFormatter(locale)) },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when (plan.schedule.timeZoneMode) {
                        TimeZoneMode.DEVICE -> stringResource(R.string.detail_zone_device)
                        TimeZoneMode.MANUAL -> stringResource(
                            R.string.detail_zone_manual,
                            plan.schedule.manualZoneId.orEmpty(),
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(stringResource(R.string.detail_dose_label), fontWeight = FontWeight.Medium)
                val occurrence = doseState?.occurrence
                Text(
                    when {
                        doseState == null -> stringResource(R.string.detail_loading_dose)
                        doseState.stale -> stringResource(R.string.detail_stale_occurrence)
                        occurrence == null -> stringResource(R.string.detail_no_actionable_dose)
                        else -> stringResource(
                            if (explicitOccurrence != null) R.string.detail_reminder_occurrence else R.string.detail_next_occurrence,
                            detailInstantFormatter(locale).format(occurrence.scheduledFor.atZone(occurrence.zoneId)),
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (doseState?.decided == true) {
                    Spacer(Modifier.height(8.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                when (doseState.decidedStatus) {
                                    DoseStatus.TAKEN -> stringResource(R.string.detail_taken_state)
                                    DoseStatus.SKIPPED -> stringResource(R.string.detail_skipped_state)
                                    null -> stringResource(R.string.detail_already_decided)
                                },
                            )
                        },
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        enabled = actionable,
                        onClick = {
                            val currentState = doseState ?: return@OutlinedButton
                            val occurrence = currentState.occurrence ?: return@OutlinedButton
                            viewModel.decideDose(occurrence, DoseStatus.TAKEN) { result ->
                                state = when (result) {
                                    is DoseDecisionResult.Recorded -> currentState.copy(decided = true, decidedStatus = result.status)
                                    is DoseDecisionResult.AlreadyDecided -> currentState.copy(decided = true, decidedStatus = result.status)
                                    DoseDecisionResult.StaleOccurrence -> currentState.copy(stale = true)
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.status_taken)) }
                    OutlinedButton(
                        enabled = actionable,
                        onClick = {
                            val currentState = doseState ?: return@OutlinedButton
                            val occurrence = currentState.occurrence ?: return@OutlinedButton
                            viewModel.decideDose(occurrence, DoseStatus.SKIPPED) { result ->
                                state = when (result) {
                                    is DoseDecisionResult.Recorded -> currentState.copy(decided = true, decidedStatus = result.status)
                                    is DoseDecisionResult.AlreadyDecided -> currentState.copy(decided = true, decidedStatus = result.status)
                                    DoseDecisionResult.StaleOccurrence -> currentState.copy(stale = true)
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.status_skipped)) }
                }
            }
        }
    }
}

@Composable
private fun DetailMedicationImage(uri: Uri, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            android.widget.ImageView(context).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { it.setImageURI(uri) },
        modifier = modifier,
    )
}

private fun com.example.medicationreminder.domain.model.MedicationSchedule.zoneIdForDetail(): ZoneId = when (timeZoneMode) {
    TimeZoneMode.DEVICE -> ZoneId.systemDefault()
    TimeZoneMode.MANUAL -> manualZoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()
}

private fun detailTimeFormatter(locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", locale)
private fun detailInstantFormatter(locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a", locale)
