package com.example.medicationreminder.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class TimeZoneMode { DEVICE, MANUAL }

enum class DoseStatus { TAKEN, SKIPPED }

data class Medication(
    val id: Long,
    val name: String,
    val dosageText: String,
    val note: String,
    val photoPath: String?,
    val enabled: Boolean,
    val alias: String?,
)

data class MedicationSchedule(
    val id: Long,
    val medicationId: Long,
    val weekdays: Set<DayOfWeek>,
    val timeZoneMode: TimeZoneMode,
    val manualZoneId: String?,
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
)

data class DoseEvent(
    val id: Long,
    val medicationName: String,
    val dosageText: String,
    val scheduledFor: Instant,
    val status: DoseStatus,
    val actionedAt: Instant,
)

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
)

