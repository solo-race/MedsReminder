package com.example.medicationreminder.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reminderDataStore by preferencesDataStore(name = "reminder_preferences")

enum class AppLanguage(val languageTag: String) {
    ENGLISH("en"),
    SIMPLIFIED_CHINESE("zh-CN"),
    ;

    companion object {
        fun fromTag(languageTag: String?): AppLanguage =
            entries.firstOrNull { it.languageTag == languageTag } ?: ENGLISH
    }
}

class AppPreferences(private val context: Context) {
    val language: Flow<AppLanguage> = context.reminderDataStore.data
        .map { AppLanguage.fromTag(it[APP_LANGUAGE]) }
        .distinctUntilChanged()

    suspend fun setLanguage(language: AppLanguage) {
        context.reminderDataStore.edit { it[APP_LANGUAGE] = language.languageTag }
    }

    suspend fun updateAndGetPreviousDeviceZone(currentZoneId: String): String? {
        val previous = context.reminderDataStore.data.first()[LAST_DEVICE_ZONE]
        context.reminderDataStore.edit { it[LAST_DEVICE_ZONE] = currentZoneId }
        return previous
    }

    private companion object {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val LAST_DEVICE_ZONE = stringPreferencesKey("last_device_zone")
    }
}
