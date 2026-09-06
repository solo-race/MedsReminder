# Phase 1 — Stable Dose-Time Identity

## Problem

`RoomMedicationRepository.saveMedication()` currently deletes all `dose_times` for a schedule and reinserts them. Even a note/photo/name-only edit therefore changes every `doseTimeId`. Existing dose decisions are keyed to the old ID, so a same-day pre-alarm decision can be bypassed after an unrelated edit.

## Objective

Preserve the existing `DoseTimeEntity.id` for every unchanged logical slot.

## Steps

1. Add DAO support to read current dose times for a schedule and update/delete individual rows as needed.
2. In `saveMedication()`, diff desired times against existing rows by normalized `minuteOfDay`.
3. Keep rows whose `minuteOfDay` still exists; update only mutable fields such as `enabled` if required.
4. Insert only newly added times.
5. Delete only removed times.
6. Keep returned `MedicationPlan.times` ordering unchanged.
7. Do not change the Room schema or primary-key definition.

## Edge cases

- Duplicate desired times must still collapse to one slot, matching current `draft.times.distinct()` behavior.
- Reordering times in the editor must not create new IDs.
- Editing only medication metadata must leave all dose IDs untouched.
- Removing and later re-adding a time is allowed to create a new ID; identity preservation applies to continuously existing slots.

## Tests

- Metadata-only edit preserves all doseTime IDs.
- Reordered input preserves IDs.
- Add one time preserves existing IDs and creates exactly one new ID.
- Remove one time deletes only that ID.
- Duplicate input does not create duplicate rows.

## Exit criteria

Subsequent phases may rely on `doseTimeId` as a stable identity for a continuously existing schedule slot.
