package com.example.medicationreminder

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.medicationreminder.data.local.MedicationDatabase
import com.example.medicationreminder.data.photos.MedicationImageStore
import com.example.medicationreminder.data.repository.MedicationRepository
import com.example.medicationreminder.data.repository.RoomMedicationRepository
import com.example.medicationreminder.data.settings.AppPreferences
import com.example.medicationreminder.domain.decision.DoseDecisionUseCase
import com.example.medicationreminder.reminders.ReminderDoseDecisionEffects
import com.example.medicationreminder.reminders.ReminderNotifications
import com.example.medicationreminder.reminders.ReminderScheduler

class MedicationReminderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
        container.notifications.createChannels()
    }
}

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context,
        MedicationDatabase::class.java,
        "medication-reminder.db",
    )
        .addMigrations(MedicationDatabase.MIGRATION_1_2)
        .build()

    private val roomRepository = RoomMedicationRepository(database)
    val repository: MedicationRepository = roomRepository
    val imageStore = MedicationImageStore(context)
    val preferences = AppPreferences(context)
    val notifications = ReminderNotifications(context)
    val scheduler = ReminderScheduler(context, repository)
    val doseDecisionUseCase = DoseDecisionUseCase(
        repository = roomRepository,
        effects = ReminderDoseDecisionEffects(repository, scheduler, notifications),
    )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MedicationReminderApplication).container
