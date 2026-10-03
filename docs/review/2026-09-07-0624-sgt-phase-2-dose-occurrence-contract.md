# 2026-09-07 06:24 SGT — Phase 2 Dose Occurrence Contract Re-review

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed HEAD: `576978412edc00c2958814dd2e2974d9f5354eef`

Previous Phase 2 review: `2026-09-07-0457-sgt-phase-2-dose-occurrence-contract.md`, reviewed HEAD `ffe265da4844fdb3da4a8ddc6140b3518b6d73b2`, disposition `CHANGES REQUIRED`.

Feature phase: Phase 2 — Dose Occurrence Contract

Review type: implementation Review Gate re-review after corrective development.

## Scope

This review determines whether the two blocking findings from the 04:57 SGT Phase 2 review have been resolved and whether Phase 2 may advance to Phase 3 under the repository state machine.

The review covers the corrective changes from `ffe265da` through `57697841`, including:

- stale notification Taken/Skipped action validation;
- repository transaction boundaries for occurrence validation and event insertion;
- current medication/schedule/slot ownership and enabled-state checks;
- schedule zone, weekday, wall-clock time, and exact scheduled instant validation;
- action occurrence parsing and malformed-input fail-closed behavior;
- notification content navigation occurrence parsing;
- delayed notification opening preserving the original scheduled instant;
- focused regression tests added after the prior review;
- GitHub Actions verification for the reviewed HEAD.

## Verification evidence

GitHub Actions CI Run #26 (`34063496707`) ran against reviewed HEAD `576978412edc00c2958814dd2e2974d9f5354eef` and completed with conclusion `success`.

The build job reports the `unit tests, lint, and debug build` step as `success`. This is the repository CI path that executes `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.

The branch HEAD remained `576978412edc00c2958814dd2e2974d9f5354eef` during this review, so the CI result applies to the exact implementation being reviewed.

## Prior blocking finding resolution

### RESOLVED — stale notification actions no longer write orphan/invalid dose events

The previous review found that `ReminderActionReceiver` parsed an explicit `DoseOccurrence` but then called unconditional `recordDose()`, allowing a stale notification to create a dose event after its medication or dose slot had been deleted, disabled, or otherwise changed.

The corrective implementation now routes notification actions through `recordDoseIfOccurrenceActionable()`.

That repository operation runs inside `database.withTransaction` and reads the current medication, schedule, and dose-time row before insertion. It fails closed unless all of the following remain true:

- medication exists, is enabled, and matches `occurrence.medicationId`;
- schedule still belongs to that medication;
- dose slot exists, is enabled, matches `occurrence.doseTimeId`, and belongs to that schedule;
- the schedule's current zone equals `occurrence.zoneId`;
- the occurrence local date is still enabled by the schedule weekday mask;
- the dose slot's current wall-clock time resolves in that zone to the exact `occurrence.scheduledFor` instant.

Only after this validation succeeds does the same transaction insert the dose event.

The receiver cancels the notification after handling the action and only triggers `scheduleAll()` after a successful record.

This closes the orphan-event defect identified in the previous review and establishes the required persisted-current-state validation boundary for stale notification actions.

### RESOLVED — focused Phase 2 occurrence/action regression coverage added

The previous review found that Phase 2 tests covered only the domain `occurrenceAt()` model and did not exercise the actionable stale-state or Android-boundary identity contracts.

Corrective tests now cover:

- current enabled occurrence accepted;
- deleted or disabled medication rejected;
- deleted, disabled, or foreign dose slot rejected;
- schedule zone, weekday, or wall-clock time changes invalidate the old occurrence;
- multiple daily slots cannot cross-target;
- DST gap resolution uses the same `LocalDateTime.atZone()` semantics as the scheduler;
- action parsing preserves medication ID, dose-time ID, exact instant, and zone;
- malformed/incomplete action identity fails closed;
- delayed action still targets the original scheduled instant;
- notification/MainActivity navigation parsing preserves the original exact instant and zone;
- malformed/incomplete notification navigation identity fails closed.

The specific Home-to-Detail behavior remains deferred because the read-only Detail route is a Phase 5 deliverable, as already accepted by the previous review.

## Findings

### No blocking correctness findings

No new Phase 2 correctness blocker was identified in the corrective implementation.

The explicit `DoseOccurrence` identity remains `medicationId + doseTimeId + scheduledFor + zoneId`. Alarm and notification paths continue to transport the exact fired occurrence rather than recomputing it from the current clock. The corrective changes strengthen, rather than weaken, the persisted-state boundary.

### MEDIUM / NON-BLOCKING — stale-action persistence behavior still lacks a real Room repository integration test

The new validation tests directly exercise the pure `isOccurrenceActionable()` predicate, and source review confirms that `recordDoseIfOccurrenceActionable()` calls that predicate and inserts only after it succeeds in the same Room transaction.

However, there is still no test that instantiates the Room database, invokes `RoomMedicationRepository.recordDoseIfOccurrenceActionable()` for a stale/deleted slot, and then queries `dose_events` to prove that no row was persisted.

This is not blocking for the Phase 2 gate because:

- the transaction wiring is direct and visible;
- the fail-closed predicate is covered by focused JVM tests;
- the receiver is visibly wired to the guarded repository operation;
- the exact reviewed source compiles and passes unit tests, lint, and debug assembly in CI.

The missing integration test remains verification debt and should be added before final feature merge, preferably during Phase 3 when the shared transactional decision operation replaces this temporary Phase 2 boundary or during Phase 6 verification.

### LOW / NON-BLOCKING — occurrence extras encoding/decoding remains duplicated

Occurrence identity is still encoded/decoded in several Android boundaries rather than through one centralized codec/helper. The newly extracted MainActivity parser and ReminderActionReceiver parser reduce testability risk but do not eliminate duplication across alarm, notification, receiver, dismiss, and activity paths.

No field drift was found in the reviewed implementation. Centralizing the codec remains optional cleanup unless later phases add enough call sites to make drift materially risky.

### NON-BLOCKING / DEFERRED — decision-aware scheduling and final receiver decision guards remain Phase 4

Phase 2 still intentionally does not solve decision-aware `scheduleAll()`, final persisted-decision checks immediately before reminder display, cancel/fire races, or advancement strictly after the fired occurrence boundary.

These remain explicit Phase 4 obligations and are not Phase 2 regressions.

## Correctness assessment

PASS.

The prior stale-action correctness failure is resolved. An intent's occurrence identity is no longer sufficient authority to write a dose event: the repository checks current persisted medication/schedule/slot state inside the write transaction and rejects stale occurrences.

Exact scheduled instants and zones remain preserved across action and notification navigation parsing, including delayed handling after the original reminder time.

## Architecture assessment

PASS.

Phase 2 now provides a safe explicit occurrence transport contract for subsequent phases. It does not introduce a Room migration and does not weaken Phase 1 stable `doseTimeId` semantics.

The guarded Phase 2 repository write is acceptable as a corrective boundary, but Phase 3 should still replace/direct both UI and notification actions through the planned shared transactional dose-decision operation so local-day idempotence, first-write-wins behavior, and common result semantics are implemented once.

## Test assessment

PASS for the Phase 2 Review Gate, with the non-blocking Room integration gap recorded above.

CI for reviewed HEAD: `success`.

Focused JVM coverage now exercises the stale-state validation and exact occurrence parsing contracts that were missing in the prior review.

## Disposition

**PASS WITH NON-BLOCKING FINDINGS**

Blocking findings: 0.

Phase 2 Review Gate: **PASSED**.

Phase 2 may be treated as complete. The next development execution may proceed to Phase 3 — Transactional Dose Decision, provided no substantive Phase 2 implementation changes occur after this reviewed HEAD without another review.

The next phase must not treat the temporary `recordDoseIfOccurrenceActionable()` API as the final decision architecture. Phase 3 remains responsible for the shared transactional operation, local-day idempotence, deterministic concurrent Taken/Skipped behavior, and explicit decision results.
