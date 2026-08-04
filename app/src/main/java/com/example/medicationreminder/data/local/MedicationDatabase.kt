package com.example.medicationreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        MedicationEntity::class,
        MedicationScheduleEntity::class,
        DoseTimeEntity::class,
        DoseEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MedicationDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseTimeDao(): DoseTimeDao
    abstract fun doseEventDao(): DoseEventDao
}
