package com.example.medicationreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.ZoneId

@Database(
    entities = [
        MedicationEntity::class,
        MedicationScheduleEntity::class,
        DoseTimeEntity::class,
        DoseEventEntity::class,
        PendingReminderEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MedicationDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseTimeDao(): DoseTimeDao
    abstract fun doseEventDao(): DoseEventDao

    abstract fun pendingReminderDao(): PendingReminderDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medication_schedules ADD COLUMN scheduleMode TEXT NOT NULL DEFAULT 'FIXED_TIMES'")
                db.execSQL("ALTER TABLE medication_schedules ADD COLUMN intervalMinutes INTEGER")
                db.execSQL("ALTER TABLE medication_schedules ADD COLUMN anchorEpochMillis INTEGER")
                db.execSQL("ALTER TABLE medications ADD COLUMN doseAmount TEXT")
                db.execSQL("ALTER TABLE medications ADD COLUMN doseUnit TEXT")
                db.execSQL("ALTER TABLE dose_events ADD COLUMN doseAmountSnapshot TEXT")
                db.execSQL("ALTER TABLE dose_events ADD COLUMN doseUnitSnapshot TEXT")
                db.execSQL("ALTER TABLE dose_events ADD COLUMN dosageTextSnapshot TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_reminders (
                        doseTimeId INTEGER NOT NULL, scheduledForEpochMillis INTEGER NOT NULL,
                        medicationId INTEGER NOT NULL, zoneId TEXT NOT NULL, stage TEXT NOT NULL,
                        PRIMARY KEY(doseTimeId, scheduledForEpochMillis),
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(doseTimeId) REFERENCES dose_times(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_reminders_medicationId ON pending_reminders(medicationId)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN alias TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE dose_events ADD COLUMN scheduledLocalEpochDay INTEGER NOT NULL DEFAULT 0",
                )

                val deviceZone = ZoneId.systemDefault()
                db.query(
                    """
                    SELECT e.id, e.scheduledForEpochMillis, s.timeZoneMode, s.manualZoneId
                    FROM dose_events e
                    LEFT JOIN dose_times d ON d.id = e.doseTimeId
                    LEFT JOIN medication_schedules s ON s.id = d.scheduleId
                    """.trimIndent(),
                ).use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val scheduledForIndex = cursor.getColumnIndexOrThrow("scheduledForEpochMillis")
                    val modeIndex = cursor.getColumnIndexOrThrow("timeZoneMode")
                    val manualZoneIndex = cursor.getColumnIndexOrThrow("manualZoneId")
                    while (cursor.moveToNext()) {
                        val mode = if (cursor.isNull(modeIndex)) null else cursor.getString(modeIndex)
                        val manualZone = if (cursor.isNull(manualZoneIndex)) null else cursor.getString(manualZoneIndex)
                        val zone = if (mode == "MANUAL" && manualZone != null) {
                            runCatching { ZoneId.of(manualZone) }.getOrDefault(deviceZone)
                        } else {
                            deviceZone
                        }
                        val epochDay = Instant.ofEpochMilli(cursor.getLong(scheduledForIndex))
                            .atZone(zone)
                            .toLocalDate()
                            .toEpochDay()
                        db.execSQL(
                            "UPDATE dose_events SET scheduledLocalEpochDay = ? WHERE id = ?",
                            arrayOf(epochDay, cursor.getLong(idIndex)),
                        )
                    }
                }

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_dose_events_doseTimeId_scheduledLocalEpochDay " +
                        "ON dose_events(doseTimeId, scheduledLocalEpochDay)",
                )
            }
        }
    }
}
