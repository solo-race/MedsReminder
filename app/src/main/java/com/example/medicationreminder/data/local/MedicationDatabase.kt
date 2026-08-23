package com.example.medicationreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MedicationEntity::class,
        MedicationScheduleEntity::class,
        DoseTimeEntity::class,
        DoseEventEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MedicationDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseTimeDao(): DoseTimeDao
    abstract fun doseEventDao(): DoseEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN alias TEXT")
            }
        }
    }
}
