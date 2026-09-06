package com.example.medicationreminder.data.repository

import androidx.room.withTransaction
import com.example.medicationreminder.data.local.DoseEventEntity
import com.example.medicationreminder.data.local.DoseTimeEntity
import com.example.medicationreminder.data.local.MedicationDatabase
import com.example.medicationreminder.data.local.MedicationEntity
import com.example.medicationreminder.data.local.MedicationScheduleEntity
import com.example.medicationreminder.domain.model.DoseEvent
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.DoseTime
import com.example.medicationreminder.domain.model.Medication
import com.example.medicationreminder.domain.model.MedicationDraft
import com.example.medicationreminder.domain.model.MedicationPlan
import com.example.medicationreminder.domain.model.MedicationSchedule
import com.example.medicationreminder.domain.model.ScheduledDose
import com.example.medicationreminder.domain.model.TimeZoneMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface MedicationRepository {
    fun observeMedicationPlans(): Flow<List<MedicationPlan>>
    fun observeDoseHistory(): Flow<List<DoseEvent>>
    suspend fun saveMedication(draft: MedicationDraft): SaveMedicationResult
    suspend fun deleteMedication(medicationId: Long): String?
    suspend fun activeScheduledDoses(): List<ScheduledDose>
    suspend fun recordDose(
        medicationId: Long,
        doseTimeId: Long,
        scheduledFor: Instant,
        status: DoseStatus,
    )
    suspend fun recordDoseIfOccurrenceActionable(occurrence: DoseOccurrence, status: DoseStatus): Boolean
    suspend fun hasDoseDecisionOnLocalDay(doseTimeId: Long, scheduledFor: Instant, zoneId: ZoneId): Boolean
    suspend fun setScheduleToDeviceTime(scheduleId: Long)
    suspend fun manualSchedules(): List<MedicationSchedule>
}

data class SaveMedicationResult(val medicationId: Long, val replacedPhotoPath: String?)

internal data class DoseTimeReconciliation(
    val toInsert: List<DoseTimeEntity>,
    val toUpdate: List<DoseTimeEntity>,
    val idsToDelete: List<Long>,
)

internal fun reconcileDoseTimes(
    scheduleId: Long,
    existing: List<DoseTimeEntity>,
    desiredMinutes: List<Int>,
): DoseTimeReconciliation {
    val desired = desiredMinutes.distinct().sorted()
    val existingByMinute = existing.groupBy { it.minuteOfDay }
    val retainedIds = mutableSetOf<Long>()
    val toInsert = mutableListOf<DoseTimeEntity>()
    val toUpdate = mutableListOf<DoseTimeEntity>()

    desired.forEach { minuteOfDay ->
        val retained = existingByMinute[minuteOfDay].orEmpty().minByOrNull { it.id }
        if (retained == null) {
            toInsert += DoseTimeEntity(
                scheduleId = scheduleId,
                minuteOfDay = minuteOfDay,
                enabled = true,
            )
        } else {
            retainedIds += retained.id
            if (!retained.enabled) {
                toUpdate += retained.copy(enabled = true)
            }
        }
    }

    val idsToDelete = existing.asSequence()
        .filter { it.id !in retainedIds }
        .map { it.id }
        .sorted()
        .toList()

    return DoseTimeReconciliation(
        toInsert = toInsert,
        toUpdate = toUpdate,
        idsToDelete = idsToDelete,
    )
}

internal fun isOccurrenceActionable(
    medication: MedicationEntity?,
    schedule: MedicationScheduleEntity?,
    doseTime: DoseTimeEntity?,
    occurrence: DoseOccurrence,
): Boolean {
    if (medication == null || !medication.enabled || medication.id != occurrence.medicationId) return false
    if (schedule == null || schedule.medicationId != medication.id) return false
    if (doseTime == null || !doseTime.enabled || doseTime.id != occurrence.doseTimeId || doseTime.scheduleId != schedule.id) return false

    val zoneId = schedule.toDomain().zoneId()
    if (zoneId != occurrence.zoneId) return false

    val occurrenceDate = occurrence.scheduledFor.atZone(zoneId).toLocalDate()
    if (occurrenceDate.dayOfWeek !in schedule.weekdaysMask.toWeekdays()) return false

    val scheduledTime = LocalTime.of(doseTime.minuteOfDay / 60, doseTime.minuteOfDay % 60)
    val expectedInstant = occurrenceDate.atTime(scheduledTime).atZone(zoneId).toInstant()
    return expectedInstant == occurrence.scheduledFor
}

class RoomMedicationRepository(
    private val database: MedicationDatabase,
) : MedicationRepository {
    private val medicationDao = database.medicationDao()
    private val scheduleDao = database.scheduleDao()
    private val doseTimeDao = database.doseTimeDao()
    private val doseEventDao = database.doseEventDao()

    override fun observeMedicationPlans(): Flow<List<MedicationPlan>> = combine(
        medicationDao.observeAll(),
        scheduleDao.observeAll(),
        doseTimeDao.observeAll(),
    ) { medications, schedules, doseTimes ->
        val timesBySchedule = doseTimes.groupBy { it.scheduleId }
        val schedulesByMedication = schedules.associateBy { it.medicationId }
        medications.mapNotNull { medication ->
            schedulesByMedication[medication.id]?.let { schedule ->
                MedicationPlan(
                    medication = medication.toDomain(),
                    schedule = schedule.toDomain(),
                    times = timesBySchedule[schedule.id].orEmpty()
                        .filter { it.enabled }
                        .sortedBy { it.minuteOfDay }
                        .map { it.toDomain() },
                )
            }
        }
    }

    override fun observeDoseHistory(): Flow<List<DoseEvent>> =
        doseEventDao.observeAllWithMedication().map { rows ->
            rows.map {
                DoseEvent(
                    id = it.id,
                    medicationName = it.medicationName,
                    dosageText = it.dosageText,
                    scheduledFor = Instant.ofEpochMilli(it.scheduledForEpochMillis),
                    status = it.status,
                    actionedAt = Instant.ofEpochMilli(it.actionedAtEpochMillis),
                )
            }
        }

    override suspend fun saveMedication(draft: MedicationDraft): SaveMedicationResult = database.withTransaction {
        val now = System.currentTimeMillis()
        val old = draft.id?.let { medicationDao.getById(it) }
        val medicationId = if (old == null) {
            medicationDao.insert(
                MedicationEntity(
                    name = draft.name.trim(),
                    dosageText = draft.dosageText.trim(),
                    note = draft.note.trim(),
                    photoPath = draft.existingPhotoPath,
                    alias = draft.alias?.trim()?.takeIf { it.isNotEmpty() },
                    enabled = draft.enabled,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        } else {
            medicationDao.update(
                old.copy(
                    name = draft.name.trim(),
                    dosageText = draft.dosageText.trim(),
                    note = draft.note.trim(),
                    photoPath = draft.existingPhotoPath,
                    alias = draft.alias?.trim()?.takeIf { it.isNotEmpty() },
                    enabled = draft.enabled,
                    updatedAtEpochMillis = now,
                ),
            )
            old.id
        }

        val existingSchedule = scheduleDao.getForMedication(medicationId)
        val scheduleId = if (existingSchedule == null) {
            scheduleDao.insert(
                MedicationScheduleEntity(
                    medicationId = medicationId,
                    weekdaysMask = draft.weekdays.toMask(),
                    timeZoneMode = draft.timeZoneMode,
                    manualZoneId = draft.manualZoneId,
                ),
            )
        } else {
            scheduleDao.update(
                existingSchedule.copy(
                    weekdaysMask = draft.weekdays.toMask(),
                    timeZoneMode = draft.timeZoneMode,
                    manualZoneId = draft.manualZoneId,
                ),
            )
            existingSchedule.id
        }

        val reconciliation = reconcileDoseTimes(
            scheduleId = scheduleId,
            existing = doseTimeDao.getForSchedule(scheduleId),
            desiredMinutes = draft.times.map { it.toMinuteOfDay() },
        )
        if (reconciliation.idsToDelete.isNotEmpty()) {
            doseTimeDao.deleteByIds(reconciliation.idsToDelete)
        }
        if (reconciliation.toUpdate.isNotEmpty()) {
            doseTimeDao.updateAll(reconciliation.toUpdate)
        }
        if (reconciliation.toInsert.isNotEmpty()) {
            doseTimeDao.insertAll(reconciliation.toInsert)
        }

        SaveMedicationResult(medicationId, old?.photoPath?.takeIf { it != draft.existingPhotoPath })
    }

    override suspend fun deleteMedication(medicationId: Long): String? = database.withTransaction {
        val photoPath = medicationDao.getById(medicationId)?.photoPath
        doseEventDao.deleteForMedication(medicationId)
        medicationDao.deleteById(medicationId)
        photoPath
    }

    override suspend fun activeScheduledDoses(): List<ScheduledDose> = database.withTransaction {
        val medicationsById = medicationDao.getAll().associateBy { it.id }
        val allSchedules = scheduleDao.getAll()
        val allTimes = doseTimeDao.getAll().filter { it.enabled }
        val timesBySchedule = allTimes.groupBy { it.scheduleId }
        allSchedules.flatMap { schedule ->
            val medication = medicationsById[schedule.medicationId]
            if (medication == null || !medication.enabled) emptyList() else {
                val zone = schedule.toDomain().zoneId()
                timesBySchedule[schedule.id].orEmpty().map { time ->
                    ScheduledDose(
                        medicationId = medication.id,
                        medicationName = medication.name,
                        dosageText = medication.dosageText,
                        medicationAlias = medication.alias,
                        scheduleId = schedule.id,
                        doseTimeId = time.id,
                        time = time.toDomain().time,
                        weekdays = schedule.weekdaysMask.toWeekdays(),
                        zoneId = zone,
                    )
                }
            }
        }
    }

    override suspend fun recordDose(
        medicationId: Long,
        doseTimeId: Long,
        scheduledFor: Instant,
        status: DoseStatus,
    ) = database.withTransaction {
        insertDoseEvent(medicationId, doseTimeId, scheduledFor, status)
    }

    override suspend fun recordDoseIfOccurrenceActionable(occurrence: DoseOccurrence, status: DoseStatus): Boolean =
        database.withTransaction {
            val medication = medicationDao.getById(occurrence.medicationId)
            val schedule = scheduleDao.getForMedication(occurrence.medicationId)
            val doseTime = doseTimeDao.getById(occurrence.doseTimeId)
            if (!isOccurrenceActionable(medication, schedule, doseTime, occurrence)) return@withTransaction false
            insertDoseEvent(
                occurrence.medicationId,
                occurrence.doseTimeId,
                occurrence.scheduledFor,
                status,
            )
            true
        }

    private suspend fun insertDoseEvent(
        medicationId: Long,
        doseTimeId: Long,
        scheduledFor: Instant,
        status: DoseStatus,
    ) {
        val now = System.currentTimeMillis()
        doseEventDao.insertOrReplace(
            DoseEventEntity(
                medicationId = medicationId,
                doseTimeId = doseTimeId,
                scheduledForEpochMillis = scheduledFor.toEpochMilli(),
                status = status,
                actionedAtEpochMillis = now,
            ),
        )
        doseEventDao.deleteOlderThan(now - HISTORY_RETENTION_MILLIS)
    }

    override suspend fun hasDoseDecisionOnLocalDay(doseTimeId: Long, scheduledFor: Instant, zoneId: ZoneId): Boolean {
        val day = scheduledFor.atZone(zoneId).toLocalDate()
        val from = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return doseEventDao.existsForOnLocalDay(doseTimeId, from, to)
    }

    override suspend fun setScheduleToDeviceTime(scheduleId: Long) {
        database.withTransaction {
            scheduleDao.getById(scheduleId)?.let {
                scheduleDao.update(it.copy(timeZoneMode = TimeZoneMode.DEVICE, manualZoneId = null))
            }
        }
    }

    override suspend fun manualSchedules(): List<MedicationSchedule> = database.withTransaction {
        scheduleDao.getAll().filter { it.timeZoneMode == TimeZoneMode.MANUAL }.map { it.toDomain() }
    }

    private companion object {
        const val HISTORY_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000
    }
}

private fun MedicationEntity.toDomain() = Medication(id, name, dosageText, note, photoPath, enabled, alias)

private fun MedicationScheduleEntity.toDomain() = MedicationSchedule(
    id = id,
    medicationId = medicationId,
    weekdays = weekdaysMask.toWeekdays(),
    timeZoneMode = timeZoneMode,
    manualZoneId = manualZoneId,
)

private fun DoseTimeEntity.toDomain() = DoseTime(
    id = id,
    scheduleId = scheduleId,
    time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
    enabled = enabled,
)

private fun MedicationSchedule.zoneId(): ZoneId = when (timeZoneMode) {
    TimeZoneMode.DEVICE -> ZoneId.systemDefault()
    TimeZoneMode.MANUAL -> manualZoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()
}

private fun Set<DayOfWeek>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

private fun Int.toWeekdays(): Set<DayOfWeek> = DayOfWeek.entries.filterTo(linkedSetOf()) {
    this and (1 shl (it.value - 1)) != 0
}

private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
