package com.example.medicationreminder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications")
    suspend fun getAll(): List<MedicationEntity>

    @Insert
    suspend fun insert(medication: MedicationEntity): Long

    @Update
    suspend fun update(medication: MedicationEntity)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM medication_schedules")
    fun observeAll(): Flow<List<MedicationScheduleEntity>>

    @Query("SELECT * FROM medication_schedules WHERE medicationId = :medicationId")
    suspend fun getForMedication(medicationId: Long): MedicationScheduleEntity?

    @Query("SELECT * FROM medication_schedules WHERE id = :id")
    suspend fun getById(id: Long): MedicationScheduleEntity?

    @Query("SELECT * FROM medication_schedules")
    suspend fun getAll(): List<MedicationScheduleEntity>

    @Insert
    suspend fun insert(schedule: MedicationScheduleEntity): Long

    @Update
    suspend fun update(schedule: MedicationScheduleEntity)
}

@Dao
interface DoseTimeDao {
    @Query("SELECT * FROM dose_times")
    fun observeAll(): Flow<List<DoseTimeEntity>>

    @Query("SELECT * FROM dose_times WHERE id = :id")
    suspend fun getById(id: Long): DoseTimeEntity?

    @Query("SELECT * FROM dose_times WHERE scheduleId = :scheduleId")
    suspend fun getForSchedule(scheduleId: Long): List<DoseTimeEntity>

    @Query("SELECT * FROM dose_times")
    suspend fun getAll(): List<DoseTimeEntity>

    @Query("DELETE FROM dose_times WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Insert
    suspend fun insertAll(times: List<DoseTimeEntity>)

    @Update
    suspend fun updateAll(times: List<DoseTimeEntity>)
}

data class DoseEventRow(
    val id: Long,
    val medicationName: String,
    val scheduledForEpochMillis: Long,
    val status: com.example.medicationreminder.domain.model.DoseStatus,
    val actionedAtEpochMillis: Long,
    val medicationId: Long,
    val doseAmountSnapshot: String?,
    val doseUnitSnapshot: String?,
    val dosageTextSnapshot: String?,
)

@Dao
interface DoseEventDao {
    @Query(
        """
        SELECT e.id, e.medicationId, m.name AS medicationName,
               e.doseAmountSnapshot, e.doseUnitSnapshot, e.dosageTextSnapshot,
               e.scheduledForEpochMillis, e.status, e.actionedAtEpochMillis
        FROM dose_events e
        INNER JOIN medications m ON m.id = e.medicationId
        ORDER BY e.scheduledForEpochMillis DESC
        """
    )
    fun observeAllWithMedication(): Flow<List<DoseEventRow>>

    @Insert
    suspend fun insert(event: DoseEventEntity)

    @Query("SELECT * FROM dose_events WHERE doseTimeId = :doseTimeId AND scheduledForEpochMillis = :scheduledFor LIMIT 1")
    suspend fun forOccurrence(doseTimeId: Long, scheduledFor: Long): DoseEventEntity?

    @Query("DELETE FROM dose_events WHERE actionedAtEpochMillis < :cutoffEpochMillis")
    suspend fun deleteOlderThan(cutoffEpochMillis: Long)

    @Query("DELETE FROM dose_events WHERE medicationId = :medicationId")
    suspend fun deleteForMedication(medicationId: Long)

    @Query(
        """
        SELECT * FROM dose_events
        WHERE doseTimeId = :doseTimeId
          AND scheduledLocalEpochDay = :scheduledLocalEpochDay
        ORDER BY actionedAtEpochMillis ASC, id ASC
        LIMIT 1
        """
    )
    suspend fun firstForLogicalDay(doseTimeId: Long, scheduledLocalEpochDay: Long): DoseEventEntity?

    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM dose_events
        WHERE doseTimeId = :doseTimeId AND scheduledLocalEpochDay = :scheduledLocalEpochDay)
        """
    )
    suspend fun existsForLogicalDay(doseTimeId: Long, scheduledLocalEpochDay: Long): Boolean
}

@Dao
interface PendingReminderDao {
    @Query("SELECT * FROM pending_reminders ORDER BY scheduledForEpochMillis, doseTimeId")
    fun observeAll(): Flow<List<PendingReminderEntity>>

    @Query("SELECT * FROM pending_reminders ORDER BY scheduledForEpochMillis, doseTimeId")
    suspend fun getAll(): List<PendingReminderEntity>

    @Query("SELECT * FROM pending_reminders WHERE medicationId = :medicationId ORDER BY scheduledForEpochMillis, doseTimeId")
    suspend fun getForMedication(medicationId: Long): List<PendingReminderEntity>

    @Query("SELECT * FROM pending_reminders WHERE doseTimeId = :doseTimeId AND scheduledForEpochMillis = :scheduledFor")
    suspend fun get(doseTimeId: Long, scheduledFor: Long): PendingReminderEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(reminder: PendingReminderEntity): Long

    @Query("UPDATE pending_reminders SET stage = 'DUE' WHERE doseTimeId = :doseTimeId AND scheduledForEpochMillis = :scheduledFor AND stage = 'PRE'")
    suspend fun advanceToDue(doseTimeId: Long, scheduledFor: Long): Int

    @Query("DELETE FROM pending_reminders WHERE doseTimeId = :doseTimeId AND scheduledForEpochMillis = :scheduledFor")
    suspend fun delete(doseTimeId: Long, scheduledFor: Long)

    @Query("DELETE FROM pending_reminders WHERE doseTimeId = :doseTimeId")
    suspend fun deleteForSlot(doseTimeId: Long)

    @Query("DELETE FROM pending_reminders WHERE medicationId = :medicationId")
    suspend fun deleteForMedication(medicationId: Long)
}
