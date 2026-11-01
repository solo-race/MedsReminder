# 2026-10-24 23:33 SGT — Reconstructed autostart and pending-dose correction

## Scope and identity

Fresh reconstruction on `codex/recover-autostart-pending-dose`, based on
`4cd0f2f4f1b23e6e3a1246224896e76589dadec2`. This is not a recovery of the previous
VM's exact commit or build. No prior APK hash or test result validates this source.
The GitHub read-only check found master still at that base and all other remote
branches behind it. No equivalent correction was present.

## Findings and bounded corrections

1. The autostart entry tried OEM activities but then automatically opened App
   Details, which need not contain autostart. Remove that fallback. Preserve only
   existing vendor candidates; unsuccessful launch opens a saved, scrollable
   manual guide. General Settings has its own accurately labelled action.
   Neither navigation nor returning implies permission granted. Device/OEM UI
   verification remains separate; see `../autostart-detail-readability.md`.
2. Home cleared notification context and selected strictly future occurrences
   using the alarm scheduler helper. Detail now selects the most recent due,
   undecided occurrence today in the schedule zone, then falls back to the earliest
   undecided future occurrence. Older pending slots today remain available after
   newer ones are confirmed. Previous-day backlog is not synthesized, consistent
   with existing missed-reminder recovery. Older explicit notification identities
   remain pinned and are not replaced by this Home policy.
3. A Compose `produceState` value can still belong to the previous input while
   a new plan/notification loads. The result now includes its input key and is
   hidden unless the key matches; remembered decision state is also key-scoped.
   The clock is captured per input key, not for the entire composition lifetime.
4. Detail's occurrence validation now resolves local date/time with the same
   `atZone` rule as scheduling and repository validation: gaps shift forward,
   overlaps use the earlier offset. No schedules, doses or event identities change.

## Invariants

- Every action retains the exact medication ID, dose-time ID, scheduled instant
  and zone. Explicit notification occurrences always win, including delayed entry.
- Selection and navigation write no dose events. Taken/Skipped still use the
  existing transactional first-write-wins use case. No automatic confirmation,
  rescheduling policy change, schema migration or historical data modification.
- AlarmManager still schedules strictly future occurrences with its unchanged
  helper. There is no snooze feature or new snooze policy in this change.
- Disabled/deleted plans cannot supply a Home action; stale explicit occurrences
  remain rejected. DEVICE and MANUAL zones use existing repository semantics.

## Verification checkpoint

The following tests were implemented and passed in the fresh execution:

- 14 pure Home-selector cases: exact boundary, pending before future, latest due,
  older same-day pending, decided future, midnight/prior-day exclusion, weekdays,
  medication isolation, empty schedules, local dates and DST gap/overlap.
- 5 Room/notification payload/ViewModel integration cases: two medicines same
  slot, first Taken then Home to second, repeated/conflicting decision idempotency,
  no navigation writes, notification identity and reapplication, future fallback,
  older pending/explicit identities, DEVICE zone, disabled/deleted plans.
- 4 additional Detail tests: DST and keyed loaded-result invalidation.
- 13 autostart tests (replacing 5), including one structural UI contract check.

These integration tests drive route state methods and real notification payloads;
they are not Compose button-click or physical-device tests. No live Android UI,
OEM settings, real medication data, release signing, push, PR or deployment is
part of this reconstruction. Fresh test/build/lint and independent-review outcomes
are recorded below.

## Fresh results received

- `testDebugUnitTest assembleDebug`: BUILD SUCCESSFUL; 135 tests, 25 classes, zero failures/errors/skips. All 50 Gradle tasks executed, not merely up-to-date.
- New debug APK: 20,289,851 bytes; SHA-256 `36acab943408f4ec3a3cd756510158d07b726f12a941c74bfa3d86b2186d56e4`. Not installed on any device.
- Independent read-only pending-dose source review: no blocking findings. Actual Compose recomposition/click timing and OEM UI remain untested.
- XML/unique keys/EN–ZH parity and Kotlin resource references passed (115 keys); complete patch applies cleanly to the base.
- Standard full `lintDebug` passed: zero errors, 22 warnings. Production, JVM unit and AndroidTest analysis were included. The previous VM’s dependency HTTP 403 did not recur; no external init script, dependency changes, alternate mirror or exclusion was used.
- Baseline reproduction: copying only the new integration test into a detached `4cd0f2f4` checkout produced 4 failures out of 5. In the exact two-medicine case, medicine 2 / slot 3 at 08:00 was expected, but old code selected slot 4 at 20:00. All five pass on the correction. Older-pending, due-boundary and DEVICE-zone cases also fail on base. The disabled/deleted no-write case passes on both.

## Warning comparison and remaining acceptance

The standard full lint task on the detached baseline ran offline and reported 13 warnings; the correction’s online run reported 22. The ten added entries are only newer-dependency/Gradle-version notices in unchanged build files, available from online metadata. The removed App Details URI eliminates one existing UseKtx warning. Thus this is not a same-mode warning-count comparison, and no new source-code warning was found in changed files. No warning was silenced and no dependency was changed.

Before device acceptance, verify the exact ColorOS version and OEM autostart page/manual path; guide Close/Back/reopen/return/recreation, EN/ZH at small screens and 200% font; and real two-medication notification → first Taken → Home → second pending occurrence. Do not uninstall an existing app to work around debug-signature incompatibility, clear its data, alter schedules or change its autostart setting without separate authorization. This debug APK was not installed or device-tested.

## Final aggregate gate

`testDebugUnitTest assembleDebug lintDebug --offline` passed. Tests/build reused the unchanged successful outputs; full lint executed again. The final offline lint has zero errors / 22 warnings. It retains ten dependency-version notices from the prior online analysis, so it does not share the baseline’s metadata state (baseline: 13 warnings). Normalized issue comparison shows those ten availability notices in unchanged build files and one removed UseKtx warning for the deleted App Details URI; there are no added non-version warnings. These metadata differences are not conflated with source regressions.
