# 2026-09-07 04:57 SGT — Phase 2 Dose Occurrence Contract Review

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed HEAD: `ffe265da4844fdb3da4a8ddc6140b3518b6d73b2`

Feature phase: Phase 2 — Dose Occurrence Contract

Review type: implementation Review Gate.

## Scope

This review evaluates the Phase 2 implementation against `docs/feature-plans/medication-detail-dose-actions/02-dose-occurrence-contract.md`, the current architecture contract, the latest Phase 1 review, and the feature-wide correctness invariants.

Reviewed areas include:

- `DoseOccurrence` and `ScheduledDose.occurrenceAt()`;
- AlarmManager reminder/show intents;
- `ReminderReceiver` occurrence parsing and current-slot validation;
- reminder notification content, Taken, Skipped, and dismiss intents;
- `ReminderActionReceiver` and `ReminderDismissReceiver`;
- `MainActivity` and Compose entry propagation;
- system-event overdue reminder propagation;
- Phase 2 tests;
- branch diff from the Phase 1 reviewed baseline through `ffe265da`;
- GitHub Actions verification for the reviewed HEAD.

## Verification evidence

GitHub Actions CI Run #19 (`34056928361`) ran against reviewed HEAD `ffe265da4844fdb3da4a8ddc6140b3518b6d73b2` and completed with conclusion `success`. Its build job completed the `unit tests, lint, and debug build` step successfully.

The source review confirms that the occurrence contract carries all four required identity fields:

- `medicationId`;
- `doseTimeId`;
- exact `scheduledFor` instant;
- `zoneId`.

AlarmManager reminder intents, alarm-clock show intents, posted-notification content intents, Taken/Skipped actions, and dismiss intents all propagate these values rather than deriving a fired reminder target from `Instant.now()`.

## Findings

### HIGH / BLOCKING — stale notification Taken/Skipped actions can create orphan dose events

`ReminderActionReceiver` parses a full `DoseOccurrence` but then immediately calls `repository.recordDose(...)` without first verifying that the referenced medication and dose slot still exist, are enabled, and remain actionable for the occurrence.

`RoomMedicationRepository.recordDose()` unconditionally inserts/replaces a `DoseEventEntity`. The `dose_events` table has no foreign key to either `medications` or `dose_times`, so deleting or disabling the medication/slot does not make this insert fail safely.

Concrete failure mode:

1. a reminder notification is posted for occurrence O;
2. the medication or dose slot is later deleted/disabled, or the old notification becomes stale after a relevant schedule change;
3. the user taps Taken or Skipped on the stale notification;
4. `ReminderActionReceiver` records O without validating current persistence state;
5. an orphan/invalid dose event can therefore be created.

This violates the feature invariant that stale notification actions must not create orphan or invalid dose events and directly fails Phase 2 steps 6–7 plus the specified test scenario “A stale notification for a deleted slot cannot record a new dose event.”

Required correction: before persistence, route notification actions through a current-state validation boundary that verifies medication/slot ownership, enabled/actionable state, and occurrence applicability. Phase 3 is expected to introduce the shared transactional decision operation, so the clean correction may be to establish that operation there; however Phase 2 cannot pass its Review Gate while the current completed state affirmatively permits the stale-action write. The plan may be revised first if the repository intentionally moves this exit condition into Phase 3, but the invariant itself must not be weakened.

### MEDIUM / BLOCKING — Phase 2 behavioral test coverage does not exercise the propagation or stale-action contracts

The added `DoseOccurrenceTest` verifies only that `ScheduledDose.occurrenceAt()` preserves an exact instant and zone. It does not test the Android intent serialization/parsing paths or the stale-action behavior specified by the Phase 2 plan.

Missing focused coverage includes, at minimum:

- an 08:00 notification opened after 08:00 retains the original 08:00 `scheduledFor`;
- multiple daily slots do not cross-target through request-code/extras handling;
- a deleted/disabled slot cannot be acted on from a stale notification;
- malformed or incomplete occurrence extras fail closed;
- notification action parsing preserves `zoneId` and the exact instant.

The Home-to-Detail test named by the plan cannot be fully exercised until the Detail route exists in Phase 5; that specific deferred UI scenario is not independently blocking here. The currently implementable reminder/action contract, however, needs focused regression coverage before Phase 2 should be treated as stable.

### LOW / NON-BLOCKING — occurrence intent encoding/decoding is duplicated across Android boundaries

The four-field occurrence contract is encoded and decoded separately in `MainActivity`, `ReminderReceiver`, `ReminderActionReceiver`, `ReminderDismissReceiver`, and `ReminderNotifications`. The current field names agree, but duplication increases drift risk as Phase 3–5 add more call sites.

A small centralized intent codec/helper for `DoseOccurrence` would reduce this risk. This is not required to pass the gate if the blocking correctness and test findings are fixed without introducing drift.

### NON-BLOCKING / DEFERRED — final decision-aware scheduling and fire-race correctness remain Phase 4 work

`ReminderReceiver` still schedules the next alarm after firing via `scheduler.schedule(currentDose)`, and the code explicitly notes that Phase 4 will make this path decision-aware. The current Phase 2 implementation therefore does not yet satisfy the feature-wide guarantees for persisted-decision suppression, cancel/fire races, restart/BOOT/time-change rescheduling, or advancing strictly beyond the fired occurrence boundary.

This is expected sequencing rather than a Phase 2 defect, provided Phase 4 implements and tests those invariants before feature completion.

## Correctness assessment

`DoseOccurrence` correctly represents the intended identity and the reviewed alarm/notification transport paths preserve the exact `scheduledFor` instant and `zoneId`. A notification target therefore no longer needs to infer its fired occurrence from the current clock.

However, correctness is incomplete at the action boundary: explicit identity is transported but not validated against persisted current state before `recordDose()`. Because the persistence model permits orphan rows, this is a real stale-action correctness failure rather than only missing defensive validation.

## Architecture assessment

The explicit occurrence model is directionally correct and consistent with the architecture contract. No Room migration was introduced. Phase 1 stable `doseTimeId` remains intact.

The unresolved action-boundary validation means the architecture is not yet safe enough for Phase 3+ to assume every transported occurrence is actionable. The persisted database remains the required correctness source of truth; Android intent contents alone cannot authorize a dose decision.

## Race / persistence / restart / time-zone assessment

Phase 2 adds no new persisted schema and does not itself resolve decision races. Exact `scheduledFor` and `zoneId` are now carried across reminder boundaries, which is necessary for DST overlap/gap and device/manual-zone correctness.

The stale-action blocker is especially relevant after schedule edits, deletion/disable operations, process lifetime changes, or time-zone rescheduling because an old notification can outlive the state that originally made its occurrence valid. The action receiver must fail closed against current persisted state.

BOOT, package replacement, TIME_SET, TIMEZONE_CHANGED, decision-aware rescheduling, and final pre-notification decision suppression remain Phase 4/6 verification obligations.

## Test assessment

CI for reviewed HEAD: **PASS**.

Phase 2 targeted behavioral coverage: **INSUFFICIENT**.

The existing pure model test is useful but does not demonstrate the key Android boundary or stale-action guarantees required by this phase.

## Disposition

**CHANGES REQUIRED**

Blocking findings: 2.

Phase 2 Review Gate: **NOT PASSED**.

Do not begin Phase 3 development under the current state machine. The next development run should remain on Phase 2 / review corrective scope: resolve the stale notification action validation defect and add focused tests for the currently implementable occurrence propagation/action contracts. Once Phase 2 again reaches completion, stop and require a new independent review run before advancing.
