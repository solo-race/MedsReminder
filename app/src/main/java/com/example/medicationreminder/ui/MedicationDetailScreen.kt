@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.example.medicationreminder.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import java.time.DayOfWeek
import java.time.LocalTime
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DetailSection {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        plan.medication.photoPath?.let {
                            DetailMedicationImage(
                                Uri.fromFile(File(it)),
                                Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)),
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(plan.medication.name, style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold)
                            plan.medication.alias?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.amount_dosage),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // Display exactly what the user entered; never infer a unit or medical dose.
                            Text(plan.medication.dosageText, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            item {
                DetailSection {
                    val occurrence = doseState?.occurrence
                    Text(stringResource(if (explicitOccurrence != null) R.string.detail_reminder_heading
                        else R.string.detail_next_heading), style = MaterialTheme.typography.titleMedium)
                    if (doseState != null && !doseState.stale && occurrence != null) {
                        val local = occurrence.scheduledFor.atZone(occurrence.zoneId)
                        Text(formatDetailTime(local.toLocalTime(), locale),
                            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text(detailDateFormatter(locale).format(local),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(stringResource(when {
                            doseState == null -> R.string.detail_loading_dose
                            doseState.stale -> R.string.detail_stale_occurrence
                            else -> R.string.detail_no_actionable_dose
                        }), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (doseState?.decided == true) {
                        // A readable status, not a fake clickable chip or two almost invisible disabled buttons.
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(when (doseState.decidedStatus) {
                                DoseStatus.TAKEN -> R.string.detail_taken_state
                                DoseStatus.SKIPPED -> R.string.detail_skipped_state
                                null -> R.string.detail_already_decided
                            }), modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    if (actionable) {
                        // Full-width actions also work at large font scales without horizontal clipping.
                        Button(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {
                            val currentState = doseState ?: return@Button
                            val occurrence = currentState.occurrence ?: return@Button
                            viewModel.decideDose(occurrence, DoseStatus.TAKEN) { result ->
                                state = when (result) {
                                    is DoseDecisionResult.Recorded -> currentState.copy(decided = true, decidedStatus = result.status)
                                    is DoseDecisionResult.AlreadyDecided -> currentState.copy(decided = true, decidedStatus = result.status)
                                    DoseDecisionResult.StaleOccurrence -> currentState.copy(stale = true)
                                }
                            }
                        }) { Text(stringResource(R.string.detail_take_action), textAlign = TextAlign.Center) }
                        OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {
                            val currentState = doseState ?: return@OutlinedButton
                            val occurrence = currentState.occurrence ?: return@OutlinedButton
                            viewModel.decideDose(occurrence, DoseStatus.SKIPPED) { result ->
                                state = when (result) {
                                    is DoseDecisionResult.Recorded -> currentState.copy(decided = true, decidedStatus = result.status)
                                    is DoseDecisionResult.AlreadyDecided -> currentState.copy(decided = true, decidedStatus = result.status)
                                    DoseDecisionResult.StaleOccurrence -> currentState.copy(stale = true)
                                }
                            }
                        }) { Text(stringResource(R.string.detail_skip_action), textAlign = TextAlign.Center) }
                    }
                }
            }
            item {
                DetailSection {
                    Text(stringResource(R.string.detail_schedule_label), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.detail_weekdays_label), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when {
                        isEveryDay(plan.schedule.weekdays) -> stringResource(R.string.detail_every_day)
                        plan.schedule.weekdays.isEmpty() -> stringResource(R.string.detail_no_days)
                        else -> plan.schedule.weekdays.sortedBy { it.value }
                            .joinToString(" · ") { it.getDisplayName(TextStyle.SHORT, locale) }
                    }, style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.detail_times_label), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val times = plan.times.filter { it.enabled }.sortedBy { it.time }
                    if (times.isEmpty()) Text(stringResource(R.string.detail_no_times))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        times.forEach { slot ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                                Text(formatDetailTime(slot.time, locale), Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    Text(when (plan.schedule.timeZoneMode) {
                        TimeZoneMode.DEVICE -> stringResource(R.string.detail_zone_device)
                        TimeZoneMode.MANUAL -> stringResource(R.string.detail_zone_manual, plan.schedule.manualZoneId.orEmpty())
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (plan.medication.note.isNotBlank()) {
                item {
                    DetailSection {
                        Text(stringResource(R.string.detail_note_label), style = MaterialTheme.typography.titleMedium)
                        Text(plan.medication.note, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun DetailMedicationImage(uri: Uri, modifier: Modifier) {
    AndroidView(
        factory = { context ->
            android.widget.ImageView(context).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = context.getString(R.string.detail_photo_description)
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

internal fun isEveryDay(days: Set<DayOfWeek>): Boolean = days.size == DayOfWeek.entries.size

internal fun formatDetailTime(time: LocalTime, locale: Locale): String = time.format(
    DateTimeFormatter.ofPattern(if (locale.language == "zh") "HH:mm" else "h:mm a", locale),
)

private fun detailDateFormatter(locale: Locale): DateTimeFormatter = DateTimeFormatter.ofPattern(
    if (locale.language == "zh") "M月d日 EEEE" else "EEE, MMM d", locale,
)
