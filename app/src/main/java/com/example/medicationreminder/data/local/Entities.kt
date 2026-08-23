package com.example.medicationreminder.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.TimeZoneMode

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosageText: String,
    val note: String,
    val alias: String? = null,
    val photoPath: String?,
    val enabled: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "medication_schedules",
    foreignKeys = [ForeignKey(
        entity = MedicationEntity::class,
        parentColumns = ["id"],
        childColumns = ["medicationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["medicationId"], unique = true)],
)
data class MedicationScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    /** Bit 0 is Monday through bit 6 Sunday. */
    val weekdaysMask: Int,
    val timeZoneMode: TimeZoneMode,
    val manualZoneId: String?,
)

@Entity(
    tableName = "dose_times",
    foreignKeys = [ForeignKey(
        entity = MedicationScheduleEntity::class,
        parentColumns = ["id"],
        childColumns = ["scheduleId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["scheduleId"])],
)
data class DoseTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val minuteOfDay: Int,
    val enabled: Boolean,
)

@Entity(
    tableName = "dose_events",
    indices = [
        Index(value = ["scheduledForEpochMillis"]),
        Index(value = ["doseTimeId", "scheduledForEpochMillis"], unique = true),
    ],
)
data class DoseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val doseTimeId: Long,
    val scheduledForEpochMillis: Long,
    val status: DoseStatus,
    val actionedAtEpochMillis: Long,
)
