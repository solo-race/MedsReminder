# Phase 2 — Dose Occurrence Contract

## Problem

Reminder notification content intents currently carry only `medicationId`. If Detail recalculates the next dose after a reminder has already fired, the action may target a later slot or later day instead of the occurrence represented by the notification.

## Objective

Represent an actionable dose occurrence explicitly and propagate it across AlarmManager, notification content intents, navigation, and UI actions.

## Proposed model

Introduce a small domain value such as:

```kotlin
data class DoseOccurrence(
    val medicationId: Long,
    val doseTimeId: Long,
    val scheduledFor: Instant,
    val zoneId: ZoneId,
)
```

The exact final location/name may change during implementation, but the identity contract must remain explicit.

## Steps

1. Add the domain occurrence type.
2. Treat Home navigation as the only path allowed to derive an upcoming occurrence from the current schedule and current time.
3. Extend reminder/alarm content intents to carry `doseTimeId` and `scheduledFor` in addition to medicationId.
4. Extend `MainActivity`/Compose entry state so Detail can receive an explicit occurrence when opened from a reminder.
5. If an explicit occurrence is present, Detail must use it rather than recomputing from `Instant.now()`.
6. Validate that the occurrence still belongs to the medication/slot before enabling actions.
7. Define fallback behavior for a stale notification: show medication Detail if the medication remains, but disable occurrence actions when the referenced slot is no longer actionable.

## Tests

- A notification fired for 08:00 and opened at 08:05 still targets 08:00.
- Multiple daily times do not cross-target.
- A stale notification for a deleted slot cannot record a new dose event.
- Home-opened Detail computes the next eligible upcoming occurrence.

## Exit criteria

All dose-action call sites receive an explicit occurrence identity; fired notifications never infer their action target from the current clock.
