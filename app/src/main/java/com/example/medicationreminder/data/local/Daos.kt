package com.example.medicationreminder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query("SELECT * FROM dose_times WHERE scheduleId = :scheduleId")
    suspend fun getForSchedule(scheduleId: Long): List<DoseTimeEntity>

    @Query("SELECT * FROM dose_times")
    suspend fun getAll(): List<DoseTimeEntity>

    @Query("DELETE FROM dose_times WHERE scheduleId = :scheduleId")
    suspend fun deleteForSchedule(scheduleId: Long)

    @Insert
    suspend fun insertAll(times: List<DoseTimeEntity>)
}

data class DoseEventRow(
    val id: Long,
    val medicationName: String,
    val dosageText: String,
    val scheduledForEpochMillis: Long,
    val status: com.example.medicationreminder.domain.model.DoseStatus,
    val actionedAtEpochMillis: Long,
)

@Dao
interface DoseEventDao {
    @Query(
        """
        SELECT e.id, m.name AS medicationName, m.dosageText AS dosageText,
               e.scheduledForEpochMillis, e.status, e.actionedAtEpochMillis
        FROM dose_events e
        INNER JOIN medications m ON m.id = e.medicationId
        ORDER BY e.scheduledForEpochMillis DESC
        """
    )
    fun observeAllWithMedication(): Flow<List<DoseEventRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(event: DoseEventEntity)

    @Query("DELETE FROM dose_events WHERE actionedAtEpochMillis < :cutoffEpochMillis")
    suspend fun deleteOlderThan(cutoffEpochMillis: Long)

    @Query("DELETE FROM dose_events WHERE medicationId = :medicationId")
    suspend fun deleteForMedication(medicationId: Long)

    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM dose_events
        WHERE doseTimeId = :doseTimeId AND scheduledForEpochMillis BETWEEN :fromInclusive AND :toInclusive)
        """
    )
    suspend fun existsForOnLocalDay(doseTimeId: Long, fromInclusive: Long, toInclusive: Long): Boolean
}
