package com.example.medicationreminder.data.settings

import androidx.lifecycle.ViewModelStore
import android.os.Looper
import com.example.medicationreminder.R
import com.example.medicationreminder.ui.MedicationViewModel
import java.util.Locale
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLanguageTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After fun resetPreference() = runBlocking { AppPreferences(context).setLanguage(AppLanguage.SYSTEM) }

    @Test fun missingAndLegacyValuesKeepTheirIntendedMeaning() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("unknown"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromTag("zh-CN"))
    }

    @Test fun systemUsesSupportedChineseOrFallsBackToEnglish() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, AppLanguage.SYSTEM.localeFor(Locale.CHINA))
        assertEquals(Locale.SIMPLIFIED_CHINESE, AppLanguage.SYSTEM.localeFor(Locale.TAIWAN))
        assertEquals(Locale.ENGLISH, AppLanguage.SYSTEM.localeFor(Locale.US))
        assertEquals(Locale.ENGLISH, AppLanguage.SYSTEM.localeFor(Locale.FRANCE))
    }

    @Test fun returningToSystemClearsTheExplicitChoiceAndPersists() = runBlocking {
        val preferences = AppPreferences(context)
        assertEquals(AppLanguage.SYSTEM, preferences.language.first())
        preferences.setLanguage(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, AppPreferences(context).language.first())
        preferences.setLanguage(AppLanguage.SIMPLIFIED_CHINESE)
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppPreferences(context).language.first())
        preferences.setLanguage(AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, AppPreferences(context).language.first())
    }

    @Test fun resourceContextTracksConfigurationWithoutOverridingSystemOrExplicitChoice() {
        RuntimeEnvironment.setQualifiers("zh-rCN")
        assertEquals("语言", context.withAppLanguage(AppLanguage.SYSTEM).getString(R.string.language_title))
        assertEquals("Language", context.withAppLanguage(AppLanguage.ENGLISH).getString(R.string.language_title))
        assertEquals("zh", context.resources.configuration.locales[0].language)
        RuntimeEnvironment.setQualifiers("en-rUS")
        assertEquals("Language", context.withAppLanguage(AppLanguage.SYSTEM).getString(R.string.language_title))
        assertEquals("语言", context.withAppLanguage(AppLanguage.SIMPLIFIED_CHINESE).getString(R.string.language_title))
        RuntimeEnvironment.setQualifiers("fr-rFR")
        assertEquals("Language", context.withAppLanguage(AppLanguage.SYSTEM).getString(R.string.language_title))
    }

    @Test fun coldViewModelLoadsSavedChoiceBeforeUiUsesIt() = runBlocking {
        RuntimeEnvironment.setQualifiers("zh-rCN")
        val preferences = AppPreferences(context)
        for (choice in listOf(AppLanguage.ENGLISH, AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.SYSTEM)) {
            preferences.setLanguage(choice)
            val model = MedicationViewModel(context)
            val store = ViewModelStore().apply { put("language", model) }
            try {
                assertNull(model.language.value)
                val loaded = async { model.language.filterNotNull().first() }
                withTimeout(5_000) {
                    while (!loaded.isCompleted) {
                        shadowOf(Looper.getMainLooper()).idle()
                        delay(10)
                    }
                }
                assertEquals(choice, loaded.await())
                RuntimeEnvironment.setQualifiers("fr-rFR")
                assertEquals(choice, AppPreferences(context).language.first())
            } finally { store.clear() }
        }
    }
}
