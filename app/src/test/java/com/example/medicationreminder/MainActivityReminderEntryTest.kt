package com.example.medicationreminder

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.ui.MedicationViewModel
import com.example.medicationreminder.ui.PendingEarlyDecision
import com.example.medicationreminder.ui.ReminderEntry
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val activities = mutableListOf<ActivityController<MainActivity>>()
    private val zone = ZoneId.of("Asia/Shanghai")
    private val scheduledFor = Instant.parse("2026-09-13T13:25:00Z")

    @After
    fun closeActivities() {
        activities.asReversed().forEach { it.close() }
    }

    @Test
    fun coldLaunchBindsTheReminderOccurrence() {
        val controller = launchActivity(reminderIntent(medicationId = 4, doseTimeId = 13))
        val viewModel = viewModelOf(controller)
        awaitOpened(viewModel, occurrence(medicationId = 4, doseTimeId = 13))

        assertEquals("Pending entry=${viewModel.reminderEntry.value}; lifecycle=${controller.get().lifecycle.currentState}",
            occurrence(medicationId = 4, doseTimeId = 13), viewModel.reminderOccurrence.value)
        assertNull(viewModel.reminderEntry.value)
    }

    @Test
    fun warmDeliveryBindsTheSameOccurrenceAsAColdLaunch() {
        val controller = launchActivity(null)
        val viewModel = viewModelOf(controller)
        idleFrames()
        assertNull(viewModel.reminderOccurrence.value)

        controller.newIntent(reminderIntent(medicationId = 4, doseTimeId = 13))
        awaitOpened(viewModel, occurrence(medicationId = 4, doseTimeId = 13))

        assertEquals("Pending entry=${viewModel.reminderEntry.value}; lifecycle=${controller.get().lifecycle.currentState}",
            occurrence(medicationId = 4, doseTimeId = 13), viewModel.reminderOccurrence.value)
        assertNull(viewModel.reminderEntry.value)
    }

    @Test
    fun secondReminderRebindsDetailToItsOwnOccurrence() {
        val controller = launchActivity(reminderIntent(medicationId = 4, doseTimeId = 13))
        val viewModel = viewModelOf(controller)
        awaitOpened(viewModel, occurrence(medicationId = 4, doseTimeId = 13))

        controller.newIntent(reminderIntent(medicationId = 7, doseTimeId = 21))
        awaitOpened(viewModel, occurrence(medicationId = 7, doseTimeId = 21))

        assertEquals(occurrence(medicationId = 7, doseTimeId = 21), viewModel.reminderOccurrence.value)
    }

    @Test
    fun recreatingTheActivityKeepsTheBoundOccurrenceWithoutReplayingTheDelivery() {
        val controller = launchActivity(reminderIntent(medicationId = 4, doseTimeId = 13))
        awaitOpened(viewModelOf(controller), occurrence(medicationId = 4, doseTimeId = 13))

        controller.recreate()
        val viewModel = viewModelOf(controller)
        awaitOpened(viewModel, occurrence(medicationId = 4, doseTimeId = 13))

        assertEquals("Pending entry=${viewModel.reminderEntry.value}; lifecycle=${controller.get().lifecycle.currentState}",
            occurrence(medicationId = 4, doseTimeId = 13), viewModel.reminderOccurrence.value)
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
    fun reminderEntryDeliversWarmIntentsToTheRunningInstance() {
        // Device-verified 2026-09-14: with the default launch mode, a NEW_TASK intent aimed at the
        // root activity of an existing task only brings the task forward on PLB110/ColorOS and no
        // onNewIntent is delivered, so the notification tap silently loses its occurrence.
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)

        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, info.launchMode)
    }

    @Test
    fun launchWithoutReminderExtrasLeavesDetailOnTheScheduleDerivedOccurrence() {
        val controller = launchActivity(null)
        val viewModel = viewModelOf(controller)
        idleFrames()

        assertNull(viewModel.reminderEntry.value)
        assertNull(viewModel.reminderOccurrence.value)
    }

    @Test
    fun preAlarmActionCarriesTheRequestedDecisionIntoDetail() {
        val controller = launchActivity(
            reminderIntent(medicationId = 4, doseTimeId = 13)
                .putExtra(MainActivity.EXTRA_REQUESTED_STATUS, DoseStatus.TAKEN.name),
        )
        val viewModel = viewModelOf(controller)
        awaitOpened(viewModel, occurrence(medicationId = 4, doseTimeId = 13))

        assertEquals(
            PendingEarlyDecision(occurrence(medicationId = 4, doseTimeId = 13), DoseStatus.TAKEN),
            viewModel.pendingEarlyDecision.value,
        )
    }

    @Test
    fun unknownRequestedStatusDoesNotOpenConfirmation() {
        val controller = launchActivity(
            reminderIntent(medicationId = 4, doseTimeId = 13)
                .putExtra(MainActivity.EXTRA_REQUESTED_STATUS, "NOT_A_STATUS"),
        )
        val viewModel = viewModelOf(controller)
        awaitOpened(viewModel, occurrence(medicationId = 4, doseTimeId = 13))

        assertNull(viewModel.pendingEarlyDecision.value)
    }

    private fun launchActivity(intent: Intent?): ActivityController<MainActivity> {
        val controller = if (intent == null) {
            Robolectric.buildActivity(MainActivity::class.java)
        } else {
            Robolectric.buildActivity(MainActivity::class.java, intent)
        }
        activities += controller
        return controller.setup().windowFocusChanged(true)
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

    /** Synchronize Compose frames while allowing asynchronous preference reads to finish. */
    private fun awaitOpened(viewModel: MedicationViewModel, expected: DoseOccurrence) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.reminderOccurrence.value == expected && viewModel.reminderEntry.value == null
        }
        assertEquals(expected, viewModel.reminderOccurrence.value)
        assertNull(viewModel.reminderEntry.value)
    }

    /** Lets pending recommendations and the Compose frame-driven effect run to completion. */
    private fun idleFrames() {
        composeRule.waitForIdle()
    }
}
