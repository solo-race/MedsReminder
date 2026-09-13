package com.example.medicationreminder.ui

import android.app.Application
import android.net.Uri
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
import com.example.medicationreminder.reminders.nextUndecidedOccurrence
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
            container.preferences.updateAndGetPreviousDeviceZone(ZoneId.systemDefault().id)
            container.scheduler.scheduleAll()
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
    fun onReminderIntent(entry: ReminderEntry, reapplied: Boolean) {
        if (reapplied && entry == lastDeliveredEntry) return
        lastDeliveredEntry = entry
        _reminderEntry.value = entry
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
        viewModelScope.launch { container.preferences.setLanguage(language) }
    }

    fun dismissPermissionPrompt() {
        viewModelScope.launch { container.preferences.setPermissionPromptDismissed() }
    }

    fun setRepostMissedReminders(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setRepostMissedReminders(enabled) }
    }

    fun newCameraCaptureUri(): Uri = container.imageStore.newCameraCaptureUri()

    suspend fun nextActionableOccurrence(medicationId: Long, after: Instant): DoseOccurrence? = withContext(Dispatchers.IO) {
        var earliest: DoseOccurrence? = null
        for (dose in container.repository.activeScheduledDoses()) {
            if (dose.medicationId != medicationId) continue
            val occurrence = nextUndecidedOccurrence(dose, after) { candidate ->
                container.repository.hasDoseDecisionOnLocalDay(
                    candidate.doseTimeId,
                    candidate.scheduledFor,
                    candidate.zoneId,
                )
            } ?: continue
            if (earliest == null || occurrence.scheduledFor < earliest.scheduledFor) {
                earliest = occurrence
            }
        }
        earliest
    }

    suspend fun isDoseDecided(occurrence: DoseOccurrence): Boolean = withContext(Dispatchers.IO) {
        container.repository.hasDoseDecisionOnLocalDay(
            occurrence.doseTimeId,
            occurrence.scheduledFor,
            occurrence.zoneId,
        )
    }

    fun decideDose(
        occurrence: DoseOccurrence,
        status: DoseStatus,
        onResult: (DoseDecisionResult) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                container.doseDecisionUseCase(occurrence, status)
            }
            onResult(result)
        }
    }

    fun saveMedication(
        draft: MedicationDraft,
        previousPlan: MedicationPlan?,
        newImageUri: Uri?,
        removePhoto: Boolean,
        onSaved: () -> Unit,
    ) {
        if (draft.name.isBlank() || draft.dosageText.isBlank() || draft.times.isEmpty() || draft.weekdays.isEmpty()) {
            _error.value = R.string.error_missing_medication_fields
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    previousPlan?.times?.forEach {
                        container.scheduler.cancel(it.id)
                        container.notifications.cancelReminder(it.id)
                    }
                    var newlyStoredPhoto: String? = null
                    try {
                        val photoPath = when {
                            newImageUri != null -> container.imageStore.importFrom(newImageUri).also { newlyStoredPhoto = it }
                            removePhoto -> null
                            else -> draft.existingPhotoPath
                        }
                        val result = container.repository.saveMedication(draft.copy(existingPhotoPath = photoPath))
                        container.imageStore.delete(result.replacedPhotoPath)
                        container.scheduler.scheduleAll()
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
                viewModelScope.launch(Dispatchers.IO) { container.scheduler.scheduleAll() }
            }
        }
    }

    fun deleteMedication(plan: MedicationPlan, onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    plan.times.forEach {
                        container.scheduler.cancel(it.id)
                        container.notifications.cancelReminder(it.id)
                    }
                    val photo = container.repository.deleteMedication(plan.medication.id)
                    container.imageStore.delete(photo)
                    container.scheduler.scheduleAll()
                }
            }.onSuccess { onDeleted() }
                .onFailure { _error.value = R.string.error_delete_medication }
        }
    }
}
