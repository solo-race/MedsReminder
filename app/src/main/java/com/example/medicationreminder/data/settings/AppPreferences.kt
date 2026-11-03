package com.example.medicationreminder.data.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reminderDataStore by preferencesDataStore(name = "reminder_preferences")

enum class AppLanguage(val preferenceValue: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    SIMPLIFIED_CHINESE("zh-CN"),
    ;

    companion object {
        fun fromTag(languageTag: String?): AppLanguage =
            entries.firstOrNull { it.preferenceValue == languageTag } ?: SYSTEM
    }

    fun localeFor(systemLocale: Locale): Locale = when (this) {
        SYSTEM -> if (systemLocale.language == "zh") Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
        ENGLISH -> Locale.ENGLISH
        SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
    }
}

/** Resolve from the unmodified context each time; never change the process/system locale. */
fun Context.withAppLanguage(language: AppLanguage): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(language.localeFor(configuration.locales[0]))
    val localizedResources = createConfigurationContext(configuration).resources
    // Keep the Activity's owners/services available to Compose and embedded views.
    return object : ContextWrapper(this) {
        override fun getResources(): android.content.res.Resources = localizedResources
    }
}

class AppPreferences(private val context: Context) {
    val language: Flow<AppLanguage> = context.reminderDataStore.data
        .map { AppLanguage.fromTag(it[APP_LANGUAGE]) }
        .distinctUntilChanged()

    suspend fun setLanguage(language: AppLanguage) {
        context.reminderDataStore.edit {
            if (language == AppLanguage.SYSTEM) it.remove(APP_LANGUAGE)
            else it[APP_LANGUAGE] = language.preferenceValue
        }
    }

    val permissionPromptDismissed: Flow<Boolean> = context.reminderDataStore.data
        .map { it[PERMISSION_PROMPT_DISMISSED] == true }
        .distinctUntilChanged()

    suspend fun setPermissionPromptDismissed() {
        context.reminderDataStore.edit { it[PERMISSION_PROMPT_DISMISSED] = true }
    }

    val exactAlarmPromptDismissed: Flow<Boolean> = context.reminderDataStore.data
        .map { it[EXACT_ALARM_PROMPT_DISMISSED] == true }
        .distinctUntilChanged()

    suspend fun setExactAlarmPromptDismissed() {
        context.reminderDataStore.edit { it[EXACT_ALARM_PROMPT_DISMISSED] = true }
    }

    val reminderSoundEnabled: Flow<Boolean> = context.reminderDataStore.data
        .map { it[REMINDER_SOUND_ENABLED] ?: true }.distinctUntilChanged()

    suspend fun setReminderSoundEnabled(enabled: Boolean) {
        context.reminderDataStore.edit { it[REMINDER_SOUND_ENABLED] = enabled }
    }

    val repostMissedReminders: Flow<Boolean> = context.reminderDataStore.data
        .map { it[REPOST_MISSED_REMINDERS] == true }
        .distinctUntilChanged()

    suspend fun setRepostMissedReminders(enabled: Boolean) {
        context.reminderDataStore.edit { it[REPOST_MISSED_REMINDERS] = enabled }
    }

    suspend fun updateAndGetPreviousDeviceZone(currentZoneId: String): String? {
        val previous = context.reminderDataStore.data.first()[LAST_DEVICE_ZONE]
        context.reminderDataStore.edit { it[LAST_DEVICE_ZONE] = currentZoneId }
        return previous
    }

    private companion object {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val PERMISSION_PROMPT_DISMISSED = booleanPreferencesKey("permission_prompt_dismissed")
        val EXACT_ALARM_PROMPT_DISMISSED = booleanPreferencesKey("exact_alarm_prompt_dismissed")
        val REPOST_MISSED_REMINDERS = booleanPreferencesKey("repost_missed_reminders")
        val REMINDER_SOUND_ENABLED = booleanPreferencesKey("reminder_sound_enabled")
        val LAST_DEVICE_ZONE = stringPreferencesKey("last_device_zone")
    }
}
