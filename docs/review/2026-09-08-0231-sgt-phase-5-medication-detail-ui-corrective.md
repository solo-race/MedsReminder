# 2026-09-08 02:31 SGT — Phase 5 Medication Detail UI Corrective Review

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed implementation commit / HEAD: `823bb68e339ca250a30b66c2da7c8853136b5f21`

Previous review: `2026-09-08-0128-sgt-phase-5-medication-detail-ui.md`, disposition `CHANGES REQUIRED`; Phase 5 gate not passed.

Feature phase: Phase 5 — Medication Detail UI

Review type: corrective implementation Review Gate.

## Scope

This review independently verifies the corrected Phase 5 implementation after the 01:28 SGT blocking finding. It rechecks the Phase 5 plan, architecture contract, current branch diff, Detail state lifecycle, explicit notification occurrence handling, Home-derived occurrence handling, shared Taken/Skipped mutation path, stale-action behavior, and exact-HEAD CI. The corrective implementation is compared with reviewed Phase 5 commit `27fd89f272959fa6f1a48c16bde8249ce73cf6a5`. No Phase 6 development is performed in this review run.

## Verification evidence

The corrective code changes are limited to `MedicationDetailScreen.kt` plus focused `MedicationDetailLogicTest.kt` coverage. `DetailDoseStateKey` now contains the current `MedicationPlan?` and explicit `DoseOccurrence?`, and the remembered mutable Detail dose state is keyed by that value rather than only medication ID plus explicit occurrence. Because `MedicationPlan`, `Medication`, `MedicationSchedule`, `DoseTime`, and `DoseOccurrence` are data/value classes, relevant same-ID plan edits change key equality and recreate the remembered state.

`produceState` is already keyed by `plan` and `explicitOccurrence`; after invalidation it re-resolves the state. Explicit notification occurrences are revalidated against enabled medication/slot, medication ID, zone, weekday, and exact slot time. Home-originated Detail re-runs `nextActionableOccurrence()` for the changed plan. `LaunchedEffect(initialState)` now installs a newly resolved non-null state instead of preserving the previous state solely because it was already initialized.

Focused regression coverage proves that disabling the medication, changing dose time, removing the occurrence weekday, or changing the schedule zone all alter the Detail state key and make the old explicit occurrence incompatible with the edited plan. Existing tests continue to cover deleted/disabled slots, multiple daily slots, wrong medication identity, explicit occurrence priority, and stale schedule semantics.

GitHub Actions CI Run #43 (`34150158044`) ran against exact HEAD `823bb68e339ca250a30b66c2da7c8853136b5f21` and completed successfully. Its build job reports success for `unit tests, lint, and debug build`.

The corrective diff does not modify Room persistence, dose-decision transaction semantics, scheduler rebuild/reschedule logic, AlarmManager integration, reminder receiver guards, notification occurrence serialization, or system-event recovery paths. Phase 4 correctness invariants therefore remain structurally unchanged by this correction.

## Findings

### LOW / NON-BLOCKING — Navigation coverage remains source-contract based

`MedicationDetailNavigationContractTest` still protects Home → Detail, notification → Detail, Detail → editor, and the shared decision path primarily through source assertions rather than runtime Compose navigation semantics. The corrected state-lifecycle defect is covered at logic/value level, not through an instrumented Edit → back-stack → Detail scenario.

This does not block Phase 5 because the relevant runtime wiring is directly reviewable, the state invalidation mechanism is deterministic and exact-HEAD CI is green. Phase 6 should add runtime/device navigation evidence where practical.

### LOW / NON-BLOCKING — Existing decided explicit occurrence still renders generic decided state initially

For an explicit occurrence, Detail asks only whether a decision exists; it does not read the persisted `TAKEN` versus `SKIPPED` status into `decidedStatus` during initial rendering. The actions are disabled correctly and persistence/idempotence semantics are unaffected, but the UI may show the generic already-decided copy rather than the exact persisted state until a decision result is produced in the current Detail session.

This remains a presentation limitation and does not block the Phase 5 correctness contract. Phase 6 UX verification can decide whether exact persisted status should be surfaced through an existing/read-only repository API.

### LOW / NON-BLOCKING — Home Detail occurrence resolution uses the destination-entry `now`

`MedicationDetailScreen` remembers `Instant.now()` for the lifetime of the mounted destination. A plan edit causes occurrence re-resolution, but Home-originated re-resolution still uses that original instant. A long-lived Detail/Edit session that crosses a dose boundary can therefore continue to treat the boundary-relative occurrence as relevant until the destination is recreated or another state transition occurs.

The shared transactional decision layer still validates stale writes, and notification-originated flows preserve the explicit occurrence identity. This is not a demonstrated persistence/scheduling defect and does not invalidate the corrective fix, but Phase 6 device/UX verification should exercise long-lived Detail across a dose boundary and determine whether current/upcoming presentation needs a live clock or lifecycle refresh.

## Correctness assessment

**PASS WITH NON-BLOCKING FINDINGS.** The previous HIGH blocker is corrected: same-medication plan edits now invalidate the remembered Detail dose/action state and force re-resolution/revalidation before actions are presented. Old explicit notification occurrences become stale after relevant plan edits, while Home-originated Detail obtains a newly resolved candidate for the changed plan. Taken/Skipped continue to flow through the shared transactional dose-decision use case, and no Phase 3/4 correctness path was weakened.

## Architecture assessment

Phase 5 remains within the intended architecture: Detail is read-only, existing editor ownership is preserved, explicit reminder occurrence identity remains authoritative, Home uses the decision-aware occurrence resolver, and mutation stays in the shared business layer. The correction is local to UI state invalidation and introduces no schema, persistence, scheduler, receiver, or notification-contract change.

## Test assessment

Exact-HEAD CI #43 is successful for unit tests, lint, and debug assembly. The new regression directly covers the plan-semantic categories named by the blocking review: enabled state, dose time, weekday, and schedule zone. Existing stale occurrence and multi-slot tests remain green. Instrumented navigation/back-stack and real-device timing/system-event evidence remain appropriate Phase 6 work and are non-blocking for this Phase 5 gate.

## Disposition

**PASS WITH NON-BLOCKING FINDINGS**

Blocking findings: 0.

Non-blocking findings: 3.

Phase 5 Review Gate: **PASSED**.

The next execution may treat Phase 5 as gate-passed at reviewed implementation HEAD `823bb68e339ca250a30b66c2da7c8853136b5f21` provided no later substantive Phase 5 code change invalidates this review. It may update necessary phase/plan status and begin Phase 6 according to the feature plan, but must stop if Phase 6 reaches its completion criteria in that execution and leave Phase 6 review to a later independent run.