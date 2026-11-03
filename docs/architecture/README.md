# Architecture Index

This directory indexes architecture-relevant feature work and the implementation plans that define cross-layer contracts.

## Default reading order

Before making architecture or feature implementation decisions, read the repository review archive first: [`../review/README.md`](../review/README.md). Its **Latest review** pointer is the authoritative entry point to the most recent review and must be read by default before relying on older plans or assumptions.

After the latest review, use this architecture index and then the relevant feature implementation plan.

## Architecture and feature index

| Feature / source | Architecture focus | Implementation plan / entry point | Status |
| --- | --- | --- | --- |
| Review archive | Time-locked architecture, implementation, and regression reviews; latest findings override older review assumptions when they conflict | [`../review/README.md`](../review/README.md) — read **Latest review** first | Active / canonical review entry point |
| Medication Detail + pre-alarm dose decisions | Stable dose-slot identity, explicit occurrence identity, transactional dose decisions, decision-aware AlarmManager scheduling and receiver guards | [`../feature-plans/medication-detail-dose-actions/README.md`](../feature-plans/medication-detail-dose-actions/README.md) | Implementation through Phase 6 and reminder-entry/startup corrections included in master via `7de78b4` / `701ffec`. Master `de2f6b6` also includes correction `2cbdad2`; the canonical latest review covers reconstructed autostart guidance, Home-opened Detail selection, keyed state and DST. Historical results are bounded to their reviewed source; latest correction device acceptance remains pending. |
| Interval reminders + dose summary | Fixed-anchor interval occurrences, PRE/DUE reminder stages, persisted pending reminders, per-occurrence notification identity, dose snapshots and history summary | [`../feature-plans/interval-reminders-dose-summary/README.md`](../feature-plans/interval-reminders-dose-summary/README.md) | Plan revised on merge baseline `2b6822e`, including master `de2f6b6`. Supervisor relayed independent plan review PASS for `be77e1a`. P1 (`2f75eb6`) independent review PASS relayed by the supervisor; P2 (`5cb5fa0`) independent re-review PASS relayed by the supervisor. P3 dose-summary implementation delivered (B), awaiting independent review; Room/Robolectric runtime validation remains deferred and P4 has not started. Subsequent full validation belongs to the main assistant VM or user local environment; bounded cloud self-check results are recorded in the plan. |

## Current feature contract

The medication-detail feature keeps the existing editor architecture and uses persisted dose decisions as the correctness boundary: AlarmManager state may be cancelled/rescheduled, but reminder delivery must always consult persisted decision state before displaying a reminder. The Phase 4 review established that the local-day decision identity itself must also be persisted rather than reconstructed from the current schedule zone. A minimal Room v2→v3 migration therefore adds a stable logical local-day key to `dose_events`; this is a correctness migration, not an unrelated schema expansion.

On the merged master baseline, the medication-detail implementation is already included. Home-opened Detail selects today's latest undecided due occurrence in the schedule zone before future undecided occurrences; The baseline Home card ordering/display uses future times; the feature branch's P2 now uses unified current-occurrence selection. Explicit notification occurrences remain preferred, loaded results and decision state are keyed to their inputs, and DST validation follows the shared `atZone` rule. Autostart launch failures open the existing manual guide rather than automatically falling back to App Details. Preserve these corrections when extending interval/pending behavior; do not rebuild their navigation, state or permission framework.

For current findings and corrections to assumptions, read the latest archived review first. The original medication-detail plan remains an implementation history and contract reference; the interval-reminder plan below governs the new feature's sequencing and verification.

## Interval-reminder extension

The new plan preserves the fixed-time schedule's logical-local-day decision behavior and uses the existing exact `(doseTimeId, scheduledFor)` identity for interval schedules. Persisted pending reminders remain separate from completed dose events: explicit notification first, then oldest persisted pending; without persisted pending, fixed-time schedules retain today's latest undecided due fallback before future doses. P1 implements the Room v3→v4 migration, interval calculation, stable interval slot and per-occurrence decision queries/snapshots, with a shared validator and pending DAO. P2 implements separate PRE/DUE delivery, pending restoration/selection, occurrence-tagged notifications, legacy notification transition, sound preference and interval/dose editors with foreground countdowns. Fixed-mode decision lookup now checks exact occurrence before logical-day, preserving uniqueness across travel. Independent P1 review PASS was relayed by the supervisor; P2 independent re-review PASS for `5cb5fa0` was relayed; P3 independent review and deferred Room/notification runtime verification remain pending. Baseline reminder notifications use `VISIBILITY_PUBLIC` and display dosage without a `publicVersion`; the plan preserves that behavior and makes no lockscreen-redaction claim. Baseline app version is `1.0.0-beta.4` / code 4; future release preparation must advance the then-current version. The canonical latest review remains the review archive pointer above; its historical 135-test result does not verify this feature.


## Dose summary extension

P3 derives read-only dose summaries from the same history Flow as the history list. Both views use the shared 30-day retention filter; summary windows use confirmation time, with Today bounded by device-local midnight and 7/30 days rolling by elapsed time. Aggregation groups medication IDs and exact stored units and sums only known Taken snapshots using BigDecimal. Skipped records contribute counts only; unknown historical doses remain unknown and are never inferred from current medication text. The history query no longer selects current dosage text. There is no new database version, summary table, background job, unit conversion or cross-medication dose total. Draft release notes are in [`../feature-plans/interval-reminders-dose-summary/release-notes.md`](../feature-plans/interval-reminders-dose-summary/release-notes.md); P4 validation/version preparation remains pending.
