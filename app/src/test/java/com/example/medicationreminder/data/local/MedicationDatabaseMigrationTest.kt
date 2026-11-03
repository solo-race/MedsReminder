package com.example.medicationreminder.data.local

import androidx.room.Room
import com.example.medicationreminder.domain.model.ScheduleMode
import com.example.medicationreminder.domain.model.ReminderStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MedicationDatabaseMigrationTest {
    private val databaseName = "phase6-migration-2-3.db"
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
    fun migration2To3BackfillsManualZoneLogicalDayAndCreatesLookupIndex() {
        val manualZone = ZoneId.of("Pacific/Kiritimati")
        val scheduledFor = Instant.parse("2026-09-07T12:00:00Z")
        val expectedEpochDay = scheduledFor.atZone(manualZone).toLocalDate().toEpochDay()
        val helper = openVersion2Database()

        try {
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO medication_schedules(id, timeZoneMode, manualZoneId) VALUES(20, 'MANUAL', ?)",
                arrayOf(manualZone.id),
            )
            db.execSQL("INSERT INTO dose_times(id, scheduleId) VALUES(30, 20)")
            db.execSQL(
                "INSERT INTO dose_events(id, doseTimeId, scheduledForEpochMillis) VALUES(40, 30, ?)",
                arrayOf(scheduledFor.toEpochMilli()),
            )

            MedicationDatabase.MIGRATION_2_3.migrate(db)

            db.query(
                "SELECT scheduledLocalEpochDay FROM dose_events WHERE id = 40",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(expectedEpochDay, cursor.getLong(0))
            }

            db.query("PRAGMA index_list('dose_events')").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "index_dose_events_doseTimeId_scheduledLocalEpochDay") {
                        found = true
                        break
                    }
                }
                assertTrue(found)
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun migration3To4PassesRoomValidationPreservesDataAndCascadesPending() = runBlocking {
        val helper = openVersion3Database()
        val old = helper.writableDatabase
        old.execSQL("INSERT INTO medications VALUES(10, 'Legacy', '1 tablet', 'note', NULL, NULL, 1, 100, 200)")
        old.execSQL("INSERT INTO medication_schedules VALUES(20, 10, 127, 'MANUAL', 'UTC')")
        old.execSQL("INSERT INTO dose_times VALUES(30, 20, 480, 1)")
        old.execSQL("INSERT INTO dose_events VALUES(40, 10, 30, 1000, 0, 'TAKEN', 2000)")
        helper.close()
        val migrated = Room.databaseBuilder(context, MedicationDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .addMigrations(MedicationDatabase.MIGRATION_1_2, MedicationDatabase.MIGRATION_2_3,
                MedicationDatabase.MIGRATION_3_4)
            .build()
        try {
            // Opening Room performs generated schema validation, including defaults, FKs and indices.
            val db = migrated.openHelper.writableDatabase
            assertEquals(4, db.version)
            val medication = migrated.medicationDao().getById(10)!!
            assertEquals("Legacy", medication.name)
            assertEquals("1 tablet", medication.dosageText)
            assertNull(medication.doseAmount)
            assertNull(medication.doseUnit)
            val schedule = migrated.scheduleDao().getById(20)!!
            assertEquals(ScheduleMode.FIXED_TIMES, schedule.scheduleMode)
            assertNull(schedule.intervalMinutes)
            assertNull(schedule.anchorEpochMillis)
            assertEquals(480, migrated.doseTimeDao().getById(30)!!.minuteOfDay)
            val event = migrated.doseEventDao().forOccurrence(30, 1000)!!
            assertEquals(40L, event.id)
            assertEquals(2000L, event.actionedAtEpochMillis)
            assertNull(event.doseAmountSnapshot)
            assertNull(event.doseUnitSnapshot)
            assertNull(event.dosageTextSnapshot)
            val pending = PendingReminderEntity(30, 3000, 10, "UTC", ReminderStage.PRE)
            migrated.pendingReminderDao().insert(pending)
            migrated.pendingReminderDao().insert(pending)
            assertEquals(1, migrated.pendingReminderDao().getAll().size)
            migrated.pendingReminderDao().advanceToDue(30, 3000)
            assertEquals(ReminderStage.DUE, migrated.pendingReminderDao().getAll().single().stage)
            migrated.doseTimeDao().deleteByIds(listOf(30))
            assertTrue(migrated.pendingReminderDao().getAll().isEmpty())
            // Historical events deliberately outlive removed slots.
            assertEquals(40L, migrated.doseEventDao().forOccurrence(30, 1000)!!.id)
        } finally { migrated.close() }
    }

    private fun openVersion3Database(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dosageText` TEXT NOT NULL, `note` TEXT NOT NULL, `alias` TEXT, `photoPath` TEXT, `enabled` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS `medication_schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, `weekdaysMask` INTEGER NOT NULL, `timeZoneMode` TEXT NOT NULL, `manualZoneId` TEXT, FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                    db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_medication_schedules_medicationId` ON `medication_schedules` (`medicationId`)""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS `dose_times` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scheduleId` INTEGER NOT NULL, `minuteOfDay` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, FOREIGN KEY(`scheduleId`) REFERENCES `medication_schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                    db.execSQL("""CREATE INDEX IF NOT EXISTS `index_dose_times_scheduleId` ON `dose_times` (`scheduleId`)""")
                    db.execSQL("""CREATE TABLE IF NOT EXISTS `dose_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, `doseTimeId` INTEGER NOT NULL, `scheduledForEpochMillis` INTEGER NOT NULL, `scheduledLocalEpochDay` INTEGER NOT NULL DEFAULT 0, `status` TEXT NOT NULL, `actionedAtEpochMillis` INTEGER NOT NULL)""")
                    db.execSQL("""CREATE INDEX IF NOT EXISTS `index_dose_events_scheduledForEpochMillis` ON `dose_events` (`scheduledForEpochMillis`)""")
                    db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_dose_events_doseTimeId_scheduledForEpochMillis` ON `dose_events` (`doseTimeId`, `scheduledForEpochMillis`)""")
                    db.execSQL("""CREATE INDEX IF NOT EXISTS `index_dose_events_doseTimeId_scheduledLocalEpochDay` ON `dose_events` (`doseTimeId`, `scheduledLocalEpochDay`)""")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun openVersion2Database(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE medication_schedules (" +
                            "id INTEGER NOT NULL PRIMARY KEY, " +
                            "timeZoneMode TEXT NOT NULL, " +
                            "manualZoneId TEXT)",
                    )
                    db.execSQL(
                        "CREATE TABLE dose_times (" +
                            "id INTEGER NOT NULL PRIMARY KEY, " +
                            "scheduleId INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE TABLE dose_events (" +
                            "id INTEGER NOT NULL PRIMARY KEY, " +
                            "doseTimeId INTEGER NOT NULL, " +
                            "scheduledForEpochMillis INTEGER NOT NULL)",
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }
}
