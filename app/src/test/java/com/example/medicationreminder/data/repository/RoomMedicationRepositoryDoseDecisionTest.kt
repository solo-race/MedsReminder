package com.example.medicationreminder.data.repository

import androidx.room.Room
import com.example.medicationreminder.data.local.DoseTimeEntity
import com.example.medicationreminder.data.local.MedicationDatabase
import com.example.medicationreminder.data.local.MedicationEntity
import com.example.medicationreminder.data.local.MedicationScheduleEntity
import com.example.medicationreminder.domain.model.DoseDecisionResult
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.TimeZoneMode
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class RoomMedicationRepositoryDoseDecisionTest {
    private lateinit var database: MedicationDatabase
    private lateinit var repository: RoomMedicationRepository

    private val medication = MedicationEntity(
        id = 10,
        name = "Medication",
        dosageText = "1 tablet",
        note = "",
        photoPath = null,
        enabled = true,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
    private val schedule = MedicationScheduleEntity(
        id = 20,
        medicationId = medication.id,
        weekdaysMask = 1 shl 0,
        timeZoneMode = TimeZoneMode.MANUAL,
        manualZoneId = "UTC",
    )
    private val doseTime = DoseTimeEntity(
        id = 30,
        scheduleId = schedule.id,
        minuteOfDay = 8 * 60,
        enabled = true,
    )
    private val occurrence = DoseOccurrence(
        medicationId = medication.id,
        doseTimeId = doseTime.id,
        scheduledFor = Instant.parse("2026-09-07T08:00:00Z"),
        zoneId = ZoneId.of("UTC"),
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            MedicationDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repository = RoomMedicationRepository(database)
        runBlocking { insertActiveFixture() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun duplicateDecisionReturnsAlreadyDecidedAndKeepsFirstStatus() = runBlocking {
        val first = repository.decideDose(occurrence, DoseStatus.TAKEN)
        val second = repository.decideDose(occurrence, DoseStatus.SKIPPED)

        assertEquals(DoseDecisionResult.Recorded(DoseStatus.TAKEN), first)
        assertEquals(DoseDecisionResult.AlreadyDecided(DoseStatus.TAKEN), second)
        assertEquals(DoseStatus.TAKEN, persistedDecisionStatus())
    }

    @Test
    fun concurrentTakenAndSkippedProduceOneFirstWriteWinsDecision() = runBlocking {
        val start = CompletableDeferred<Unit>()
        val taken = async(Dispatchers.Default) {
            start.await()
            repository.decideDose(occurrence, DoseStatus.TAKEN)
        }
        val skipped = async(Dispatchers.Default) {
            start.await()
            repository.decideDose(occurrence, DoseStatus.SKIPPED)
        }

        start.complete(Unit)
        val results = listOf(taken.await(), skipped.await())
        val recorded = results.filterIsInstance<DoseDecisionResult.Recorded>()
        val alreadyDecided = results.filterIsInstance<DoseDecisionResult.AlreadyDecided>()

        assertEquals(1, recorded.size)
        assertEquals(1, alreadyDecided.size)
        assertEquals(recorded.single().status, alreadyDecided.single().status)
        assertEquals(recorded.single().status, persistedDecisionStatus())
    }

    @Test
    fun decisionRemainsOnSameLogicalDayAcrossInternationalDateLineZoneChange() = runBlocking {
        val honolulu = ZoneId.of("Pacific/Honolulu")
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        database.scheduleDao().update(schedule.copy(manualZoneId = honolulu.id))
        val originalOccurrence = occurrence.copy(
            scheduledFor = Instant.parse("2026-09-07T18:00:00Z"),
            zoneId = honolulu,
        )

        assertEquals(
            DoseDecisionResult.Recorded(DoseStatus.TAKEN),
            repository.decideDose(originalOccurrence, DoseStatus.TAKEN),
        )

        database.scheduleDao().update(schedule.copy(manualZoneId = kiritimati.id))
        val rebuiltOccurrence = occurrence.copy(
            scheduledFor = Instant.parse("2026-09-06T18:00:00Z"),
            zoneId = kiritimati,
        )

        assertTrue(
            repository.hasDoseDecisionOnLocalDay(
                rebuiltOccurrence.doseTimeId,
                rebuiltOccurrence.scheduledFor,
                rebuiltOccurrence.zoneId,
            ),
        )
        assertEquals(
            DoseDecisionResult.AlreadyDecided(DoseStatus.TAKEN),
            repository.decideDose(rebuiltOccurrence, DoseStatus.SKIPPED),
        )
    }

    @Test
    fun disabledSlotReturnsStaleAndWritesNothing() = runBlocking {
        database.doseTimeDao().updateAll(listOf(doseTime.copy(enabled = false)))

        val result = repository.decideDose(occurrence, DoseStatus.TAKEN)

        assertTrue(result === DoseDecisionResult.StaleOccurrence)
        assertNull(persistedDecisionStatus())
    }

    @Test
    fun deletedMedicationReturnsStaleAndWritesNothing() = runBlocking {
        database.medicationDao().deleteById(medication.id)

        val result = repository.decideDose(occurrence, DoseStatus.SKIPPED)

        assertTrue(result === DoseDecisionResult.StaleOccurrence)
        assertNull(persistedDecisionStatus())
    }

    private suspend fun insertActiveFixture() {
        database.medicationDao().insert(medication)
        database.scheduleDao().insert(schedule)
        database.doseTimeDao().insertAll(listOf(doseTime))
    }

    private suspend fun persistedDecisionStatus(): DoseStatus? {
        val scheduledLocalEpochDay = occurrence.scheduledFor
            .atZone(occurrence.zoneId)
            .toLocalDate()
            .toEpochDay()
        return database.doseEventDao()
            .firstForLogicalDay(occurrence.doseTimeId, scheduledLocalEpochDay)
            ?.status
    }
}
