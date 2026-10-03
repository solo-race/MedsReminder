# Phase 4 — Decision-aware Scheduling

## Problem

`scheduleAll()` currently schedules the next wall-clock occurrence for every active dose without checking existing decisions. `ReminderReceiver` also posts immediately when a slot is active, leaving a cancellation/fire race.

The Phase 4 Review Gate additionally found that reconstructing decision local-day membership from `scheduledFor` using the schedule's current zone is not stable across schedule-zone changes. The logical local day used for idempotence must therefore be persisted with the decision.

## Objective

Make every scheduling and delivery path respect persisted dose decisions, including after device/manual zone changes that move the same logical wall-clock dose day across a UTC date boundary.

## Steps

1. Persist the decision's logical schedule-local day as a stable key on `dose_events`; add the minimal Room migration required for the new field and backfill legacy events deterministically at upgrade.
2. Query decision state by `doseTimeId + persisted logical local day`, not by reinterpreting a historical decision instant in the candidate's current zone.
3. Add scheduler logic that calculates a candidate occurrence and queries decision state before registering it.
4. If the candidate is already decided, advance to the following eligible occurrence and repeat until an undecided occurrence is found.
5. Prefer an API that can schedule from a known occurrence boundary so receiver rescheduling does not accidentally re-register the same occurrence.
6. Update `scheduleAll()` to use decision-aware logic for app init, save/delete recovery, boot/package replacement, time changes, and time-zone changes.
7. In `ReminderReceiver`, revalidate slot activity and decision state immediately before `showReminder()`.
8. If the fired occurrence is already decided, do not display it; schedule the following occurrence.
9. After a normal fire/display, schedule the following occurrence using the fired `scheduledFor` as the advancement boundary rather than relying only on `Instant.now()`.
10. Keep `ReminderDismissReceiver` consistent with the same decision semantics.

## Race cases to close

- Decision recorded just before alarm dispatch.
- Alarm dispatch begins while UI action is cancelling it.
- App restart after an early decision.
- Boot/package/time/time-zone rescheduling after an early decision.
- Device/manual zone change across the International Date Line after an early decision.
- OEM-delayed receiver execution after the nominal scheduled time.

## Tests

- 07:30 Taken for 08:00 prevents 08:00 registration after `scheduleAll()`.
- Relaunch/reschedule still skips the decided occurrence.
- A decision made for local day D remains decided for local day D after changing between zones whose UTC mapping crosses a calendar-date boundary.
- A second Taken/Skipped action for that same logical day after the zone change remains idempotent.
- Receiver suppresses an already-decided fired occurrence.
- Receiver advances exactly once to the next occurrence.
- DST gap/overlap cases retain existing NextDoseCalculator semantics.

## Exit criteria

No persisted decided occurrence can create a user-visible reminder through any known rescheduling or receiver path, including schedule-zone transitions that change the candidate occurrence's UTC date.
