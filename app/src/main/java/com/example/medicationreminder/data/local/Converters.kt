package com.example.medicationreminder.data.local

import androidx.room.TypeConverter
import com.example.medicationreminder.domain.model.ScheduleMode
import com.example.medicationreminder.domain.model.ReminderStage
import com.example.medicationreminder.domain.model.DoseStatus
import com.example.medicationreminder.domain.model.TimeZoneMode

class Converters {
    @TypeConverter fun fromScheduleMode(value: ScheduleMode): String = value.name
    @TypeConverter fun toScheduleMode(value: String): ScheduleMode = ScheduleMode.valueOf(value)
    @TypeConverter fun fromReminderStage(value: ReminderStage): String = value.name
    @TypeConverter fun toReminderStage(value: String): ReminderStage = ReminderStage.valueOf(value)
    @TypeConverter fun fromTimeZoneMode(value: TimeZoneMode): String = value.name
    @TypeConverter fun toTimeZoneMode(value: String): TimeZoneMode = TimeZoneMode.valueOf(value)
    @TypeConverter fun fromDoseStatus(value: DoseStatus): String = value.name
    @TypeConverter fun toDoseStatus(value: String): DoseStatus = DoseStatus.valueOf(value)
}
