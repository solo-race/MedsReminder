# Phase 3 — Transactional Dose Decision

## Problem

`recordDose()` currently writes directly and local-day dedupe is only a query convention. Separate `hasDecision()` and `recordDose()` calls would race under double taps or concurrent receiver/UI actions.

## Objective

Create one shared business operation that atomically validates and records a decision, then coordinates reminder cleanup and rescheduling.

## Steps

1. Add repository support for a transaction that:
   - validates the medication/slot relationship and active state;
   - computes the schedule-zone local-day range for the supplied occurrence;
   - checks whether a decision already exists for that slot/day;
   - inserts the event only when none exists;
   - returns a result such as `Recorded`, `AlreadyDecided`, or `StaleOccurrence`.
2. Add a shared use-case/service (for example `DoseDecisionUseCase`) used by both Detail and `ReminderActionReceiver`.
3. After a successful or already-existing decision, cancel the visible notification and current slot alarm where appropriate.
4. Trigger decision-aware scheduling for the next eligible occurrence.
5. Keep database state authoritative: correctness must not depend solely on AlarmManager cancellation succeeding.

## Concurrency requirements

- Taken and Skipped actions racing for the same local-day slot produce one persisted decision.
- Repeated notification action delivery is idempotent.
- A stale action after deletion/disable does not create an orphan event.

## Tests

- Concurrent/duplicate actions persist one event.
- Already-decided returns a stable result without creating another event.
- Deleted/disabled slot returns stale/invalid and writes nothing.
- UI and notification receiver paths exercise the same use-case contract.

## Exit criteria

There is exactly one production entry point for dose-decision semantics, independent of presentation surface.
