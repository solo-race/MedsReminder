# 2026-09-08 01:28 SGT — Phase 5 Medication Detail UI Review

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed implementation commit / HEAD: `27fd89f272959fa6f1a48c16bde8249ce73cf6a5`

Previous review: `2026-09-07-2212-sgt-phase-4-decision-aware-scheduling-corrective.md`, disposition `PASS WITH NON-BLOCKING FINDINGS`; Phase 4 gate passed.

Feature phase: Phase 5 — Medication Detail UI

Review type: implementation Review Gate.

## Scope

This review verifies the complete Phase 5 implementation against `05-medication-detail-ui.md`, the feature contract, architecture index, latest Phase 4 review, current branch diff, source wiring, focused Phase 5 tests, and exact-HEAD CI. The review covers Home → Detail, notification → Detail with explicit occurrence identity, Detail → existing editor, read-only medication presentation, Taken/Skipped routing through the shared decision path, stale-occurrence behavior, localization, and regression risk at the Phase 4 correctness boundary. No Phase 6 implementation is performed in this review run.

## Verification evidence

Phase 5 adds `medication/{medicationId}` routing, redirects Today cards to Detail, routes notification targets to Detail while retaining `LocalNotificationDoseOccurrence`, keeps the existing editor as the only edit surface, and adds a read-only Detail screen with medication metadata, schedule/zone presentation, occurrence presentation, Taken/Skipped/Edit actions, and English/Simplified Chinese strings.

`MedicationViewModel.nextActionableOccurrence()` reuses the decision-aware scheduler candidate logic and persisted repository decision lookup. `MedicationViewModel.decideDose()` delegates Taken/Skipped to the existing shared `doseDecisionUseCase`; therefore Phase 5 does not introduce a second mutation path.

Focused tests cover explicit-occurrence priority, stale occurrences after slot/medication/schedule changes, multi-slot targeting, Home/notification/Edit navigation wiring, and the shared decision call path. Commit `27fd89f272959fa6f1a48c16bde8249ce73cf6a5` corrects the navigation contract test so Taken/Skipped assertions inspect `MedicationDetailScreen.kt` rather than `MedicationApp.kt`.

GitHub Actions CI Run #40 (`34145791616`) ran against exact HEAD `27fd89f272959fa6f1a48c16bde8249ce73cf6a5` and completed with conclusion `success`. The repository CI path executes unit tests, lint, and debug assembly.

## Findings

### HIGH / BLOCKING — Detail dose/action state is not invalidated when the same medication plan changes

`MedicationDetailScreen` stores mutable action state with `remember(plan?.medication?.id, explicitOccurrence)`. Its `LaunchedEffect(initialState)` only assigns the newly produced state when the existing state is `null`. Consequently, a plan update that keeps the same medication ID does not replace the previous `DetailDoseState`.

This is reachable through the normal Phase 5 flow: Detail → Edit uses the existing editor; a successful save calls `onBack`, returning to the existing Detail back-stack entry. If the edit changes dose time, weekdays, schedule zone, or enabled state, `plans` updates with the same medication ID, but the remembered Detail state can retain the pre-edit occurrence and `actionable` value. The UI can therefore continue to offer Taken/Skipped for an occurrence that is no longer valid under the current plan.

The shared dose-decision use case remains the correctness boundary and should reject the stale occurrence, so this finding does not demonstrate an orphan/incorrect persisted dose event. However, Phase 5 explicitly requires Taken/Skipped to be disabled when no valid actionable occurrence exists, and stale notification/action state must not be presented as valid. The implementation therefore does not satisfy the Phase 5 UI state contract.

Required correction: invalidate/recompute Detail dose state whenever plan semantics relevant to occurrence validity change, not only when medication ID changes. A suitable implementation can re-resolve state from `plan` plus `explicitOccurrence` on each relevant plan update: notification-originated explicit occurrences should be revalidated and marked stale if the edited plan no longer matches; Home-originated Detail should resolve a fresh next actionable occurrence. Preserve the shared decision use case as the only mutation path. Add focused regression coverage for returning from Edit after changing time/weekday/zone and after disabling the medication, proving stale old actions are disabled without requiring a click first.

### LOW / NON-BLOCKING — Navigation tests assert source text rather than runtime navigation behavior

`MedicationDetailNavigationContractTest` now passes and correctly scopes its source assertions, but it verifies string presence in Kotlin source rather than exercising a Compose navigation graph or UI semantics. This protects against accidental rewiring at a coarse level but cannot prove back-stack behavior, runtime argument delivery, or click behavior.

This does not independently block Phase 5 because the implementation wiring is directly reviewable, exact-HEAD CI is green, and Phase 6 is explicitly reserved for broader integration/device verification. Runtime navigation tests or device smoke evidence should be added in Phase 6 where practical.

### LOW / NON-BLOCKING — Existing decided occurrence does not recover its persisted Taken/Skipped status for initial rendering

For an explicit occurrence, Detail calls `isDoseDecided()` and initializes only a Boolean `decided`; `decidedStatus` remains null, so an already-decided occurrence renders the generic already-decided state rather than the persisted Taken or Skipped label until a decision is made during the current Detail session.

Actions are correctly disabled and persistence semantics are unaffected. This is a presentation limitation, not a correctness blocker, but Phase 6 UX verification should decide whether the screen should surface the exact persisted status and, if so, use a read API that returns the existing decision rather than adding a second decision path.

## Correctness assessment

**CHANGES REQUIRED.** Phase 4 persistence/scheduling correctness remains protected because all actual dose writes still flow through the shared transactional decision use case and stale occurrences are rejected there. The blocker is at the Phase 5 UI contract boundary: actionability can remain stale after a same-ID plan edit and therefore does not always reflect current persisted plan state before the user acts.

## Architecture assessment

The overall Phase 5 structure is sound: Detail is read-only, editor ownership is not duplicated, explicit notification occurrence identity is preserved, Home uses the decision-aware occurrence resolver, and Taken/Skipped reuse the existing business layer. No new Room schema change is introduced. The required correction should remain local to Detail state invalidation/re-resolution and tests; it does not require changing the Phase 3/4 persistence or scheduling architecture.

## Test assessment

Exact-HEAD CI #40 is `success`. Existing tests cover principal stale-occurrence predicates and navigation/source contracts, but there is no regression proving that an already-mounted Detail destination updates its actionability when the same medication plan changes after Edit. That missing scenario directly corresponds to the blocking finding and must be added with the correction. Phase 6 remains responsible for migration-focused testing and real Android system-event/AlarmManager/device evidence identified by earlier reviews.

## Disposition

**CHANGES REQUIRED**

Blocking findings: 1.

Non-blocking findings: 2.

Phase 5 Review Gate: **NOT PASSED**.

The next execution must remain in Phase 5 (state C), fix the stale Detail-state invalidation defect, add focused regression coverage, and stop once Phase 5 again reaches completion. It must not enter Phase 6 in that same execution; the corrected completion state requires another independent Review Gate.