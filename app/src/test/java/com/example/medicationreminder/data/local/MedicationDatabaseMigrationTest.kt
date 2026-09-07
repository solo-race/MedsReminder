package com.example.medicationreminder.data.local

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
