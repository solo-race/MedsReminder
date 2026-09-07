package com.example.medicationreminder.reminders

import androidx.room.Room
import com.example.medicationreminder.data.local.DoseTimeEntity
import com.example.medicationreminder.data.local.MedicationDatabase
import com.example.medicationreminder.data.local.MedicationEntity
import com.example.medicationreminder.data.local.MedicationScheduleEntity
import com.example.medicationreminder.data.repository.RoomMedicationRepository
import com.example.medicationreminder.domain.model.DoseDecisionResult
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.ScheduledDose
import com.example.medicationreminder.domain.model.TimeZoneMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistedDecisionRescheduleTest {
    private val databaseName = "phase6-persisted-decision.db"
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun earlyDecisionSurvivesDatabaseReopenAndRebuildSkipsOriginalOccurrence() = runBlocking {
        val zone = ZoneId.of("UTC")
        val scheduledFor = Instant.parse("2026-09-07T08:00:00Z")
        val occurrence = DoseOccurrence(
            medicationId = 10,
            doseTimeId = 30,
            scheduledFor = scheduledFor,
            zoneId = zone,
        )

        val database = openDatabase()
        try {
            database.medicationDao().insert(
                MedicationEntity(
                    id = 10,
                    name = "Medication",
                    dosageText = "1 tablet",
                    note = "before restart",
                    photoPath = null,
                    enabled = true,
                    createdAtEpochMillis = 0,
                    updatedAtEpochMillis = 0,
                ),
            )
            database.scheduleDao().insert(
                MedicationScheduleEntity(
                    id = 20,
                    medicationId = 10,
                    weekdaysMask = DayOfWeek.entries.fold(0) { mask, day ->
                        mask or (1 shl (day.value - 1))
                    },
                    timeZoneMode = TimeZoneMode.MANUAL,
                    manualZoneId = zone.id,
                ),
            )
            database.doseTimeDao().insertAll(
                listOf(
                    DoseTimeEntity(
                        id = 30,
                        scheduleId = 20,
                        minuteOfDay = 8 * 60,
                        enabled = true,
                    ),
                ),
            )

            val repository = RoomMedicationRepository(database)
            assertEquals(
                DoseDecisionResult.Recorded(DoseStatus.TAKEN),
                repository.decideDose(occurrence, DoseStatus.TAKEN),
            )
        } finally {
            database.close()
        }

        val reopened = openDatabase()
        try {
            val repository = RoomMedicationRepository(reopened)
            reopened.medicationDao().update(
                MedicationEntity(
                    id = 10,
                    name = "Renamed medication",
                    dosageText = "1 tablet",
                    note = "metadata changed after restart",
                    photoPath = null,
                    enabled = true,
                    createdAtEpochMillis = 0,
                    updatedAtEpochMillis = 1,
                ),
            )

            val dose = ScheduledDose(
                medicationId = 10,
                medicationName = "Renamed medication",
                dosageText = "1 tablet",
                scheduleId = 20,
                doseTimeId = 30,
                time = LocalTime.of(8, 0),
                weekdays = DayOfWeek.entries.toSet(),
                zoneId = zone,
                medicationAlias = null,
            )
            val rebuilt = nextUndecidedOccurrence(
                dose = dose,
                after = Instant.parse("2026-09-07T07:45:00Z"),
                isDecided = { candidate ->
                    repository.hasDoseDecisionOnLocalDay(
                        candidate.doseTimeId,
                        candidate.scheduledFor,
                        candidate.zoneId,
                    )
                },
            )

            assertEquals(Instant.parse("2026-09-08T08:00:00Z"), rebuilt?.scheduledFor)
            assertEquals(30L, rebuilt?.doseTimeId)
        } finally {
            reopened.close()
        }
    }

    private fun openDatabase(): MedicationDatabase = Room.databaseBuilder(
        context,
        MedicationDatabase::class.java,
        databaseName,
    )
        .allowMainThreadQueries()
        .addMigrations(
            MedicationDatabase.MIGRATION_1_2,
            MedicationDatabase.MIGRATION_2_3,
        )
        .build()
}
