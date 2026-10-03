package com.example.medicationreminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.ui.MedicationApp
import com.example.medicationreminder.ui.MedicationViewModel
import com.example.medicationreminder.ui.ReminderEntry
import com.example.medicationreminder.ui.theme.MedicationReminderTheme
import java.time.Instant
import java.time.ZoneId

internal fun navigationDoseOccurrenceOrNull(
    medicationId: Long,
    doseTimeId: Long,
    scheduledForEpochMillis: Long,
    zoneIdValue: String?,
): DoseOccurrence? {
    val zoneId = runCatching { ZoneId.of(zoneIdValue.orEmpty()) }.getOrNull()
    if (medicationId < 0 || doseTimeId < 0 || scheduledForEpochMillis < 0 || zoneId == null) return null
    return DoseOccurrence(
        medicationId = medicationId,
        doseTimeId = doseTimeId,
        scheduledFor = Instant.ofEpochMilli(scheduledForEpochMillis),
        zoneId = zoneId,
    )
}

class MainActivity : ComponentActivity() {
    private val viewModel: MedicationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyIntent(intent, reapplied = true)
        setContent {
            MedicationReminderTheme {
                MedicationApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent, reapplied = false)
    }

    /**
     * Parses the reminder extras for both delivery paths: the launch intent read in [onCreate]
     * (re-applied whenever the Activity is recreated) and a new intent delivered to the running
     * Activity in [onNewIntent]. The ViewModel deduplicates the re-application.
     */
    private fun applyIntent(intent: Intent, reapplied: Boolean) {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1)
        if (medicationId < 0) return
        viewModel.onReminderIntent(
            entry = ReminderEntry(
                medicationId = medicationId,
                occurrence = navigationDoseOccurrenceOrNull(
                    medicationId = medicationId,
                    doseTimeId = intent.getLongExtra(EXTRA_DOSE_TIME_ID, -1),
                    scheduledForEpochMillis = intent.getLongExtra(EXTRA_SCHEDULED_FOR, -1),
                    zoneIdValue = intent.getStringExtra(EXTRA_ZONE_ID),
                ),
            ),
            reapplied = reapplied,
        )
    }

    companion object {
        const val EXTRA_MEDICATION_ID = "open_medication_id"
        const val EXTRA_DOSE_TIME_ID = "open_dose_time_id"
        const val EXTRA_SCHEDULED_FOR = "open_scheduled_for"
        const val EXTRA_ZONE_ID = "open_zone_id"
    }
}
