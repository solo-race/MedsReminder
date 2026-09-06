# 2026-09-07 06:58 SGT — Phase 3 Transactional Dose Decision Review

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed HEAD: `6c9f92fe1cb46a84333fdf256bdc3f959831b2e6`

Previous review: `2026-09-07-0624-sgt-phase-2-dose-occurrence-contract.md`, reviewed HEAD `576978412edc00c2958814dd2e2974d9f5354eef`, disposition `PASS WITH NON-BLOCKING FINDINGS`.

Feature phase: Phase 3 — Transactional Dose Decision

Review type: implementation Review Gate.

## Scope

This review determines whether Phase 3 satisfies `03-transactional-dose-decision.md` and may advance to Phase 4. The review covers the production and test changes introduced by `6c9f92fe1cb46a84333fdf256bdc3f959831b2e6`, including:

- the shared `DoseDecisionUseCase` production entry point;
- Room transaction semantics for validation, local-day deduplication, and insertion;
- explicit `Recorded`, `AlreadyDecided`, and `StaleOccurrence` results;
- first-write-wins behavior for concurrent Taken/Skipped actions;
- stale/deleted/disabled occurrence rejection;
- notification receiver wiring through the shared use case;
- post-decision notification/alarm cleanup and scheduling handoff;
- real in-memory Room repository tests and use-case contract tests;
- CI verification for the exact reviewed HEAD.

## Verification evidence

GitHub Actions CI Run #28 (`34064764975`) ran against reviewed HEAD `6c9f92fe1cb46a84333fdf256bdc3f959831b2e6` and completed with conclusion `success`. The repository CI path executes unit tests, lint, and debug assembly.

The reviewed branch HEAD remained `6c9f92fe1cb46a84333fdf256bdc3f959831b2e6` during this review, so the CI result applies to the exact implementation reviewed here.

## Findings

### No blocking correctness findings

The Phase 3 persistence path is consolidated into `RoomMedicationRepository.decideDose()`. The operation runs inside `database.withTransaction`, reloads the current medication/schedule/dose-time state, rejects stale occurrences before writing, derives the schedule-zone local day from the explicit occurrence, checks for an existing decision for the same stable slot/day, and inserts only when no prior decision exists.

The public medication repository no longer exposes the previous direct `recordDose()` or temporary `recordDoseIfOccurrenceActionable()` write paths. The dedicated internal `DoseDecisionRepository` is consumed by `DoseDecisionUseCase`, and `ReminderActionReceiver` calls that use case rather than writing directly. This establishes one production semantic entry point for notification-originated decisions and a presentation-independent entry point for the later Detail UI.

The in-memory Room test exercises duplicate and concurrent Taken/Skipped calls against the real repository transaction. It verifies one `Recorded` result, one `AlreadyDecided` result, matching persisted status, and no write for disabled/deleted stale state. This directly covers the Phase 3 first-write-wins and idempotence requirement in the app's single-process Room model.

### MEDIUM / NON-BLOCKING — decision side-effect integration is not yet exercised against AlarmManager/notification components

`DoseDecisionUseCaseTest` verifies that `Recorded` and `AlreadyDecided` invoke the persisted-decision effect and that `StaleOccurrence` invokes only stale cleanup. Source review confirms `ReminderDoseDecisionEffects` cancels the visible notification, cancels the current slot alarm for persisted decisions, and hands scheduling to `scheduleAfter()`.

There is not yet an integration test that executes the real `ReminderDoseDecisionEffects` against AlarmManager/notification boundaries. This is non-blocking for Phase 3 because the phase's correctness authority is the transactional database decision; cancellation is explicitly an optimization, and Phase 4 is responsible for decision-aware scheduler rebuilds and receiver race closure. This integration debt should be covered during Phase 4 or Phase 6.

### LOW / NON-BLOCKING — local-day uniqueness is enforced transactionally rather than by a dedicated schema constraint

The database schema still has the existing unique index on `(doseTimeId, scheduledForEpochMillis)`, not a unique local-day key. Phase 3 therefore relies on the Room transaction's serialized check-then-insert behavior for same-slot/same-local-day first-write-wins semantics. The real concurrent Room test passes for this application model.

No Room migration should be added merely to duplicate this invariant unless a later requirement introduces a multi-process writer or another concrete failure mode. The feature plan explicitly avoids an unnecessary schema migration.

### NON-BLOCKING / DEFERRED — full decision-aware rescheduling remains Phase 4

`ReminderDoseDecisionEffects` schedules strictly after the handled occurrence boundary via `scheduleAfter()`, which prevents immediate re-registration of the same fired occurrence. It does not yet make every rebuild/reschedule path skip already-decided future candidates, nor does it add the final persisted decision check immediately before notification display. These are explicit Phase 4 obligations and are not blockers for the Phase 3 Review Gate.

### NON-BLOCKING / DEFERRED — Detail UI consumption of the shared use case remains Phase 5

The Phase 3 use case is presentation-independent and ready for both notification and UI callers, but the read-only medication Detail route does not yet exist. Therefore the plan test bullet requiring UI and notification surfaces to exercise the same contract cannot be fully instantiated until Phase 5. No competing UI decision write path exists today, so this is deferred rather than a Phase 3 blocker.

## Correctness assessment

PASS.

The persisted database decision is authoritative. Validation, local-day deduplication, first-write-wins semantics, and insertion occur in one Room transaction. Repeated or concurrent Taken/Skipped delivery for the same stable slot/local day produces one persisted status, while stale occurrences fail closed.

## Architecture assessment

PASS.

Phase 3 removes the temporary Phase 2 direct repository write API and introduces a shared `DoseDecisionUseCase` boundary with explicit result semantics. The design preserves the existing Room schema and cleanly separates authoritative persistence from reminder cleanup/rescheduling effects. Phase 4 can now make scheduling and receiver delivery decision-aware without duplicating decision semantics.

## Test assessment

PASS for the Phase 3 Review Gate, with the non-blocking side-effect integration gap recorded above.

CI for reviewed HEAD: `success`.

Focused tests include a real in-memory Room concurrency test, duplicate-decision test, stale deleted/disabled state tests, and use-case result/effect routing tests.

## Disposition

**PASS WITH NON-BLOCKING FINDINGS**

Blocking findings: 0.

Phase 3 Review Gate: **PASSED**.

Phase 3 may be treated as complete. The next development execution may proceed to Phase 4 — Decision-Aware Scheduling, provided no substantive Phase 3 implementation changes occur after this reviewed HEAD without another review.
