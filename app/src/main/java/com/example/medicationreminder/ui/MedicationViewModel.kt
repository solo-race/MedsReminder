package com.example.medicationreminder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicationreminder.MedicationReminderApplication
import com.example.medicationreminder.R
import com.example.medicationreminder.data.settings.AppLanguage
import com.example.medicationreminder.domain.model.MedicationDraft
import com.example.medicationreminder.domain.model.MedicationPlan
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            container.preferences.updateAndGetPreviousDeviceZone(ZoneId.systemDefault().id)
            container.scheduler.scheduleAll()
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { container.preferences.setLanguage(language) }
    }

    fun newCameraCaptureUri(): Uri = container.imageStore.newCameraCaptureUri()

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
                // Re-establish previous alarms if saving failed after cancelling an edited schedule.
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
