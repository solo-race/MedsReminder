package com.example.medicationreminder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ReminderColors = darkColorScheme(
    primary = Color(0xFFB0C8FF),
    onPrimary = Color(0xFF15213B),
    secondary = Color(0xFFB7E4C7),
    onSecondary = Color(0xFF102117),
    background = Color(0xFF101112),
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF1B1C1E),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF292B2F),
    onSurfaceVariant = Color(0xFFC6C6CB),
    error = Color(0xFFFFB4AB),
)

@Composable
fun MedicationReminderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ReminderColors, content = content)
}
