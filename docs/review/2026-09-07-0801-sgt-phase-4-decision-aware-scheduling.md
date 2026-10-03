# 2026-09-07 08:01 SGT — Phase 4 Decision-aware Scheduling Review

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed implementation commit: `5e87d6321083fe8bb0c384f0cdd6eefa0343eff1`

Branch HEAD at review start: `fa745bcf49d180fb1658dbcb63feeda59699b412`

Previous review: `2026-09-07-0658-sgt-phase-3-transactional-dose-decision.md`, reviewed HEAD `6c9f92fe1cb46a84333fdf256bdc3f959831b2e6`, disposition `PASS WITH NON-BLOCKING FINDINGS`.

Feature phase: Phase 4 — Decision-aware Scheduling

Review type: implementation Review Gate.

## Scope

This review determines whether Phase 4 satisfies `04-decision-aware-scheduling.md` and may advance to Phase 5. It covers the scheduler candidate loop, persisted-decision checks, all `scheduleAll()` rebuild paths, fired-occurrence advancement, reminder/dismiss receiver decision guards, cancellation/fire ordering, DST behavior, focused tests, and CI evidence.

The commit after the implementation, `fa745bcf49d180fb1658dbcb63feeda59699b412`, modifies only the root `README.md` commit/tag convention. It does not change Phase 3 or Phase 4 production/test behavior, so the Phase 3 gate remains valid and the Phase 4 implementation under review is the code introduced by `5e87d6321083fe8bb0c384f0cdd6eefa0343eff1`.

## Verification evidence

GitHub Actions CI Run #30 (`34066171403`) ran against Phase 4 implementation commit `5e87d6321083fe8bb0c384f0cdd6eefa0343eff1` and completed with conclusion `success`. The repository CI path runs unit tests, lint, and debug assembly.

GitHub Actions CI Run #31 (`34066861212`) also completed with conclusion `success` against current branch HEAD `fa745bcf49d180fb1658dbcb63feeda59699b412`; that HEAD differs from the Phase 4 implementation only by the root README documentation change.

## Findings

### HIGH / BLOCKING — persisted local-day suppression is not stable across schedule-zone changes

Phase 4 routes candidate suppression through `MedicationRepository.hasDoseDecisionOnLocalDay(doseTimeId, scheduledFor, zoneId)`. That method derives a local-day UTC interval from the candidate occurrence's **current** `zoneId`, then asks whether an existing event's persisted `scheduledForEpochMillis` falls inside that interval.

A persisted dose event does not store the zone/local-date under which the decision was made. Consequently, when a DEVICE schedule changes zone, or a MANUAL schedule is switched to device time, the same logical wall-clock dose day can move far enough in UTC that the previously persisted event no longer lies inside the new zone's local-day interval. The scheduler then treats the new candidate as undecided and can register it again.

A concrete counterexample is a dose decided for local 08:00 on September 7 in a negative-offset zone, followed before the dose by a move across the International Date Line to a large positive-offset zone. The old decision's stored instant can map to September 8 in the new zone, while the rebuilt 08:00 candidate is on September 7; `existsForOnLocalDay` therefore returns false even though the user already decided the logical September 7 slot.

This violates the feature-level requirements that an early decision survive `TIMEZONE_CHANGED`, manual/device zone transitions, restart/rescheduling, and the Phase 4 exit criterion that no persisted decided occurrence can create a visible reminder through a known rebuild path.

Required corrective direction: define and persist a zone-stable logical decision key for the local-day policy, or otherwise make the decision lookup provably stable across schedule-zone transitions. The existing assumption that no Room migration is needed must not override this correctness requirement; if the current schema cannot represent the required logical day/zone identity, update the feature plan before implementation and add the minimal justified migration. The corrective tests must include a real zone-change case that crosses a local-date boundary, not only DST transitions within one zone.

Phase 4 Review Gate impact: **blocking**.

### MEDIUM / NON-BLOCKING — rebuild-path tests exercise the decision-aware kernel, not the actual system-event wiring

`DecisionAwareSchedulingTest` directly verifies `nextUndecidedOccurrence()` for early decisions, repeated decided candidates, fired-boundary advancement, DST gap, and DST overlap. `ReminderDeliveryHandlingTest` verifies suppression, post-display race cancellation, single advancement, and stale occurrence handling.

Source review confirms app initialization and `SystemEventReceiver` call `scheduleAll()`, and the manifest registers BOOT, MY_PACKAGE_REPLACED, TIME_SET, and TIMEZONE_CHANGED. However, there is no focused integration test that drives those actual receiver/rebuild entry points with persisted Room decisions and verifies the resulting AlarmManager registration behavior.

This is not independently blocking because the wiring is direct and CI compiles it, but the missing integration coverage should be added during the Phase 4 corrective pass or Phase 6, especially for the blocking zone-change case.

### LOW / NON-BLOCKING — real AlarmManager side effects remain unverified by the JVM suite

The pure scheduling tests demonstrate candidate selection and receiver sequencing, but do not inspect real `AlarmManager.setAlarmClock` replacement/cancellation behavior or Android `PendingIntent` identity under rescheduling. This remains suitable for integration/device verification in Phase 6 unless a corrective change touches those boundaries.

## Correctness assessment

**CHANGES REQUIRED.**

The scheduler now skips persisted decisions for stable-zone candidates and advances strictly after a fired occurrence. Receiver delivery performs persisted checks around notification posting and schedules the following occurrence from the fired boundary, closing the primary cancel/fire ordering. However, persisted decision suppression is not invariant under all required time-zone changes, so Phase 4's central correctness contract is not yet satisfied.

## Architecture assessment

The Phase 4 shape is otherwise aligned with the intended architecture: database state remains authoritative, `scheduleAll()` funnels through decision-aware scheduling, `ReminderReceiver` rechecks persisted state, and advancement is based on the explicit fired occurrence. The remaining blocker is a persistence-model mismatch between the required local-day identity and what `dose_events` currently stores. The next corrective development run must resolve that mismatch before Phase 5 begins.

## Test assessment

CI for both the implementation commit and current branch HEAD is `success`.

Focused JVM tests cover early-decision candidate skipping, repeated candidate advancement, strict fired-boundary advancement, DST gap/overlap, already-decided receiver suppression, normal display, decision-vs-notify race cancellation, and stale occurrences. Missing coverage includes persisted zone-change/local-date-boundary suppression and real receiver/AlarmManager integration.

## Disposition

**CHANGES REQUIRED**

Blocking findings: 1.

Phase 4 Review Gate: **NOT PASSED**.

The next execution must remain in Phase 4 corrective development. It must first update the plan if the no-migration assumption is no longer technically valid, then make persisted decision identity stable across the required time-zone transition semantics and add focused regression coverage. After Phase 4 again reaches completion, stop and require another independent Review Gate before entering Phase 5.
