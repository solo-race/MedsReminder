package com.example.medicationreminder.domain.model

import java.time.Duration
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class TimeZoneMode { DEVICE, MANUAL }

enum class DoseStatus { TAKEN, SKIPPED }

enum class ScheduleMode { FIXED_TIMES, INTERVAL }

enum class ReminderStage { PRE, DUE }

data class PendingReminder(val occurrence: DoseOccurrence, val stage: ReminderStage) {
    fun matches(candidate: DoseOccurrence): Boolean = occurrence.medicationId == candidate.medicationId &&
        occurrence.doseTimeId == candidate.doseTimeId && occurrence.scheduledFor == candidate.scheduledFor
}

data class Medication(
    val id: Long,
    val name: String,
    val dosageText: String,
    val note: String,
    val photoPath: String?,
    val enabled: Boolean,
    val alias: String?,
    val doseAmount: String? = null,
    val doseUnit: String? = null,
)

data class MedicationSchedule(
    val id: Long,
    val medicationId: Long,
    val weekdays: Set<DayOfWeek>,
    val timeZoneMode: TimeZoneMode,
    val manualZoneId: String?,
    val scheduleMode: ScheduleMode = ScheduleMode.FIXED_TIMES,
    val intervalMinutes: Long? = null,
    val anchorEpochMillis: Long? = null,
)

data class DoseTime(
    val id: Long,
    val scheduleId: Long,
    val time: LocalTime,
    val enabled: Boolean,
)

data class MedicationPlan(
    val medication: Medication,
    val schedule: MedicationSchedule,
    val times: List<DoseTime>,
)

data class MedicationDraft(
    val id: Long? = null,
    val name: String,
    val dosageText: String,
    val note: String,
    val enabled: Boolean,
    val weekdays: Set<DayOfWeek>,
    val timeZoneMode: TimeZoneMode,
    val manualZoneId: String?,
    val times: List<LocalTime>,
    val existingPhotoPath: String? = null,
    val alias: String? = null,
    val scheduleMode: ScheduleMode = ScheduleMode.FIXED_TIMES,
    val intervalMinutes: Long? = null,
    val anchorEpochMillis: Long? = null,
    val doseAmount: String? = null,
    val doseUnit: String? = null,
)

data class DoseEvent(
    val id: Long,
    val medicationName: String,
    val dosageText: String,
    val scheduledFor: Instant,
    val status: DoseStatus,
    val actionedAt: Instant,
    val medicationId: Long = 0,
    val doseAmountSnapshot: String? = null,
    val doseUnitSnapshot: String? = null,
    val dosageTextSnapshot: String? = null,
)

data class DoseOccurrence(
    val medicationId: Long,
    val doseTimeId: Long,
    val scheduledFor: Instant,
    val zoneId: ZoneId,
)

sealed interface DoseDecisionResult {
    data class Recorded(val status: DoseStatus) : DoseDecisionResult
    data class AlreadyDecided(val status: DoseStatus) : DoseDecisionResult
    data object StaleOccurrence : DoseDecisionResult
}

data class ScheduledDose(
    val medicationId: Long,
    val medicationName: String,
    val dosageText: String,
    val scheduleId: Long,
    val doseTimeId: Long,
    val time: LocalTime,
    val weekdays: Set<DayOfWeek>,
    val zoneId: ZoneId,
    val medicationAlias: String?,
    val scheduleMode: ScheduleMode = ScheduleMode.FIXED_TIMES,
    val intervalMinutes: Long? = null,
    val anchorEpochMillis: Long? = null,
    val doseAmount: String? = null,
    val doseUnit: String? = null,
) {
    /** Persisted pending was validated on insertion; its original zone/instant survives travel. */
    fun matchesOccurrence(occurrence: DoseOccurrence, persistedPending: Boolean = false): Boolean {
        if (medicationId != occurrence.medicationId || doseTimeId != occurrence.doseTimeId) return false
        if (persistedPending) return true
        if (scheduleMode == ScheduleMode.INTERVAL) {
            val anchor = anchorEpochMillis?.let(Instant::ofEpochMilli) ?: return false
            val minutes = intervalMinutes?.takeIf { it in 1..Long.MAX_VALUE / 60 } ?: return false
            if (occurrence.scheduledFor.isBefore(anchor)) return false
            val elapsed = Duration.between(anchor, occurrence.scheduledFor)
            return elapsed.nano == 0 && elapsed.seconds % (minutes * 60) == 0L
        }
        if (zoneId != occurrence.zoneId) return false
        val date = occurrence.scheduledFor.atZone(zoneId).toLocalDate()
        return date.dayOfWeek in weekdays && date.atTime(time).atZone(zoneId).toInstant() == occurrence.scheduledFor
    }

    fun occurrenceAt(scheduledFor: Instant): DoseOccurrence = DoseOccurrence(
        medicationId = medicationId,
        doseTimeId = doseTimeId,
        scheduledFor = scheduledFor,
        zoneId = zoneId,
    )
}

fun MedicationSchedule.zoneId(): ZoneId = when (timeZoneMode) {
    TimeZoneMode.DEVICE -> ZoneId.systemDefault()
    TimeZoneMode.MANUAL -> manualZoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()
}

fun MedicationPlan.scheduledDose(doseTimeId: Long): ScheduledDose? {
    if (!medication.enabled) return null
    val slot = times.firstOrNull { it.id == doseTimeId && it.enabled } ?: return null
    return ScheduledDose(
        medication.id, medication.name, medication.dosageText, schedule.id, slot.id,
        slot.time, schedule.weekdays, schedule.zoneId(), medication.alias,
        schedule.scheduleMode, schedule.intervalMinutes, schedule.anchorEpochMillis,
        medication.doseAmount, medication.doseUnit,
    )
}
