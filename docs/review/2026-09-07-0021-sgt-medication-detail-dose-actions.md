# 2026-09-07 00:21 SGT — Medication Detail + Pre-alarm Dose Decisions Review

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Base reviewed commit: `master` `ebafa559489c34bb7ffabcd66a0a1feede959aad`

Review type: architecture and implementation-readiness review.

## Scope

The review covered navigation, reminder notification routing, dose-event persistence, dose-time identity, AlarmManager scheduling, receiver race handling, restart/system-event recovery, and the proposed Taken/Skipped-before-alarm flow.

## Findings

### High — `doseTimeId` is not stable across ordinary edits

`RoomMedicationRepository.saveMedication()` deletes all existing dose-time rows for a schedule and reinserts the draft times. Because `dose_times.id` is auto-generated, even a note/photo/name-only edit can replace a logical slot with a new `doseTimeId`.

This breaks any scheduler suppression policy keyed by `doseTimeId`: a decision recorded against the old slot can become invisible after an unrelated edit, allowing the same logical dose to be scheduled again.

Required disposition: preserve IDs for unchanged dose slots. Diff old and new times, retain unchanged rows, insert only new slots, and delete only removed slots. No Room schema migration is required for this approach.

### High — notification navigation loses the exact occurrence identity

Reminder action PendingIntents carry `medicationId`, `doseTimeId`, and `scheduledFor`, but the notification content PendingIntent currently passes only `medicationId` to `MainActivity`.

A notification opened after its scheduled time cannot safely recompute the target with `nextOccurrence(now)`, because that calculation can select the next later slot/day instead of the occurrence that generated the notification.

Required disposition: introduce an explicit occurrence contract containing at least `medicationId`, `doseTimeId`, `scheduledFor`, and `zoneId`. Notification and alarm navigation must propagate the exact occurrence; explicit occurrence context overrides recomputation.

### High — local-day deduplication is not a database write invariant

The existing v0.004 behavior checks decision existence by schedule-zone local day in selected receiver/recovery paths, but `dose_events` remains uniquely constrained only by exact `(doseTimeId, scheduledFor)` and `recordDose()` performs `REPLACE`.

The shared decision operation therefore cannot assume Room enforces local-day idempotence. It must perform the local-day existence check and insert atomically in one Room transaction. First-write-wins is the safer default unless correction semantics are explicitly introduced.

### Medium-high — `scheduleAll()` can recreate a pre-decided alarm

Current `scheduleAll()` iterates active scheduled doses and calls `schedule(dose)`, while `schedule(dose)` computes the next occurrence from `Instant.now()` without consulting persisted dose decisions.

Therefore `recordDose -> cancel -> scheduleAll` can recreate the current day's still-future alarm. App initialization and system-event recovery also call `scheduleAll()`, so restart, boot, package replacement, time changes, or time-zone changes can independently resurrect it.

Required disposition: make scheduling decision-aware and advance past decided candidate occurrences.

### Medium-high — ReminderReceiver lacks a final decision guard

`ReminderReceiver` currently validates that the medication/slot is active and then posts the reminder. It does not check whether the occurrence/local day has already been decided.

Alarm cancellation alone cannot close the race where an alarm is already being delivered while the user records Taken/Skipped.

Required disposition: immediately before notification display, consult persisted decision state. If already decided, suppress display and still schedule the next eligible occurrence.

### Medium — fire-path rescheduling should advance relative to the fired occurrence

`ReminderReceiver` calls `scheduler.schedule(currentDose)` after delivery, and that scheduler derives the next trigger from current wall-clock time. If dispatch occurs slightly early, the same occurrence can theoretically still qualify and be scheduled again.

Required disposition: provide a scheduling operation that computes the next occurrence strictly after the received `scheduledFor` when advancing from a fired alarm.

### Medium — stale notification actions can create invalid/orphan dose events

`ReminderActionReceiver` records the event without first validating that the medication and dose slot are still current/active. `dose_events` has no foreign keys to medication or dose-time rows.

Required disposition: the shared decision layer validates the target occurrence/slot before writing. Deleted/disabled/stale targets are ignored deterministically.

## Revised architecture

The implementation should use the following sequence and boundaries:

1. Stable dose-slot identity for unchanged scheduled times.
2. Explicit `DoseOccurrence`/`ActionableDose` identity across UI, notifications, alarms, and receivers.
3. Transactional shared dose-decision use case/repository operation used by Detail UI and notification actions.
4. Database decision state as the source of truth; alarm/notification cancellation follows the successful decision write.
5. Decision-aware scheduler for all rebuild paths.
6. Receiver guard immediately before display, followed by next-occurrence advancement.
7. Read-only Medication Detail route with Home opening a computed upcoming occurrence and notification navigation opening its exact occurrence; Edit continues to reuse `EditMedicationScreen`.

## Required verification

At minimum, tests must cover:

- 07:30 Taken/Skipped for an 08:00 dose prevents the 08:00 alarm/notification.
- Relaunch before 08:00 does not recreate the decided reminder.
- Boot, package replacement, time change, and time-zone change rebuilds do not recreate it.
- Decision racing alarm delivery is suppressed by the receiver guard.
- Opening an 08:00 notification after 08:00 still targets the 08:00 occurrence.
- Multiple daily dose slots select the intended slot and occurrence.
- Unrelated medication edits preserve the unchanged slot ID and decision suppression.
- Adding/removing another time preserves IDs for unchanged slots.
- Taken/Skipped double-tap or concurrent actions are deterministic and idempotent.
- DST gap/overlap and manual/device zone modes remain correct.
- Deleted/disabled medication or slot rejects stale notification actions.
- Process death preserves decision correctness.

## Disposition

Implementation is feasible without a Room schema migration, but the feature plan must treat stable slot identity and exact occurrence propagation as prerequisites rather than optional refinements. Estimated implementation complexity is approximately 7/10 because correctness spans Room transactions, navigation, AlarmManager scheduling, broadcast receivers, and temporal race handling.
