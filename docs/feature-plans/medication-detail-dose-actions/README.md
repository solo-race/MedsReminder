# Medication Detail + Pre-alarm Dose Decisions

Status: implementation planned; development branch active.

Branch: `codex/feature-medication-detail-dose-actions`

## Goal

Add a read-only medication detail flow and allow a user to mark the relevant dose occurrence as Taken or Skipped before its alarm fires, while preserving the invariant that an already-decided occurrence does not alert again after app restart, rescheduling, system time events, schedule edits, or alarm-delivery races.

## Scope

- Today medication card opens a read-only Detail screen instead of Edit.
- Reminder notification opens Detail with the exact reminder occurrence identity.
- Detail exposes Taken, Skipped, and Edit actions.
- Existing EditMedicationScreen remains the only medication editor.
- Existing `dose_events` table and `DoseStatus.TAKEN/SKIPPED` remain the persistence model; no Room schema migration is planned.
- Scheduling becomes decision-aware.
- Reminder delivery performs a final decision check before posting a notification.

## Required invariants

1. A dose occurrence is identified by a stable dose slot plus its exact `scheduledFor` instant; UI code must not infer a fired notification's occurrence from `Instant.now()`.
2. An unchanged dose slot keeps the same `doseTimeId` across unrelated medication edits.
3. Recording a dose decision is idempotent at the schedule-zone local-day policy boundary used by reminder deduplication.
4. Database decision state is authoritative; AlarmManager cancellation is an optimization, not the only correctness mechanism.
5. Every rescheduling entry point must skip an already-decided candidate occurrence and advance to the next eligible one.
6. ReminderReceiver must re-check the decision immediately before notification display to close cancellation/fire races.
7. Stale notification actions must not create orphan or invalid dose events after a medication/slot is removed or disabled.

## Implementation order

| Phase | Document | Outcome |
| --- | --- | --- |
| 1 | [01-stable-dose-time-identity.md](01-stable-dose-time-identity.md) | Preserve IDs for unchanged dose slots during edit/save. |
| 2 | [02-dose-occurrence-contract.md](02-dose-occurrence-contract.md) | Introduce an explicit occurrence contract and propagate it from alarms/notifications. |
| 3 | [03-transactional-dose-decision.md](03-transactional-dose-decision.md) | One shared, transactional decision operation for UI and notification actions. |
| 4 | [04-decision-aware-scheduling.md](04-decision-aware-scheduling.md) | Scheduler and receivers skip decided occurrences and close delivery races. |
| 5 | [05-medication-detail-ui.md](05-medication-detail-ui.md) | Add Detail route/screen and wire Home/notification/Edit navigation. |
| 6 | [06-verification-rollout.md](06-verification-rollout.md) | Unit/integration/device verification and regression checklist. |

## Primary files expected to change

- `data/local/Daos.kt`
- `data/repository/MedicationRepository.kt`
- `domain/model/MedicationModels.kt`
- new domain/use-case file for dose decisions
- `reminders/ReminderScheduler.kt`
- `reminders/ReminderReceiver.kt`
- `reminders/ReminderActionReceiver.kt`
- `reminders/ReminderNotifications.kt`
- `MainActivity.kt`
- `ui/MedicationViewModel.kt`
- `ui/MedicationApp.kt`
- English and Simplified Chinese strings
- focused tests under `app/src/test`

## Non-goals

- Replacing the existing medication editor.
- Redesigning dose history.
- Adding cloud sync/account semantics.
- Changing reminder privacy/redaction behavior unrelated to Detail navigation.
- Introducing a new Room schema solely for this feature.

## Completion criteria

The feature is complete only when a pre-alarm Taken/Skipped decision survives `scheduleAll()`, app relaunch, boot/package recovery, time/time-zone rescheduling, unrelated medication edits, and an alarm fire racing with the decision; notification-originated actions must always apply to the exact occurrence represented by that notification.
