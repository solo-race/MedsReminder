package com.example.medicationreminder

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.medicationreminder.data.local.MedicationDatabase
import com.example.medicationreminder.data.photos.MedicationImageStore
import com.example.medicationreminder.data.repository.MedicationRepository
import com.example.medicationreminder.data.repository.RoomMedicationRepository
import com.example.medicationreminder.data.settings.AppPreferences
import com.example.medicationreminder.domain.decision.DoseDecisionUseCase
import com.example.medicationreminder.reminders.MissedReminderReposter
import com.example.medicationreminder.reminders.ReminderDoseDecisionEffects
import com.example.medicationreminder.reminders.ReminderNotifications
import com.example.medicationreminder.reminders.ReminderScheduler
import com.example.medicationreminder.reminders.ScheduleResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MedicationReminderApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
        container.notifications.createChannels()

        // Restore AlarmManager entries whenever the process starts. This covers app updates,
        // force-stop recovery and devices that remove pending alarms without broadcasting a
        // recoverable system event.
        applicationScope.launch {
            runCatching { container.scheduler.scheduleAll() }
                .onSuccess { result ->
                    if (result == ScheduleResult.WAITING_FOR_EXACT_ALARM_PERMISSION) {
                        Log.i(TAG, "Reminder alarm restore is waiting for exact-alarm access")
                    }
                }
                .onFailure { error -> Log.e(TAG, "Unable to restore reminder alarms", error) }
        }
    }

    private companion object {
        const val TAG = "MedicationReminderApp"
    }
}

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context,
        MedicationDatabase::class.java,
        "medication-reminder.db",
    )
        .addMigrations(MedicationDatabase.MIGRATION_1_2, MedicationDatabase.MIGRATION_2_3)
        .build()

    private val roomRepository = RoomMedicationRepository(database)
    val repository: MedicationRepository = roomRepository
    val imageStore = MedicationImageStore(context)
    val preferences = AppPreferences(context)
    val notifications = ReminderNotifications(context)
    val scheduler = ReminderScheduler(context, repository)
    val missedReminderReposter = MissedReminderReposter(repository, notifications)
    val doseDecisionUseCase = DoseDecisionUseCase(
        repository = roomRepository,
        effects = ReminderDoseDecisionEffects(repository, scheduler, notifications),
    )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MedicationReminderApplication).container
