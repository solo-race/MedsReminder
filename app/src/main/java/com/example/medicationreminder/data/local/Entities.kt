package com.example.medicationreminder.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.medicationreminder.domain.model.ScheduleMode
import com.example.medicationreminder.domain.model.ReminderStage
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
    val doseAmount: String? = null,
    val doseUnit: String? = null,
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
    @ColumnInfo(defaultValue = "'FIXED_TIMES'") val scheduleMode: ScheduleMode = ScheduleMode.FIXED_TIMES,
    val intervalMinutes: Long? = null,
    val anchorEpochMillis: Long? = null,
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
        Index(value = ["doseTimeId", "scheduledLocalEpochDay"]),
    ],
)
data class DoseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val doseTimeId: Long,
    val scheduledForEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val scheduledLocalEpochDay: Long,
    val status: DoseStatus,
    val actionedAtEpochMillis: Long,
    val doseAmountSnapshot: String? = null,
    val doseUnitSnapshot: String? = null,
    val dosageTextSnapshot: String? = null,
)

@Entity(
    tableName = "pending_reminders",
    primaryKeys = ["doseTimeId", "scheduledForEpochMillis"],
    foreignKeys = [
        ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"],
            childColumns = ["medicationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DoseTimeEntity::class, parentColumns = ["id"],
            childColumns = ["doseTimeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["medicationId"])],
)
data class PendingReminderEntity(
    val doseTimeId: Long,
    val scheduledForEpochMillis: Long,
    val medicationId: Long,
    val zoneId: String,
    val stage: ReminderStage,
)
