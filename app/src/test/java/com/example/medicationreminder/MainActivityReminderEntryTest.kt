package com.example.medicationreminder

import android.content.Intent
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.ui.MedicationViewModel
import com.example.medicationreminder.ui.ReminderEntry
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Runtime coverage for the reminder entry contract: a reminder must bind its explicit occurrence
 * whether the Activity is created for it (cold) or already running (warm delivery), and Activity
 * recreation must neither replay an opened delivery nor lose the bound occurrence. The
 * 2026-09-13 device smoke found the already-running case broken; the previous coverage asserted
 * source text instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityReminderEntryTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val scheduledFor = Instant.parse("2026-09-13T13:25:00Z")

    @Test
    fun coldLaunchBindsTheReminderOccurrence() {
        val controller = launchActivity(reminderIntent(medicationId = 4, doseTimeId = 13))
        val viewModel = viewModelOf(controller)
        idleFrames()

        assertEquals(occurrence(medicationId = 4, doseTimeId = 13), viewModel.reminderOccurrence.value)
        assertNull(viewModel.reminderEntry.value)
    }

    @Test
    fun warmDeliveryBindsTheSameOccurrenceAsAColdLaunch() {
        val controller = launchActivity(null)
        val viewModel = viewModelOf(controller)
        idleFrames()
        assertNull(viewModel.reminderOccurrence.value)

        controller.newIntent(reminderIntent(medicationId = 4, doseTimeId = 13))
        idleFrames()

        assertEquals(occurrence(medicationId = 4, doseTimeId = 13), viewModel.reminderOccurrence.value)
        assertNull(viewModel.reminderEntry.value)
    }

    @Test
    fun secondReminderRebindsDetailToItsOwnOccurrence() {
        val controller = launchActivity(reminderIntent(medicationId = 4, doseTimeId = 13))
        val viewModel = viewModelOf(controller)
        idleFrames()

        controller.newIntent(reminderIntent(medicationId = 7, doseTimeId = 21))
        idleFrames()

        assertEquals(occurrence(medicationId = 7, doseTimeId = 21), viewModel.reminderOccurrence.value)
    }

    @Test
    fun recreatingTheActivityKeepsTheBoundOccurrenceWithoutReplayingTheDelivery() {
        val controller = launchActivity(reminderIntent(medicationId = 4, doseTimeId = 13))
        idleFrames()

        controller.recreate()
        val viewModel = viewModelOf(controller)
        idleFrames()

        assertEquals(occurrence(medicationId = 4, doseTimeId = 13), viewModel.reminderOccurrence.value)
        assertNull(viewModel.reminderEntry.value)
    }

    @Test
    fun reappliedLaunchIntentIsNotDeliveredTwice() {
        val viewModel = viewModel()
        val entry = ReminderEntry(medicationId = 4, occurrence = occurrence(medicationId = 4, doseTimeId = 13))

        viewModel.onReminderIntent(entry, reapplied = false)
        viewModel.reminderEntryOpened()
        viewModel.onReminderIntent(entry, reapplied = true)

        assertNull(viewModel.reminderEntry.value)
        assertEquals(entry.occurrence, viewModel.reminderOccurrence.value)
    }

    @Test
    fun openingRetiresTheDeliveryTheUiNavigatesWith() {
        val viewModel = viewModel()
        val first = ReminderEntry(medicationId = 4, occurrence = occurrence(medicationId = 4, doseTimeId = 13))
        val second = ReminderEntry(medicationId = 7, occurrence = occurrence(medicationId = 7, doseTimeId = 21))

        viewModel.onReminderIntent(first, reapplied = false)
        viewModel.onReminderIntent(second, reapplied = false)

        assertEquals(second, viewModel.reminderEntryOpened())
        assertEquals(second.occurrence, viewModel.reminderOccurrence.value)
        assertNull(viewModel.reminderEntry.value)
    }

    @Test
    fun repeatedReminderDeliveryOpensAgain() {
        val viewModel = viewModel()
        val entry = ReminderEntry(medicationId = 4, occurrence = occurrence(medicationId = 4, doseTimeId = 13))

        viewModel.onReminderIntent(entry, reapplied = false)
        viewModel.reminderEntryOpened()
        viewModel.onReminderIntent(entry, reapplied = false)

        assertEquals(entry, viewModel.reminderEntry.value)
    }

    @Test
    fun launchWithoutReminderExtrasLeavesDetailOnTheScheduleDerivedOccurrence() {
        val controller = launchActivity(null)
        val viewModel = viewModelOf(controller)
        idleFrames()

        assertNull(viewModel.reminderEntry.value)
        assertNull(viewModel.reminderOccurrence.value)
    }

    private fun launchActivity(intent: Intent?): ActivityController<MainActivity> {
        val controller = if (intent == null) {
            Robolectric.buildActivity(MainActivity::class.java)
        } else {
            Robolectric.buildActivity(MainActivity::class.java, intent)
        }
        return controller.setup()
    }

    private fun reminderIntent(medicationId: Long, doseTimeId: Long): Intent =
        Intent(RuntimeEnvironment.getApplication(), MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_MEDICATION_ID, medicationId)
            .putExtra(MainActivity.EXTRA_DOSE_TIME_ID, doseTimeId)
            .putExtra(MainActivity.EXTRA_SCHEDULED_FOR, scheduledFor.toEpochMilli())
            .putExtra(MainActivity.EXTRA_ZONE_ID, zone.id)

    private fun occurrence(medicationId: Long, doseTimeId: Long) =
        DoseOccurrence(medicationId, doseTimeId, scheduledFor, zone)

    private fun viewModelOf(controller: ActivityController<MainActivity>): MedicationViewModel =
        ViewModelProvider(controller.get())[MedicationViewModel::class.java]

    private fun viewModel(): MedicationViewModel =
        MedicationViewModel(RuntimeEnvironment.getApplication())

    /** Lets pending recommendations and the Compose frame-driven effect run to completion. */
    private fun idleFrames() {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(4) { looper.idleFor(Duration.ofMillis(16)) }
    }
}
