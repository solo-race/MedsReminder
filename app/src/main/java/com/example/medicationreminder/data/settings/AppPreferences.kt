package com.example.medicationreminder.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.reminderDataStore by preferencesDataStore(name = "reminder_preferences")

class AppPreferences(private val context: Context) {
    suspend fun updateAndGetPreviousDeviceZone(currentZoneId: String): String? {
        val previous = context.reminderDataStore.data.first()[LAST_DEVICE_ZONE]
        context.reminderDataStore.edit { it[LAST_DEVICE_ZONE] = currentZoneId }
        return previous
    }

    private companion object {
        val LAST_DEVICE_ZONE = stringPreferencesKey("last_device_zone")
    }
}
