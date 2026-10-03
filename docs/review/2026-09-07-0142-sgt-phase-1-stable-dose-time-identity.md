# 2026-09-07 01:42 SGT — Phase 1 Stable Dose-Time Identity Review

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed HEAD: `a338931ed7363aa6082fd0b1fb29e31470d50a65`

Implementation commit under review: `90f80f92c9240b025614c6b660bdf30f32699cfd` — `feat: preserve stable dose-time identities`

CI-enablement commit included in reviewed HEAD: `a338931ed7363aa6082fd0b1fb29e31470d50a65` — `ci: run checks on codex feature branches`

Feature phase: Phase 1 — Stable Dose-Time Identity

Review type: implementation Review Gate.

## Scope

This review evaluates whether Phase 1 satisfies `docs/feature-plans/medication-detail-dose-actions/01-stable-dose-time-identity.md` and whether subsequent phases may safely treat `doseTimeId` as the stable identity of a continuously existing schedule slot.

The review covers:

- `DoseTimeDao` changes;
- `RoomMedicationRepository.saveMedication()` reconciliation behavior;
- preservation of existing `DoseTimeEntity.id` values;
- add/remove/reorder/duplicate-time behavior;
- transaction boundaries;
- test coverage;
- GitHub Actions verification for the reviewed HEAD.

## Verification evidence

GitHub Actions CI Run #12 (`34047794565`) executed against reviewed HEAD `a338931ed7363aa6082fd0b1fb29e31470d50a65` on `codex/feature-medication-detail-dose-actions` and completed successfully.

The workflow executed:

```text
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Final CI result: `success`.

The Gradle log reports:

```text
BUILD SUCCESSFUL
55 actionable tasks: 55 executed
```

Therefore the reviewed HEAD has real execution evidence for JVM unit tests, Android lint, Kotlin/Java compilation, and debug APK assembly. The earlier verification-blocked condition caused by CI only running on `master` has been resolved by allowing `push` events on `codex/**` branches.

## Findings

### No blocking correctness findings

Static review did not identify a correctness defect that prevents Phase 1 from satisfying its plan.

`RoomMedicationRepository.saveMedication()` no longer deletes every `dose_times` row and reinserts the complete draft. It now reconciles desired times against existing rows by normalized `minuteOfDay` while remaining inside the existing Room transaction.

For a continuously existing logical slot:

- the existing row is retained;
- its existing primary key / `doseTimeId` remains unchanged;
- metadata-only medication edits do not churn dose-time IDs;
- input reordering does not churn IDs;
- adding another time creates only the new slot;
- removing a time deletes only the removed slot;
- duplicate desired times collapse to one desired slot;
- a matching disabled slot is re-enabled while retaining its ID.

DAO changes support targeted deletion and updates without changing the Room schema or dose-time primary-key definition.

This satisfies the Phase 1 architectural prerequisite identified by the earlier review: later decision and scheduling logic may use `doseTimeId` as stable identity for a slot that continuously remains in the schedule.

### Non-blocking — persistence invariant is not yet covered by a real Room/Repository integration test

The new `DoseTimeReconciliationTest` suite directly tests the pure `reconcileDoseTimes()` function. It covers metadata-only edits, reordered input, adding a time, removing a time, duplicate desired rows, duplicate existing rows, and disabled-slot re-enable behavior.

These tests provide strong coverage of the reconciliation algorithm, and the reviewed source compiles and passes lint/build in CI. However, the tests do not instantiate a Room database, invoke `RoomMedicationRepository.saveMedication()`, then query persisted `dose_times` to assert that the real stored IDs remain unchanged.

This is not blocking for Phase 1 because the repository wiring is straightforward, the unchanged row is not sent through delete/insert, the operation remains transactional, and the phase plan did not make a Room integration test an explicit exit criterion.

Required follow-up before the feature is considered fully verified for merge, preferably no later than Phase 6:

1. metadata-only save preserves all persisted dose-time IDs;
2. adding another slot preserves every pre-existing persisted slot ID and creates exactly one new ID;
3. removing another slot preserves surviving persisted slot IDs.

### Low — CI contains unrelated deprecation warnings

CI succeeds, but the build logs contain existing AGP/Kotlin legacy DSL and GitHub Actions Node runtime deprecation warnings. These warnings are not caused by Phase 1 and do not affect this Review Gate. They should be handled as repository maintenance rather than feature-blocking work.

## Architecture assessment

PASS.

Phase 1 removes the previously identified identity instability without introducing a Room migration. This is the correct architectural direction because dose decisions and reminder suppression in later phases will be keyed by `doseTimeId`; preserving that ID across unrelated edits prevents a recorded decision from becoming detached from the still-existing logical dose slot.

No Phase 1 change conflicts with the feature architecture contract.

## Test assessment

PASS for the Phase 1 Review Gate, with the non-blocking Room persistence integration-test gap recorded above.

Actual CI execution for the reviewed HEAD confirms:

- `testDebugUnitTest` succeeded;
- `lintDebug` succeeded;
- `assembleDebug` succeeded.

The GitHub-only development workflow now has a functioning verification loop for `codex/**` branch commits.

## Disposition

**PASS WITH NON-BLOCKING FINDINGS**

Blocking findings: 0.

Phase 1 Review Gate: **PASSED**.

Phase 1 may be treated as complete. Subsequent development may proceed to Phase 2 — Dose Occurrence Contract, subject to the repository state-machine rule that this archived review remains valid only while no later substantive modification invalidates the reviewed Phase 1 implementation.

The Room/Repository persistence integration-test gap remains tracked as non-blocking verification debt and must not be silently treated as already executed coverage.
