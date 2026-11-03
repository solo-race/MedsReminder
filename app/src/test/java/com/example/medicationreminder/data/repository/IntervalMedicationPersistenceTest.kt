package com.example.medicationreminder.data.repository

import androidx.room.Room
import com.example.medicationreminder.data.local.MedicationDatabase
import com.example.medicationreminder.data.local.DoseEventEntity
import com.example.medicationreminder.domain.model.DoseDecisionResult
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.MedicationDraft
import com.example.medicationreminder.domain.model.ReminderStage
import com.example.medicationreminder.domain.model.ScheduleMode
import com.example.medicationreminder.domain.model.TimeZoneMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IntervalMedicationPersistenceTest {
    private lateinit var database: MedicationDatabase
    private lateinit var repository: RoomMedicationRepository
    private val anchor = Instant.parse("2026-11-03T08:00:00Z")

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MedicationDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = RoomMedicationRepository(database)
    }

    @After fun tearDown() { database.close() }

    @Test fun sameDayIntervalDecisionsAreIndependentAndCaptureDoseSnapshots() = runBlocking {
        val id = repository.saveMedication(draft()).medicationId
        val dose = repository.activeScheduledDoses().single()
        assertEquals(ScheduleMode.INTERVAL, dose.scheduleMode)
        assertEquals(720L, dose.intervalMinutes)
        assertEquals("0.5", dose.doseAmount)
        val first = dose.occurrenceAt(anchor)
        val second = dose.occurrenceAt(anchor.plusSeconds(12 * 3600))
        assertTrue(repository.registerPendingReminder(first, ReminderStage.PRE))
        assertTrue(repository.registerPendingReminder(second, ReminderStage.PRE))
        assertEquals(DoseDecisionResult.Recorded(DoseStatus.TAKEN), repository.decideDose(first, DoseStatus.TAKEN))
        assertTrue(repository.hasDoseDecision(first))
        assertFalse(repository.hasDoseDecision(second))
        assertEquals(listOf(second), repository.pendingReminders(id).map { it.occurrence })
        repository.saveMedication(draft().copy(id = id, dosageText = "new dose", doseAmount = "1"))
        assertEquals(DoseDecisionResult.Recorded(DoseStatus.SKIPPED), repository.decideDose(second, DoseStatus.SKIPPED))
        assertEquals(DoseDecisionResult.AlreadyDecided(DoseStatus.TAKEN), repository.decideDose(first, DoseStatus.SKIPPED))
        val history = repository.observeDoseHistory().first().sortedBy { it.scheduledFor }
        assertEquals(2, history.size)
        assertEquals(listOf("0.5", "1"), history.map { it.doseAmountSnapshot })
        assertEquals(listOf("original dose", "new dose"), history.map { it.dosageTextSnapshot })
        assertTrue(history.all { it.medicationId == id && it.doseUnitSnapshot == "tablet" })
        assertTrue(repository.pendingReminders().isEmpty())
    }

    @Test fun metadataKeepsSlotButIntervalAnchorAndModeEditsReplaceIt() = runBlocking {
        val id = repository.saveMedication(draft()).medicationId
        val original = repository.activeScheduledDoses().single()
        val pending = original.occurrenceAt(anchor)
        repository.registerPendingReminder(pending, ReminderStage.PRE)
        repository.saveMedication(draft().copy(id = id, name = "Renamed", note = "edited", manualZoneId = "Asia/Tokyo"))
        assertEquals(original.doseTimeId, repository.activeScheduledDoses().single().doseTimeId)
        assertEquals(pending, repository.pendingReminders().single().occurrence)
        repository.decideDose(pending, DoseStatus.TAKEN)
        repository.registerPendingReminder(original.occurrenceAt(anchor.plusSeconds(12 * 3600)), ReminderStage.DUE)
        repository.saveMedication(draft().copy(id = id, intervalMinutes = 600))
        val changedInterval = repository.activeScheduledDoses().single()
        assertNotEquals(original.doseTimeId, changedInterval.doseTimeId)
        assertTrue(repository.pendingReminders().isEmpty())
        assertEquals(1, repository.observeDoseHistory().first().size)
        repository.saveMedication(draft().copy(id = id, intervalMinutes = 600, anchorEpochMillis = anchor.plusSeconds(60).toEpochMilli()))
        val changedAnchor = repository.activeScheduledDoses().single()
        assertNotEquals(changedInterval.doseTimeId, changedAnchor.doseTimeId)
        repository.saveMedication(draft().copy(id = id, scheduleMode = ScheduleMode.FIXED_TIMES,
            times = listOf(LocalTime.of(8, 0)), weekdays = DayOfWeek.entries.toSet()))
        val fixed = repository.activeScheduledDoses().single()
        assertNotEquals(changedAnchor.doseTimeId, fixed.doseTimeId)
        assertNull(fixed.anchorEpochMillis)
        assertNull(fixed.intervalMinutes)
    }

    @Test fun pendingStagesNeverRegressAndInvalidOrDecidedOccurrencesCannotRegister() = runBlocking {
        repository.saveMedication(draft())
        val dose = repository.activeScheduledDoses().single()
        val occurrence = dose.occurrenceAt(anchor)
        assertTrue(repository.registerPendingReminder(occurrence, ReminderStage.PRE))
        assertFalse(repository.registerPendingReminder(occurrence, ReminderStage.PRE))
        assertTrue(repository.registerPendingReminder(occurrence, ReminderStage.DUE))
        assertFalse(repository.registerPendingReminder(occurrence, ReminderStage.PRE))
        assertEquals(ReminderStage.DUE, repository.pendingReminders().single().stage)
        val offGrid = occurrence.copy(scheduledFor = anchor.plusSeconds(60))
        assertFalse(repository.registerPendingReminder(offGrid, ReminderStage.DUE))
        assertEquals(DoseDecisionResult.StaleOccurrence, repository.decideDose(offGrid, DoseStatus.TAKEN))
        assertFalse(repository.registerPendingReminder(occurrence.copy(medicationId = 999), ReminderStage.PRE))
        repository.decideDose(occurrence, DoseStatus.TAKEN)
        assertFalse(repository.registerPendingReminder(occurrence, ReminderStage.DUE))
        assertTrue(repository.pendingReminders().isEmpty())
    }

    @Test fun fixedPendingUsesOriginalZoneAfterTravelAndDoesNotChangeLogicalDayPolicy() = runBlocking {
        val id = repository.saveMedication(draft().copy(scheduleMode = ScheduleMode.FIXED_TIMES,
            times = listOf(LocalTime.of(8, 0)), weekdays = DayOfWeek.entries.toSet(), manualZoneId = "Pacific/Honolulu")).medicationId
        val original = repository.activeScheduledDoses().single()
        val instant = Instant.parse("2026-11-03T18:00:00Z")
        val pending = original.occurrenceAt(instant)
        assertTrue(repository.registerPendingReminder(pending, ReminderStage.DUE))
        val schedule = database.scheduleDao().getForMedication(id)!!
        database.scheduleDao().update(schedule.copy(manualZoneId = "Pacific/Kiritimati"))
        assertEquals(DoseDecisionResult.Recorded(DoseStatus.TAKEN), repository.decideDose(pending, DoseStatus.TAKEN))
        val rebuilt = repository.activeScheduledDoses().single().occurrenceAt(Instant.parse("2026-11-02T18:00:00Z"))
        assertTrue(repository.hasDoseDecision(rebuilt))
        assertEquals(DoseDecisionResult.AlreadyDecided(DoseStatus.TAKEN), repository.decideDose(rebuilt, DoseStatus.SKIPPED))
        assertTrue(repository.pendingReminders().isEmpty())
    }

    @Test fun disableDeleteAndSlotRemovalClearOnlyTheirPending() = runBlocking {
        val id = repository.saveMedication(draft()).medicationId
        val otherId = repository.saveMedication(draft().copy(name = "Other")).medicationId
        val doses = repository.activeScheduledDoses().associateBy { it.medicationId }
        for (dose in doses.values) repository.registerPendingReminder(dose.occurrenceAt(anchor), ReminderStage.DUE)
        repository.saveMedication(draft().copy(id = id, enabled = false))
        assertEquals(listOf(otherId), repository.pendingReminders().map { it.occurrence.medicationId })
        assertEquals(DoseDecisionResult.StaleOccurrence, repository.decideDose(doses.getValue(id).occurrenceAt(anchor), DoseStatus.TAKEN))
        repository.clearPendingReminders(doses.getValue(otherId).doseTimeId)
        assertTrue(repository.pendingReminders().isEmpty())
        repository.registerPendingReminder(doses.getValue(otherId).occurrenceAt(anchor), ReminderStage.DUE)
        repository.deleteMedication(otherId)
        assertTrue(repository.pendingReminders().isEmpty())
    }

    @Test fun failedValidationRollsBackAndLegacyDosesCanRemainUnknown() = runBlocking {
        val id = repository.saveMedication(draft().copy(doseAmount = null, doseUnit = null)).medicationId
        val dose = repository.activeScheduledDoses().single()
        val occurrence = dose.occurrenceAt(anchor)
        repository.registerPendingReminder(occurrence, ReminderStage.PRE)
        for (invalid in listOf(draft().copy(intervalMinutes = 0), draft().copy(intervalMinutes = Long.MAX_VALUE),
                draft().copy(doseAmount = "0"), draft().copy(doseUnit = null))) {
            assertTrue(runCatching { repository.saveMedication(invalid.copy(id = id)) }.isFailure)
        }
        assertEquals(dose.doseTimeId, repository.activeScheduledDoses().single().doseTimeId)
        assertEquals(1, repository.pendingReminders().size)
        repository.decideDose(occurrence, DoseStatus.TAKEN)
        assertNull(repository.observeDoseHistory().first().single().doseAmountSnapshot)
    }

    @Test fun oldHistoryDoesNotAcquireCurrentMedicationDoseAfterEditing() = runBlocking {
        val id = repository.saveMedication(draft()).medicationId
        val dose = repository.activeScheduledDoses().single()
        database.doseEventDao().insert(DoseEventEntity(medicationId = id, doseTimeId = dose.doseTimeId,
            scheduledForEpochMillis = anchor.toEpochMilli(),
            scheduledLocalEpochDay = anchor.atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay(),
            status = DoseStatus.TAKEN, actionedAtEpochMillis = System.currentTimeMillis()))
        val before = repository.observeDoseHistory().first().single()
        assertEquals("", before.dosageText)
        assertNull(before.dosageTextSnapshot)
        assertNull(before.doseAmountSnapshot)
        repository.saveMedication(draft().copy(id = id, dosageText = "99 tablets now", doseAmount = "99"))
        val after = repository.observeDoseHistory().first().single()
        assertEquals(before, after)
    }

    private fun draft() = MedicationDraft(name = "Interval", dosageText = "original dose", note = "",
        enabled = true, weekdays = emptySet(), timeZoneMode = TimeZoneMode.MANUAL, manualZoneId = "UTC",
        times = emptyList(), scheduleMode = ScheduleMode.INTERVAL, intervalMinutes = 720,
        anchorEpochMillis = anchor.toEpochMilli(), doseAmount = "0.50", doseUnit = "tablet")
}
