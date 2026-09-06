# Phase 4 — Decision-aware Scheduling

## Problem

`scheduleAll()` currently schedules the next wall-clock occurrence for every active dose without checking existing decisions. `ReminderReceiver` also posts immediately when a slot is active, leaving a cancellation/fire race.

## Objective

Make every scheduling and delivery path respect persisted dose decisions.

## Steps

1. Add scheduler logic that calculates a candidate occurrence and queries decision state before registering it.
2. If the candidate is already decided, advance to the following eligible occurrence and repeat until an undecided occurrence is found.
3. Prefer an API that can schedule from a known occurrence boundary so receiver rescheduling does not accidentally re-register the same occurrence.
4. Update `scheduleAll()` to use decision-aware logic for app init, save/delete recovery, boot/package replacement, time changes, and time-zone changes.
5. In `ReminderReceiver`, revalidate slot activity and decision state immediately before `showReminder()`.
6. If the fired occurrence is already decided, do not display it; schedule the following occurrence.
7. After a normal fire/display, schedule the following occurrence using the fired `scheduledFor` as the advancement boundary rather than relying only on `Instant.now()`.
8. Keep `ReminderDismissReceiver` consistent with the same decision semantics.

## Race cases to close

- Decision recorded just before alarm dispatch.
- Alarm dispatch begins while UI action is cancelling it.
- App restart after an early decision.
- Boot/package/time/time-zone rescheduling after an early decision.
- OEM-delayed receiver execution after the nominal scheduled time.

## Tests

- 07:30 Taken for 08:00 prevents 08:00 registration after `scheduleAll()`.
- Relaunch/reschedule still skips the decided occurrence.
- Receiver suppresses an already-decided fired occurrence.
- Receiver advances exactly once to the next occurrence.
- DST gap/overlap cases retain existing NextDoseCalculator semantics.

## Exit criteria

No persisted decided occurrence can create a user-visible reminder through any known rescheduling or receiver path.
