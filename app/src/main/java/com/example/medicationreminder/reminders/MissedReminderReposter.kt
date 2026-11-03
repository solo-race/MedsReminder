package com.example.medicationreminder.reminders

import com.example.medicationreminder.data.repository.MedicationRepository
import com.example.medicationreminder.data.settings.AppPreferences
import com.example.medicationreminder.domain.model.DoseOccurrence
import com.example.medicationreminder.domain.model.ReminderStage
import com.example.medicationreminder.domain.model.ScheduledDose
import com.example.medicationreminder.domain.scheduling.NextDoseCalculator
import java.time.Instant
import kotlinx.coroutines.flow.first

/** Silent renders preserve the stored phase; only a real delivery may advance its alert state. */
internal fun pendingStageForDelivery(stored: ReminderStage?, requested: ReminderStage,
    alert: Boolean, isDue: Boolean): ReminderStage = when {
    stored == ReminderStage.DUE -> ReminderStage.DUE
    !alert && stored != null -> stored
    requested == ReminderStage.PRE && stored == null && alert && isDue -> ReminderStage.DUE
    else -> requested
}

/** One recovery entry for persisted pending and optional previously unseen overdue doses. */
class MissedReminderReposter(
    private val repository: MedicationRepository,
    private val notifications: ReminderNotifications,
    private val preferences: AppPreferences? = null,
) {
    suspend fun deliverOccurrence(occurrence: DoseOccurrence, phase: ReminderStage,
        alert: Boolean, legacy: Boolean = false, now: Instant = Instant.now()): Boolean {
        val pending = repository.pendingReminders(occurrence.medicationId).firstOrNull { it.matches(occurrence) }
        val dose = repository.activeScheduledDoses().firstOrNull { it.matchesOccurrence(occurrence, pending != null) }
            ?: return false
        val identity = pending?.occurrence ?: occurrence
        if (repository.hasDoseDecision(identity)) {
            repository.deletePendingReminder(identity)
            notifications.cancelReminder(identity)
            if (legacy) notifications.cancelLegacyReminder(identity.doseTimeId)
            return false
        }
        // A late PRE cannot downgrade or replay the DUE sound.
        val stage = pendingStageForDelivery(pending?.stage, phase, alert,
            isDue = !identity.scheduledFor.isAfter(now))
        val changed = repository.registerPendingReminder(identity, stage)
        if (!repository.pendingReminders(identity.medicationId).any { it.matches(identity) }) return false
        val sound = preferences?.reminderSoundEnabled?.first() ?: true
        val languageTag = preferences?.language?.first()?.languageTag
        val posted = deliverReminderIfUndecided(dose, identity,
            isDecided = { repository.hasDoseDecision(identity) },
            showReminder = { notifications.showReminder(identity, dose.medicationAlias, dose.dosageText,
                if (identity.scheduledFor.isAfter(now)) ReminderStage.PRE else ReminderStage.DUE,
                soundEnabled = sound, alert = alert && changed, languageTag = languageTag) },
            cancelVisibleReminder = { notifications.cancelReminder(identity) }, persistedPending = true)
        if (legacy && posted) notifications.cancelLegacyReminder(identity.doseTimeId)
        return posted
    }

    suspend fun restoreOccurrence(occurrence: DoseOccurrence, legacy: Boolean = false,
        now: Instant = Instant.now()): Boolean = deliverOccurrence(occurrence,
        if (occurrence.scheduledFor.isAfter(now)) ReminderStage.PRE else ReminderStage.DUE,
        alert = false, legacy = legacy, now = now)

    suspend fun restorePending(now: Instant = Instant.now()) {
        val active = repository.activeScheduledDoses()
        for (pending in repository.pendingReminders()) {
            val occurrence = pending.occurrence
            if (active.none { it.matchesOccurrence(occurrence, persistedPending = true) }) {
                repository.deletePendingReminder(occurrence)
                notifications.cancelReminder(occurrence)
            } else deliverOccurrence(occurrence, pending.stage, alert = false, now = now)
        }
    }

    suspend fun repostUndecidedOverdue(now: Instant = Instant.now()) {
        restorePending(now)
        plan(repository.activeScheduledDoses(), now) { dose, instant ->
            repository.hasDoseDecision(dose.occurrenceAt(instant))
        }.forEach {
            val occurrence = it.dose.occurrenceAt(it.scheduledFor)
            if (repository.pendingReminders(occurrence.medicationId).none { pending -> pending.matches(occurrence) })
                deliverOccurrence(occurrence, ReminderStage.DUE, alert = false, now = now)
        }
    }

    companion object {
        data class MissedDose(val dose: ScheduledDose, val scheduledFor: Instant)
        suspend fun plan(doses: List<ScheduledDose>, now: Instant,
            hasDecisionOnLocalDay: suspend (ScheduledDose, Instant) -> Boolean): List<MissedDose> = buildList {
            for (dose in doses) {
                val at = NextDoseCalculator.mostRecentOverdueOccurrence(dose, now) ?: continue
                if (!hasDecisionOnLocalDay(dose, at)) add(MissedDose(dose, at))
            }
        }
    }
}
