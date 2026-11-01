package com.example.medicationreminder.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutostartSettingsTest {
    private class RecordingContext(
        val launch: (Intent) -> Unit = {},
    ) : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val attempted = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            attempted += intent
            launch(intent)
        }
    }

    @Test fun oemScreenSuccessReportsNavigationOnlyAndStopsAfterFirstLaunch() {
        val context = RecordingContext()
        assertEquals(AutostartSettingsResult.OEM_SCREEN_OPENED, openAutostartSettings(context, "OPPO", "oppo"))
        val launched = context.attempted.single()
        assertEquals("com.coloros.safecenter", launched.component!!.packageName)
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(launched.data)
        assertNull(launched.extras)
        // Neither navigation result represents a grant or readable permission state.
        assertEquals(
            listOf("OEM_SCREEN_OPENED", "MANUAL_GUIDANCE_REQUIRED"),
            AutostartSettingsResult.entries.map { it.name },
        )
    }

    @Test fun missingOemActivitiesRequireManualGuidanceWithoutOpeningAppDetailsOrSettings() {
        val context = RecordingContext { throw ActivityNotFoundException("OEM activity missing") }
        assertManualOemFailure(context)
    }

    @Test fun privateOemActivitiesRequireManualGuidanceWithoutOpeningAppDetailsOrSettings() {
        val context = RecordingContext { throw SecurityException("OEM screen is private") }
        assertManualOemFailure(context)
    }

    @Test fun vendorRuntimeFailuresRequireManualGuidanceWithoutCrashing() {
        val context = RecordingContext { throw IllegalStateException("Vendor launch failure") }
        assertManualOemFailure(context)
    }

    @Test fun missingFirstOemActivityTriesNextExistingCandidate() {
        val first = autostartTargets("oppo", "oppo").first()
        val context = RecordingContext {
            if (it.component!!.className == first.activityName) throw ActivityNotFoundException()
        }
        assertEquals(AutostartSettingsResult.OEM_SCREEN_OPENED, openAutostartSettings(context, "OPPO", "oppo"))
        assertEquals(2, context.attempted.size)
        assertEquals(autostartTargets("oppo", "oppo")[1].activityName, context.attempted.last().component!!.className)
    }

    @Test fun mixedLaunchFailuresStillTryOnlyRemainingOemCandidates() {
        val candidates = autostartTargets("oppo", "oppo")
        val context = RecordingContext {
            when (it.component!!.className) {
                candidates[0].activityName -> throw SecurityException()
                candidates[1].activityName -> throw ActivityNotFoundException()
                candidates[2].activityName -> throw IllegalStateException()
            }
        }
        assertEquals(AutostartSettingsResult.OEM_SCREEN_OPENED, openAutostartSettings(context, "oppo", "oppo"))
        assertEquals(candidates.map { it.activityName }, context.attempted.map { it.component!!.className })
        assertTrue(context.attempted.all { it.action == null })
    }

    @Test fun unknownPhoneShowsManualGuidanceWithoutAnyExternalLaunch() {
        val context = RecordingContext()
        assertEquals(AutostartSettingsResult.MANUAL_GUIDANCE_REQUIRED, openAutostartSettings(context, "Google", "google"))
        assertTrue(context.attempted.isEmpty())
    }

    @Test fun generalSettingsOpenOnlyThroughSeparateExplicitAction() {
        val context = RecordingContext()
        assertEquals(AutostartSettingsResult.MANUAL_GUIDANCE_REQUIRED, openAutostartSettings(context, "unknown", "unknown"))
        assertTrue(context.attempted.isEmpty())
        assertTrue(openSystemSettingsForAutostartGuide(context))
        val launched = context.attempted.single()
        assertEquals(Settings.ACTION_SETTINGS, launched.action)
        assertNull(launched.component)
        assertNull(launched.data)
        assertNull(launched.extras)
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun missingSystemSettingsReturnsFailureWithoutAnotherFallback() {
        assertSystemSettingsFailure(ActivityNotFoundException())
    }

    @Test fun privateSystemSettingsReturnsFailureWithoutAnotherFallback() {
        assertSystemSettingsFailure(SecurityException())
    }

    @Test fun systemSettingsRuntimeFailureIsReturnedWithoutCrashing() {
        assertSystemSettingsFailure(IllegalStateException())
    }

    @Test fun manufacturerAndBrandAliasesAreCaseInsensitive() {
        val aliases = mapOf(
            "oppo" to listOf("OPPO", "REALME", "ONEPLUS"),
            "xiaomi" to listOf("XIAOMI", "REDMI", "POCO"),
            "huawei" to listOf("HUAWEI", "HONOR"),
            "vivo" to listOf("VIVO"),
        )
        for ((manufacturer, brands) in aliases) {
            for (brand in brands) {
                assertEquals(autostartTargets(manufacturer, manufacturer), autostartTargets("other", brand))
                assertEquals(autostartTargets(manufacturer, manufacturer), autostartTargets(brand, "other"))
            }
        }
        assertTrue(autostartTargets("unknown", "unknown").isEmpty())
    }

    @Test fun manualGuideSourceContractKeepsSavedStateAndDismissalExplicit() {
        // Structural guard only; device/Compose rendering and recreation still need
        // runtime acceptance, especially at large font sizes on small screens.
        val source = Files.readString(Path.of("src/main/java/com/example/medicationreminder/ui/MedicationApp.kt"))
        val settings = source.substringAfter("private fun SettingsScreen(").substringBefore("private fun LanguageSetting(")
        assertTrue(settings.contains("showAutostartGuide by rememberSaveable"))
        assertTrue(settings.contains("autostartSystemSettingsFailed by rememberSaveable"))
        assertTrue(settings.contains("verticalScroll(rememberScrollState())"))
        assertTrue(settings.contains("onDismissRequest = onClose"))
        assertTrue(settings.contains("dismissOnBackPress = true"))
        assertTrue(settings.contains("R.string.autostart_guide_close"))
        assertTrue(settings.contains("R.string.autostart_open_system_settings"))
        assertTrue(settings.contains("R.string.autostart_system_settings_failed"))
        assertFalse(settings.contains("Toast"))
        val settingsAction = settings.substringAfter("onOpenSystemSettings = {").substringBefore("},")
        assertTrue(settingsAction.contains("!openSystemSettingsForAutostartGuide(context)"))
        assertFalse(settingsAction.contains("showAutostartGuide = false"))
    }

    private fun assertManualOemFailure(context: RecordingContext) {
        assertEquals(AutostartSettingsResult.MANUAL_GUIDANCE_REQUIRED, openAutostartSettings(context, "OPPO", "oppo"))
        assertEquals(autostartTargets("oppo", "oppo").map { it.activityName }, context.attempted.map { it.component!!.className })
        assertTrue(context.attempted.all { it.action == null && it.data == null })
        assertFalse(context.attempted.any { it.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS })
        assertFalse(context.attempted.any { it.action == Settings.ACTION_SETTINGS })
    }

    private fun assertSystemSettingsFailure(failure: RuntimeException) {
        val context = RecordingContext { throw failure }
        assertFalse(openSystemSettingsForAutostartGuide(context))
        assertEquals(Settings.ACTION_SETTINGS, context.attempted.single().action)
    }
}
