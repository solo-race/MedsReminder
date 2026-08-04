package com.example.medicationreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.medicationreminder.ui.MedicationApp
import com.example.medicationreminder.ui.MedicationViewModel
import com.example.medicationreminder.ui.theme.MedicationReminderTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MedicationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedicationReminderTheme {
                MedicationApp(viewModel, intent.getLongExtra(EXTRA_MEDICATION_ID, -1))
            }
        }
    }

    companion object {
        const val EXTRA_MEDICATION_ID = "open_medication_id"
    }
}
