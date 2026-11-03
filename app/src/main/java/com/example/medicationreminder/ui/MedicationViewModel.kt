package com.example.medicationreminder.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicationreminder.MedicationReminderApplication
import com.example.medicationreminder.R
import com.example.medicationreminder.data.settings.AppLanguage
import com.example.medicationreminder.domain.model.DoseDecisionResult
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.MedicationDraft
import com.example.medicationreminder.domain.model.MedicationPlan
import com.example.medicationreminder.domain.model.ScheduleMode
import com.example.medicationreminder.domain.model.ReminderStage
import com.example.medicationreminder.reminders.ScheduleResult
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Medication and optional explicit dose occurrence the user opened from a reminder entry point
 * (notification content intent or alarm status-bar intent). A null occurrence means the intent
 * carried no usable occurrence identity, so Detail falls back to the next actionable dose.
 */
data class ReminderEntry(val medicationId: Long, val occurrence: DoseOccurrence?)

class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MedicationReminderApplication).container

    val plans = container.repository.observeMedicationPlans().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val pending = container.repository.observePendingReminders().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminderSoundEnabled = container.preferences.reminderSoundEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setReminderSoundEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.preferences.setReminderSoundEnabled(enabled)
                container.missedReminderReposter.restorePending()
            }.onFailure { _error.value = R.string.error_save_settings }
        }
    }

    fun onAppResumed() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.restoreReminders() }.onFailure { Log.e(TAG, "Resume recovery failed", it) }
        }
    }

    fun invalidScheduleInput() { _error.value = R.string.error_interval_input }

    val history = container.repository.observeDoseHistory().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val language = container.preferences.language.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppLanguage.ENGLISH,
    )

    val permissionPromptDismissed = container.preferences.permissionPromptDismissed.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )

    val exactAlarmPromptDismissed = container.preferences.exactAlarmPromptDismissed.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true,
    )

    val repostMissedReminders = container.preferences.repostMissedReminders.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    private val _reminderEntry = MutableStateFlow<ReminderEntry?>(null)

    /** Reminder delivery that still has to be opened by the UI; filled by [onReminderIntent]. */
    val reminderEntry: StateFlow<ReminderEntry?> = _reminderEntry.asStateFlow()

    private val _reminderOccurrence = MutableStateFlow<DoseOccurrence?>(null)

    /**
     * Explicit occurrence of the delivery the UI last opened; Detail binds it instead of deriving
     * one from the current clock. Retained across Activity recreation and cleared whenever Detail
     * is opened from Home.
     */
    val reminderOccurrence: StateFlow<DoseOccurrence?> = _reminderOccurrence.asStateFlow()

    private var lastDeliveredEntry: ReminderEntry? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                container.preferences.updateAndGetPreviousDeviceZone(ZoneId.systemDefault().id)
                container.restoreReminders()
            }.onFailure { error ->
                Log.e(TAG, "Unable to restore reminder alarms from the app process", error)
            }.onSuccess { result ->
                if (result == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                    Log.i(TAG, "App-process reminder restore is waiting for exact-alarm access")
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Records a reminder delivery from the Activity's launch or new intent.
     *
     * [reapplied] marks the launch intent being read again while the Activity is recreated: a
     * re-application that repeats the entry already delivered to this ViewModel is ignored, so
     * recreation does not replay navigation. A genuine new delivery always counts, so a repeated
     * notification tap re-opens its Detail.
     */
    fun onReminderIntent(entry: ReminderEntry, reapplied: Boolean, legacy: Boolean = false) {
        if (reapplied && entry == lastDeliveredEntry) return
        lastDeliveredEntry = entry
        _reminderEntry.value = entry
        if (legacy) entry.occurrence?.let { occurrence ->
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { container.missedReminderReposter.deliverOccurrence(occurrence,
                    if (occurrence.scheduledFor.isAfter(Instant.now())) ReminderStage.PRE else ReminderStage.DUE,
                    alert = false, legacy = true) }.onFailure { Log.e(TAG, "Legacy reminder conversion failed", it) }
            }
        }
    }

    /**
     * Retires the pending [reminderEntry] for the UI that is opening it and reports which entry was
     * retired: its explicit occurrence is bound for Detail and the delivery is dropped so Activity
     * recreation does not replay it. The caller must navigate with the returned entry, so a newer
     * delivery arriving in the same frame cannot bind its occurrence to another medication.
     */
    fun reminderEntryOpened(): ReminderEntry? {
        val entry = _reminderEntry.value ?: return null
        _reminderOccurrence.value = entry.occurrence
        _reminderEntry.value = null
        return entry
    }

    /** Binds Detail opened from Home to a schedule-derived occurrence again. */
    fun clearReminderOccurrence() {
        _reminderOccurrence.value = null
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            container.preferences.setLanguage(language)
            container.notifications.createChannels(language.languageTag)
            container.missedReminderReposter.restorePending()
        }
    }

    fun dismissPermissionPrompt() {
        viewModelScope.launch { container.preferences.setPermissionPromptDismissed() }
    }

    fun dismissExactAlarmPrompt() {
        viewModelScope.launch { container.preferences.setExactAlarmPromptDismissed() }
    }

    fun onExactAlarmPermissionRestored() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (container.scheduler.canScheduleExactAlarms()) {
                    container.restoreReminders()
                } else {
                    ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to restore reminder alarms after exact-alarm access", error)
            }.onSuccess { result ->
                if (result == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                    Log.i(TAG, "Reminder restore remains waiting for exact-alarm access")
                }
            }
        }
    }

    fun setRepostMissedReminders(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setRepostMissedReminders(enabled) }
    }

    fun newCameraCaptureUri(): Uri = container.imageStore.newCameraCaptureUri()

    suspend fun nextActionableOccurrence(medicationId: Long, now: Instant): DoseOccurrence? = withContext(Dispatchers.IO) {
        val active = container.repository.activeScheduledDoses()
        val oldest = container.repository.pendingReminders(medicationId).firstOrNull { pending ->
            active.any { it.matchesOccurrence(pending.occurrence, persistedPending = true) } &&
                !container.repository.hasDoseDecision(pending.occurrence)
        }
        oldest?.occurrence ?: homeDetailOccurrence(active, medicationId, now) { candidate ->
            container.repository.hasDoseDecision(candidate)
        }
    }

    suspend fun isPersistedPending(occurrence: DoseOccurrence): Boolean = withContext(Dispatchers.IO) {
        container.repository.pendingReminders(occurrence.medicationId).any { it.matches(occurrence) }
    }

    suspend fun isDoseDecided(occurrence: DoseOccurrence): Boolean = withContext(Dispatchers.IO) {
        container.repository.hasDoseDecision(occurrence)
    }

    fun loadNextOccurrence(medicationId: Long, onLoaded: (DoseOccurrence?) -> Unit) {
        viewModelScope.launch { onLoaded(nextActionableOccurrence(medicationId, Instant.now())) }
    }

    fun decideDose(
        occurrence: DoseOccurrence,
        status: DoseStatus,
        onResult: (DoseDecisionResult) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { container.doseDecisionUseCase(occurrence, status) }
            }.onSuccess(onResult).onFailure { error ->
                Log.e(TAG, "Dose decision failed; notification retained", error)
                _error.value = R.string.error_decide_dose
            }
        }
    }

    fun saveMedication(
        draft: MedicationDraft,
        previousPlan: MedicationPlan?,
        newImageUri: Uri?,
        removePhoto: Boolean,
        onSaved: () -> Unit,
    ) {
        if (draft.name.isBlank() || draft.dosageText.isBlank() || (draft.scheduleMode == ScheduleMode.FIXED_TIMES && (draft.times.isEmpty() || draft.weekdays.isEmpty()))) {
            _error.value = R.string.error_missing_medication_fields
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    var newlyStoredPhoto: String? = null
                    try {
                        val photoPath = when {
                            newImageUri != null -> container.imageStore.importFrom(newImageUri).also { newlyStoredPhoto = it }
                            removePhoto -> null
                            else -> draft.existingPhotoPath
                        }
                        val result = container.repository.saveMedication(draft.copy(existingPhotoPath = photoPath))
                        // Only a committed explicit schedule change may clear the old reminder.
                        if (previousPlan != null && reminderPlanChanged(previousPlan, draft)) {
                            previousPlan.times.forEach {
                                container.scheduler.cancel(it.id)
                                container.notifications.cancelReminder(it.id)
                            }
                        }
                        container.imageStore.delete(result.replacedPhotoPath)
                        val scheduleResult = container.restoreReminders()
                        if (scheduleResult == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                            Log.i(TAG, "Saved medication reminder rebuild is waiting for exact-alarm access")
                        }
                        result
                    } catch (error: Exception) {
                        container.imageStore.delete(newlyStoredPhoto)
                        throw error
                    }
                }
            }.onSuccess {
                onSaved()
            }.onFailure {
                _error.value = R.string.error_save_medication
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { container.restoreReminders() }
                        .onSuccess { result ->
                            if (result == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                                Log.i(TAG, "Retry reminder rebuild is waiting for exact-alarm access")
                            }
                        }
                        .onFailure { error ->
                            Log.e(TAG, "Unable to retry reminder alarm rebuild after save failure", error)
                        }
                }
            }
        }
    }

    fun deleteMedication(plan: MedicationPlan, onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val photo = container.repository.deleteMedication(plan.medication.id)
                    plan.times.forEach {
                        container.scheduler.cancel(it.id)
                        container.notifications.cancelReminder(it.id)
                    }
                    container.imageStore.delete(photo)
                    val scheduleResult = container.restoreReminders()
                    if (scheduleResult == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                        Log.i(TAG, "Deleted medication reminder rebuild is waiting for exact-alarm access")
                    }
                }
            }.onSuccess { onDeleted() }
                .onFailure { _error.value = R.string.error_delete_medication }
        }
    }

    private companion object {
        const val TAG = "MedicationViewModel"
    }
}

internal fun reminderPlanChanged(plan: MedicationPlan, draft: MedicationDraft): Boolean {
    if (plan.medication.enabled != draft.enabled || plan.schedule.scheduleMode != draft.scheduleMode) return true
    if (draft.scheduleMode == ScheduleMode.INTERVAL) return plan.schedule.intervalMinutes != draft.intervalMinutes ||
        plan.schedule.anchorEpochMillis != draft.anchorEpochMillis
    return plan.times.filter { it.enabled }.map { it.time }.sorted() != draft.times.distinct().sorted() ||
        plan.schedule.weekdays != draft.weekdays || plan.schedule.timeZoneMode != draft.timeZoneMode ||
        plan.schedule.manualZoneId != draft.manualZoneId
}
