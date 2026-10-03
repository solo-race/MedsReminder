package com.example.medicationreminder.reminders

import android.app.AlarmManager
import android.content.Context
import com.example.medicationreminder.data.repository.MedicationRepository
import com.example.medicationreminder.data.repository.SaveMedicationResult
import com.example.medicationreminder.domain.model.DoseEvent
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.MedicationDraft
import com.example.medicationreminder.domain.model.MedicationPlan
import com.example.medicationreminder.domain.model.MedicationSchedule
import com.example.medicationreminder.domain.model.ScheduledDose
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderSchedulerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val dose = ScheduledDose(
        medicationId = 10,
        medicationName = "Medication",
        dosageText = "1 tablet",
        scheduleId = 20,
        doseTimeId = 30,
        time = LocalTime.of(8, 0),
        weekdays = DayOfWeek.entries.toSet(),
        zoneId = ZoneId.of("UTC"),
        medicationAlias = null,
    )

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun exactAlarmPermissionUnavailableDoesNotCallSchedulingApiOrThrow() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val scheduler = ReminderScheduler(context, repository(listOf(dose)))

        val result = scheduler.scheduleAfter(dose, Instant.parse("2026-09-07T07:30:00Z"))

        assertEquals(ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION, result)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun scheduleAllReportsWaitingWhenExactAlarmPermissionIsUnavailable() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val scheduler = ReminderScheduler(context, repository(listOf(dose)))

        assertEquals(
            ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION,
            scheduler.scheduleAll(),
        )
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun grantedPermissionRebuildsOneStableAlarmForRepeatedScheduleAllCalls() = runBlocking {
        val scheduler = ReminderScheduler(context, repository(listOf(dose)))
        val boundary = Instant.parse("2026-09-07T07:30:00Z")

        assertEquals(ScheduleResult.SCHEDULED, scheduler.scheduleAfter(dose, boundary))
        assertEquals(ScheduleResult.SCHEDULED, scheduler.scheduleAfter(dose, boundary))

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun grantedPermissionAllowsStartupStyleScheduleAllToRebuildAnAlarm() = runBlocking {
        val scheduler = ReminderScheduler(context, repository(listOf(dose)))

        assertEquals(ScheduleResult.SCHEDULED, scheduler.scheduleAll())
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun reminderPendingIntentCarriesCompleteOccurrenceIdentity() {
        val scheduler = ReminderScheduler(context, repository())
        val occurrence = DoseOccurrence(
            medicationId = 101,
            doseTimeId = 202,
            scheduledFor = Instant.parse("2026-09-07T08:00:00Z"),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        val pendingIntent = scheduler.reminderPendingIntent(occurrence)
        val savedIntent = shadowOf(pendingIntent).savedIntent

        assertEquals("${ReminderReceiver.ACTION_REMIND}.${occurrence.doseTimeId}", savedIntent.action)
        assertEquals(occurrence.medicationId, savedIntent.getLongExtra(ReminderReceiver.EXTRA_MEDICATION_ID, -1))
        assertEquals(occurrence.doseTimeId, savedIntent.getLongExtra(ReminderReceiver.EXTRA_DOSE_TIME_ID, -1))
        assertEquals(occurrence.scheduledFor.toEpochMilli(), savedIntent.getLongExtra(ReminderReceiver.EXTRA_SCHEDULED_FOR, -1))
        assertEquals(occurrence.zoneId.id, savedIntent.getStringExtra(ReminderReceiver.EXTRA_ZONE_ID))
    }

    @Test
    fun differentDoseSlotsUseDifferentStablePendingIntentIdentities() {
        val scheduler = ReminderScheduler(context, repository())
        val first = DoseOccurrence(101, 202, Instant.parse("2026-09-07T08:00:00Z"), ZoneId.of("UTC"))
        val second = first.copy(doseTimeId = 203)

        val firstPendingIntent = scheduler.reminderPendingIntent(first)
        val secondPendingIntent = scheduler.reminderPendingIntent(second)

        assertFalse(firstPendingIntent == secondPendingIntent)
        assertFalse(
            shadowOf(firstPendingIntent).requestCode == shadowOf(secondPendingIntent).requestCode,
        )
    }

    @Test
    fun exactAlarmPermissionEventOnlyReschedulesAfterPermissionIsConfirmed() {
        val action = AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED

        assertTrue(systemEventRequiresReschedule(action))
        assertFalse(systemEventCanReschedule(action, exactAlarmsAllowed = false))
        assertTrue(systemEventCanReschedule(action, exactAlarmsAllowed = true))
    }

    private fun repository(doses: List<ScheduledDose> = emptyList()): MedicationRepository =
        object : MedicationRepository {
            override fun observeMedicationPlans(): Flow<List<MedicationPlan>> = emptyFlow()
            override fun observeDoseHistory(): Flow<List<DoseEvent>> = emptyFlow()
            override suspend fun saveMedication(draft: MedicationDraft): SaveMedicationResult = error("unused")
            override suspend fun deleteMedication(medicationId: Long): String? = error("unused")
            override suspend fun activeScheduledDoses(): List<ScheduledDose> = doses
            override suspend fun hasDoseDecisionOnLocalDay(
                doseTimeId: Long,
                scheduledFor: Instant,
                zoneId: ZoneId,
            ): Boolean = false
            override suspend fun setScheduleToDeviceTime(scheduleId: Long) = error("unused")
            override suspend fun manualSchedules(): List<MedicationSchedule> = emptyList()
        }
}
