# 2026-09-07 22:12 SGT — Phase 4 Decision-aware Scheduling Corrective Review

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed implementation commit / HEAD: `76820848a55d4425305d85812951e609991a1bfe`

Previous review: `2026-09-07-0801-sgt-phase-4-decision-aware-scheduling.md`, disposition `CHANGES REQUIRED`.

Feature phase: Phase 4 — Decision-aware Scheduling

Review type: corrective implementation Review Gate.

## Scope

This review verifies the Phase 4 corrective implementation after the previous Review Gate found that decision suppression was reconstructed from historical `scheduledFor` using the current schedule zone and therefore was not stable across schedule-zone changes. The scope includes the revised feature/architecture contract, Room v2→v3 migration, persisted logical-day key, repository decision/idempotence queries, the International Date Line regression, existing decision-aware scheduler/receiver behavior, and current CI evidence. No Phase 5 implementation is reviewed or authorized by this document.

## Verification evidence

The corrective commit `76820848a55d4425305d85812951e609991a1bfe` changes only Phase 4 persistence/repository tests and the corresponding plan/architecture documentation. It raises Room from v2 to v3, registers `MIGRATION_2_3`, adds `scheduledLocalEpochDay` to `dose_events`, adds an index on `doseTimeId + scheduledLocalEpochDay`, and changes decision lookup from a current-zone-derived UTC instant range to the persisted logical local day.

At decision time, `RoomMedicationRepository.decideDose()` derives `scheduledLocalEpochDay` from the explicit occurrence's original `scheduledFor + zoneId`, persists it with the event, and uses `doseTimeId + scheduledLocalEpochDay` for first-write-wins idempotence. `hasDoseDecisionOnLocalDay()` derives only the candidate's logical calendar day and compares that stable epoch-day key, so moving the same logical local day between zones no longer reinterprets the historical event instant.

`RoomMedicationRepositoryDoseDecisionTest.decisionRemainsOnSameLogicalDayAcrossInternationalDateLineZoneChange()` persists a Taken decision for local September 7 at 08:00 in `Pacific/Honolulu`, changes the schedule to `Pacific/Kiritimati`, rebuilds the same local September 7 08:00 occurrence at a different UTC instant/date mapping, then verifies both scheduler-facing lookup and a second Skipped decision remain suppressed/idempotent.

GitHub Actions CI Run #33 (`34070238673`) ran against exact HEAD `76820848a55d4425305d85812951e609991a1bfe` and completed with conclusion `success`. The repository CI path executes unit tests, lint, and debug assembly.

## Findings

### MEDIUM / NON-BLOCKING — Room migration behavior lacks a focused migration test

The production migration is registered and current CI compiles/runs the v3 database and repository tests, but there is no dedicated test that opens a v2 schema containing representative `dose_events`, applies `MIGRATION_2_3`, then verifies the new column/index, deterministic backfill, retained history rows, and successful Room schema validation.

This is not a Phase 4 correctness blocker because the migration implementation is narrow, the entity default/index match the SQL shape, exact-HEAD CI is green, and the central zone-stability behavior is covered against a real in-memory Room v3 database. It should be added during Phase 6 migration/integration verification, or earlier if subsequent work touches the schema.

### LOW / NON-BLOCKING — legacy v2 events cannot recover an unavailable historical DEVICE zone

For existing v2 rows, the migration backfills MANUAL schedules using the currently stored manual zone and DEVICE schedules using `ZoneId.systemDefault()` at upgrade time. The old schema never persisted the original decision zone/local date, so an exact historical reconstruction is impossible for a DEVICE event whose device zone changed before the v3 migration runs.

The migration is deterministic and does not weaken the new v3 contract for decisions recorded after upgrade. This limitation should be documented in Phase 6 upgrade verification; it does not justify inventing unavailable historical data or expanding the migration beyond the minimal correctness field.

### LOW / NON-BLOCKING — real system-event/AlarmManager side effects remain integration-level evidence

As in the prior review, scheduler and receiver correctness is strongly covered by focused JVM tests and source wiring, but BOOT/package replacement/TIME_SET/TIMEZONE_CHANGED plus real `AlarmManager`/`PendingIntent` side effects are not yet exercised end-to-end on device. This remains appropriate Phase 6 evidence unless Phase 5 changes those boundaries.

## Correctness assessment

**PASS WITH NON-BLOCKING FINDINGS.**

The previous blocking defect is resolved. The persisted decision now carries a zone-stable logical local-day identity independent of the event's UTC instant, scheduler lookup uses that identity, and a cross-International-Date-Line regression proves that the same logical day remains decided after a schedule-zone change. Existing Phase 4 receiver guards, decision-aware rescheduling, fired-boundary advancement, stale-action protection, and DST behavior remain intact because the corrective commit does not alter those control paths except for the decision lookup contract.

## Architecture assessment

The architecture now matches the corrected feature contract: database state remains the correctness source of truth, occurrence identity remains explicit, and the local-day idempotence key is persisted rather than reconstructed from mutable schedule-zone state. The Room v2→v3 migration is justified by the Review Gate blocker and is limited to the field/index required for this invariant. No unrelated schema expansion is present.

## Test assessment

Exact-HEAD CI is `success`. The corrective Room test covers the principal failing scenario across `Pacific/Honolulu` and `Pacific/Kiritimati` and verifies both scheduler-facing suppression and decision first-write-wins semantics after the zone change. Existing Phase 4 tests continue to cover early decisions, `scheduleAll()` candidate skipping, fired-occurrence advancement, receiver suppression/race handling, stale occurrences, and DST gap/overlap. Dedicated migration and real Android system-event/AlarmManager integration remain non-blocking follow-up evidence.

## Disposition

**PASS WITH NON-BLOCKING FINDINGS**

Blocking findings: 0.

Phase 4 Review Gate: **PASSED**.

The next execution may confirm that this review remains valid against the branch HEAD and then enter Phase 5 — Medication Detail UI. If Phase 5 reaches its completion criteria during that execution, it must stop immediately and await a separate Review Gate.