@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.example.medicationreminder.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medicationreminder.R
import com.example.medicationreminder.domain.model.DoseDecisionResult
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.MedicationPlan
import com.example.medicationreminder.domain.model.TimeZoneMode
import com.example.medicationreminder.domain.model.scheduledDose
import com.example.medicationreminder.domain.model.ScheduleMode
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

/** A producer may retain its previous value while the new key loads. Never expose that value. */
internal data class LoadedDetailDoseState(val key: DetailDoseStateKey, val state: DetailDoseState) {
    fun forKey(currentKey: DetailDoseStateKey): DetailDoseState? = state.takeIf { key == currentKey }
}

internal fun detailDoseStateKey(
    plan: MedicationPlan?,
    explicitOccurrence: DoseOccurrence?,
): DetailDoseStateKey = DetailDoseStateKey(plan, explicitOccurrence)

internal fun detailCompletionIsCurrent(requestKey: DetailDoseStateKey, requestOccurrence: DoseOccurrence,
    currentKey: DetailDoseStateKey, currentOccurrence: DoseOccurrence?): Boolean =
    requestKey == currentKey && requestOccurrence == currentOccurrence

internal fun preferredDetailOccurrence(
    explicitOccurrence: DoseOccurrence?,
    upcomingOccurrence: DoseOccurrence?,
): DoseOccurrence? = explicitOccurrence ?: upcomingOccurrence

internal fun isOccurrenceCompatibleWithPlan(
    plan: MedicationPlan, occurrence: DoseOccurrence, persistedPending: Boolean = false,
): Boolean {
    return plan.scheduledDose(occurrence.doseTimeId)?.matchesOccurrence(occurrence, persistedPending) == true
}

internal enum class DoseConfirmationPhase { EARLY, DUE }

internal fun doseConfirmationPhase(occurrence: DoseOccurrence, now: Instant): DoseConfirmationPhase =
    if (now.isBefore(occurrence.scheduledFor)) DoseConfirmationPhase.EARLY else DoseConfirmationPhase.DUE

/** Presentation state derived from the loaded dose data and the ticking display clock. */
internal sealed interface DetailDosePresentation {
    data object Loading : DetailDosePresentation
    data object Stale : DetailDosePresentation
    data object Unavailable : DetailDosePresentation
    data class Decided(val status: DoseStatus?) : DetailDosePresentation
    data class Actionable(
        val occurrence: DoseOccurrence,
        val confirmation: DoseConfirmationPhase,
    ) : DetailDosePresentation
}

internal fun detailDosePresentation(state: DetailDoseState?, now: Instant): DetailDosePresentation = when {
    state == null -> DetailDosePresentation.Loading
    state.stale -> DetailDosePresentation.Stale
    state.occurrence == null -> DetailDosePresentation.Unavailable
    state.decided -> DetailDosePresentation.Decided(state.decidedStatus)
    else -> DetailDosePresentation.Actionable(
        occurrence = state.occurrence,
        confirmation = doseConfirmationPhase(state.occurrence, now),
    )
}

internal enum class DetailDoseHeading { REMINDER, UNCONFIRMED, NEXT }

/** Heading keeps its existing rule, including decided or stale occurrences in the past. */
internal fun detailDoseHeading(
    explicitOccurrence: DoseOccurrence?,
    occurrence: DoseOccurrence?,
    now: Instant,
): DetailDoseHeading = when {
    explicitOccurrence != null -> DetailDoseHeading.REMINDER
    occurrence != null && !now.isBefore(occurrence.scheduledFor) -> DetailDoseHeading.UNCONFIRMED
    else -> DetailDoseHeading.NEXT
}

@Composable
internal fun MedicationDetailScreen(
    plan: MedicationPlan?,
    explicitOccurrence: DoseOccurrence?,
    viewModel: MedicationViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val displayNow by occurrenceClock()
    val stateKey = detailDoseStateKey(plan, explicitOccurrence)
    // Selection uses the load-time clock; every visible time-dependent element uses displayNow.
    val loadNow = remember(stateKey) { Instant.now() }
    val initialLoad by produceState<LoadedDetailDoseState?>(initialValue = null, stateKey) {
        val loadedState = when {
            plan == null -> DetailDoseState(stale = true)
            explicitOccurrence != null -> {
                if (!isOccurrenceCompatibleWithPlan(plan, explicitOccurrence, viewModel.isPersistedPending(explicitOccurrence))) {
                    DetailDoseState(occurrence = explicitOccurrence, stale = true)
                } else {
                    DetailDoseState(
                        occurrence = explicitOccurrence,
                        decided = viewModel.isDoseDecided(explicitOccurrence),
                    )
                }
            }
            else -> DetailDoseState(
                occurrence = viewModel.nextActionableOccurrence(plan.medication.id, loadNow),
            )
        }
        value = LoadedDetailDoseState(stateKey, loadedState)
    }
    val initialState = initialLoad?.forKey(stateKey)
    var state by remember(stateKey) { mutableStateOf<DetailDoseState?>(null) }
    val currentKey by rememberUpdatedState(stateKey)
    val currentOccurrence by rememberUpdatedState((state ?: initialState)?.occurrence)
    val applyDecision: (DetailDoseState, ReminderContext, DoseDecisionResult) -> Unit = decision@{ requestState, requestContext, result ->
        val requested = requestState.occurrence ?: return@decision
        if (!detailCompletionIsCurrent(stateKey, requested, currentKey, currentOccurrence) ||
            !viewModel.isCurrentReminderContext(requestContext)) return@decision
        state = when (result) {
            is DoseDecisionResult.Recorded -> requestState.copy(decided = true, decidedStatus = result.status)
            is DoseDecisionResult.AlreadyDecided -> requestState.copy(decided = true, decidedStatus = result.status)
            DoseDecisionResult.StaleOccurrence -> requestState.copy(stale = true)
        }
        // A decision that arrived from elsewhere must close, not re-arm, an early confirmation.
        val pending = viewModel.pendingEarlyDecision.value
        if (pending?.occurrence == requested && result !is DoseDecisionResult.Recorded) {
            viewModel.clearPendingEarlyDecision(pending)
        }
        if (result != DoseDecisionResult.StaleOccurrence) {
            if (stateKey.explicitOccurrence != null) {
                // Changing this binding creates a new key; the existing keyed producer loads its next dose.
                viewModel.clearReminderOccurrenceIfCurrent(stateKey.explicitOccurrence)
            } else {
                viewModel.loadNextOccurrence(requested.medicationId, isCurrent = {
                    detailCompletionIsCurrent(stateKey, requested, currentKey, currentOccurrence) &&
                        viewModel.isCurrentReminderContext(requestContext)
                }) { state = DetailDoseState(occurrence = it) }
            }
        }
    }
    LaunchedEffect(stateKey, initialLoad) {
        if (initialState != null) state = initialState
    }

    val doseState = state ?: initialState
    val presentation = detailDosePresentation(doseState, displayNow)
    val pendingEarlyDecision by viewModel.pendingEarlyDecision.collectAsStateWithLifecycle()

    // Keep a one-shot PRE request only while the occurrence Detail is showing is still actionable.
    // A missing plan is still loading or deleted; do not drop the request before lookup finishes.
    LaunchedEffect(stateKey, doseState, pendingEarlyDecision) {
        val pending = pendingEarlyDecision ?: return@LaunchedEffect
        if (plan == null || doseState == null) return@LaunchedEffect
        val boundToPending = stateKey.explicitOccurrence == pending.occurrence ||
            (stateKey.explicitOccurrence == null && doseState.occurrence == pending.occurrence)
        if (!boundToPending) return@LaunchedEffect
        val current = detailDosePresentation(doseState, Instant.now())
        if (current !is DetailDosePresentation.Actionable || current.occurrence != pending.occurrence) {
            viewModel.clearPendingEarlyDecision(pending)
        }
    }

    Scaffold(
        snackbarHost = snackbarHost,
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
                    Text(stringResource(when (detailDoseHeading(explicitOccurrence, occurrence, displayNow)) {
                        DetailDoseHeading.REMINDER -> R.string.detail_reminder_heading
                        DetailDoseHeading.UNCONFIRMED -> R.string.detail_pending_heading
                        DetailDoseHeading.NEXT -> R.string.detail_next_heading
                    }), style = MaterialTheme.typography.titleMedium)
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
                    if (occurrence != null && doseState.decided != true) Text(occurrenceCountdown(occurrence, displayNow))
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
                    if (presentation is DetailDosePresentation.Actionable) {
                        val actionableOccurrence = presentation.occurrence
                        val early = presentation.confirmation == DoseConfirmationPhase.EARLY
                        if (early) {
                            val local = actionableOccurrence.scheduledFor.atZone(actionableOccurrence.zoneId)
                            Text(
                                stringResource(
                                    R.string.detail_early_notice,
                                    "${detailDateFormatter(locale).format(local)} " +
                                        formatDetailTime(local.toLocalTime(), locale),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        // Full-width actions also work at large font scales without horizontal clipping.
                        Button(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = if (early) ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            ) else ButtonDefaults.buttonColors(),
                            onClick = {
                                val currentState = doseState ?: return@Button
                                val occurrence = currentState.occurrence ?: return@Button
                                val requestContext = viewModel.reminderContext()
                                if (early) {
                                    viewModel.requestEarlyDecision(occurrence, DoseStatus.TAKEN)
                                } else {
                                    viewModel.decideDose(occurrence, DoseStatus.TAKEN) { result ->
                                        applyDecision(currentState, requestContext, result)
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.detail_take_action), textAlign = TextAlign.Center) }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            border = if (early) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary) else null,
                            colors = if (early) ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.tertiary,
                            ) else ButtonDefaults.outlinedButtonColors(),
                            onClick = {
                                val currentState = doseState ?: return@OutlinedButton
                                val occurrence = currentState.occurrence ?: return@OutlinedButton
                                val requestContext = viewModel.reminderContext()
                                if (early) {
                                    viewModel.requestEarlyDecision(occurrence, DoseStatus.SKIPPED)
                                } else {
                                    viewModel.decideDose(occurrence, DoseStatus.SKIPPED) { result ->
                                        applyDecision(currentState, requestContext, result)
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.detail_skip_action), textAlign = TextAlign.Center) }
                    }
                }
            }
            item {
                DetailSection {
                    Text(stringResource(R.string.detail_schedule_label), style = MaterialTheme.typography.titleMedium)
                    if (plan.schedule.scheduleMode == ScheduleMode.INTERVAL) {
                        Text(stringResource(R.string.interval_schedule_description, plan.schedule.intervalMinutes ?: 0))
                        plan.schedule.anchorEpochMillis?.let { anchor ->
                            Text(DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
                                .format(Instant.ofEpochMilli(anchor).atZone(plan.schedule.zoneIdForDetail())))
                        }
                    } else {
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

    if (presentation is DetailDosePresentation.Actionable) {
        val pending = pendingEarlyDecision?.takeIf { it.occurrence == presentation.occurrence }
        if (pending != null) {
            val local = pending.occurrence.scheduledFor.atZone(pending.occurrence.zoneId)
            val scheduledText = "${detailDateFormatter(locale).format(local)} " +
                formatDetailTime(local.toLocalTime(), locale)
            val early = presentation.confirmation == DoseConfirmationPhase.EARLY
            AlertDialog(
                onDismissRequest = { viewModel.clearPendingEarlyDecision(pending) },
                title = {
                    Text(stringResource(when (pending.status) {
                        DoseStatus.TAKEN -> R.string.early_confirm_taken_title
                        DoseStatus.SKIPPED -> R.string.early_confirm_skip_title
                    }))
                },
                text = { Text(stringResource(R.string.early_confirm_message, scheduledText)) },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (early) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary,
                            contentColor = if (early) MaterialTheme.colorScheme.onTertiary
                                else MaterialTheme.colorScheme.onPrimary,
                        ),
                        onClick = {
                            val currentState = doseState ?: return@Button
                            val requestContext = viewModel.reminderContext()
                            viewModel.clearPendingEarlyDecision(pending)
                            viewModel.decideDose(pending.occurrence, pending.status) { result ->
                                applyDecision(currentState, requestContext, result)
                            }
                        },
                    ) { Text(stringResource(R.string.early_confirm_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearPendingEarlyDecision(pending) }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
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
