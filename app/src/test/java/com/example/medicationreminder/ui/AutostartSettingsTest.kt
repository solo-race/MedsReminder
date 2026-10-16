package com.example.medicationreminder.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AutostartSettingsTest {
    private class RecordingContext(val accept: (Intent) -> Boolean) : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val attempted = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            attempted += intent
            if (intent.component != null && !accept(intent)) throw SecurityException("OEM screen is private")
            if (!accept(intent)) throw ActivityNotFoundException()
        }
    }

    @Test fun oemScreenSuccessIsNavigationOnly() {
        val context = RecordingContext { true }
        assertEquals(AutostartSettingsResult.OEM_SCREEN, openAutostartSettings(context, "OPPO", "oppo"))
        assertEquals(1, context.attempted.size)
        assertEquals("com.coloros.safecenter", context.attempted.single().component!!.packageName)
        assertTrue(context.attempted.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun privateOemActivitiesFallBackToThisAppsDetails() {
        val context = RecordingContext { it.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS }
        assertEquals(AutostartSettingsResult.APP_DETAILS, openAutostartSettings(context, "OPPO", "oppo"))
        assertEquals("package:${context.packageName}", context.attempted.last().data.toString())
        assertTrue(context.attempted.size > 1)
    }

    @Test fun unknownPhoneUsesAppDetailsWithoutGuessingAnOem() {
        val context = RecordingContext { true }
        assertEquals(AutostartSettingsResult.APP_DETAILS, openAutostartSettings(context, "Google", "google"))
        assertNull(context.attempted.single().component)
    }

    @Test fun systemSettingsAreLastFallbackAndTotalFailureIsReturned() {
        val fallback = RecordingContext { it.action == Settings.ACTION_SETTINGS }
        assertEquals(AutostartSettingsResult.SYSTEM_SETTINGS, openAutostartSettings(fallback, "unknown", "unknown"))
        val blocked = RecordingContext { false }
        assertEquals(AutostartSettingsResult.UNAVAILABLE, openAutostartSettings(blocked, "unknown", "unknown"))
    }

    @Test fun manufacturerAndBrandAliasesAreCaseInsensitive() {
        assertEquals(autostartTargets("xiaomi", "xiaomi"), autostartTargets("other", "POCO"))
        assertEquals(autostartTargets("oppo", "oppo"), autostartTargets("realme", "realme"))
        assertTrue(autostartTargets("unknown", "unknown").isEmpty())
    }
}
