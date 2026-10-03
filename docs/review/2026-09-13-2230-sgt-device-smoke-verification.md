# 2026-09-13 22:30 SGT — Device Smoke Verification (medication detail dose actions)

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed ref: `288d669` (`test: verify rebased reminder and dose decision integration`). The review run itself produced no application, test, resource, manifest, or Gradle change; the documentation updates that accompany this review are listed at the end of this document.

Previous review: `2026-09-08-0730-sgt-phase-6-verification-rollout.md`, disposition `PASS WITH NON-BLOCKING FINDINGS`. That review recorded the Phase 6 device-smoke checklist as **not executed** because no device transport was available; this review executes that checklist.

Device: OPPO Find X8s+ (adb model `PLB110`, ColorOS), device clock `Asia/Shanghai`, app UI language Chinese (device locale).

Review type: device verification of reviewed source plus read-only root-cause analysis of the defect it exposed. No application, test, resource, manifest, or Gradle file was modified.

## Scope

1. Build the debug APK from reviewed source without touching the tree.
2. Install in place over existing app data (upgrade path, not a clean install).
3. Execute the Phase 6 device-smoke list: Home → Detail → Edit navigation, notification → exact occurrence Detail, Taken/Skipped decisions, pre-alarm suppression, relaunch, alarm inspection, next-slot/next-day scheduling.
4. Extend with checks the plan lists only implicitly: process-death behavior, rotation, dose-boundary crossing, and data persistence through Room.
5. Establish the root cause of any failure with device logs plus source/artifact evidence.

Out of scope: implementing a fix, running instrumented tests, and the known ColorOS reminder-suppression limitation recorded in `docs/memory.md`.

## Build and install evidence

- `assembleDebug` (offline, `--no-daemon`, wrapper driven through `JAVA_HOME=D:\AndroidStudio\jbr`): **BUILD SUCCESSFUL** in 1 m 03 s, 39 actionable tasks. APK `app/build/outputs/apk/debug/app-debug.apk`, 20,758,986 bytes, `sha256 cff3c76f…3925b836`.
- `testDebugUnitTest`: **BUILD SUCCESSFUL**; 18 test classes, **80 tests, 0 failures, 0 errors, 0 skipped**.
- Install: `adb install -r` → `Success`; `versionName=1.0.0-beta.1`, `lastUpdateTime=2026-09-13 20:46:42`. In-place upgrade preserved app data: `databases/medication-reminder.db` remained `PRAGMA user_version = 3` with the pre-existing profile (2 medications, 4 dose slots, 2 historical `TAKEN` events) intact after launch.
- The Room **v2→v3 migration was not exercised on device**: the pre-existing database was already at `user_version = 3`, so only the "existing v3 data + in-place APK replacement" path was covered.

## Smoke results

| Check | Result | Basis |
| --- | --- | --- |
| Build | PASS | `assembleDebug` + `testDebugUnitTest` above |
| Install / data preservation | PASS | `install -r`, DB re-read after launch |
| Medication Detail rendering | PASS | name/alias/dosage/note/schedule/timezone + occurrence all correct; action buttons clickable and enabled |
| Taken / Skipped | PASS | both persisted (`dose_events` `TAKEN` and `SKIPPED` rows) and controls disabled after the decision |
| Edit → return recalculation | PASS | weekday/time edits recalculate the occurrence, re-arm the alarm, and do not leak stale decisions (detail below) |
| Home flow (next actionable occurrence) | PASS | per-medication next dose correct, including after edits and after process death |
| Notification flow | **FAIL** | see CRITICAL finding; the cold path is correct, the already-running path is not |
| Lifecycle (background, rotation, process death) | PASS | state preserved; cold relaunch consistent; alarms correctly advanced |
| Time-boundary crossing | PASS with a minor finding | scheduling advanced correctly; an open Detail screen does not refresh its label |

## Findings

### CRITICAL / BLOCKING — a reminder opened while the app is already running loses its explicit occurrence and does not navigate

**Reproduce.** Use the app at least once so its task is alive (foreground or backgrounded). When a reminder fires, tap the notification body.

**Actual.** No navigation occurs: foregrounded on Home the app stays on Home; backgrounded it is merely brought forward on the previous screen. When Detail is then reached, the occurrence line reads `下一次：…` (the Home-derived next dose) instead of `提醒对应服药：…` (the notification's occurrence). Once the dose time has passed, the Home/Detail `下一次` line has already advanced to the next day, so the reminded slot has no remaining UI entry point.

**Evidence.**
- `ActivityTaskManager: START u0 {xflg=0x4 cmp=com.example.medicationreminder/.MainActivity (has extras) mCallingUid=1000 …} with LAUNCH_MULTIPLE … result code=3` at 21:03:29.211 and again 21:05:13.427. `result code=3` is `START_DELIVERED_TO_TOP`, i.e. the intent was delivered to the existing activity instance rather than creating one.
- Immediately before it, `21:03:29.207 W ActivityTaskManager: startActivity called from non-Activity context; forcing Intent.FLAG_ACTIVITY_NEW_TASK` — the notification content `PendingIntent` carries no `FLAG_ACTIVITY_*` of its own, so the system adds `NEW_TASK`, and a `NEW_TASK` intent aimed at the **root** activity of an existing task is delivered to that existing instance.
- Control experiment: the same four extras delivered through a cold start (`am start -S -n …/.MainActivity --el open_medication_id … --el open_dose_time_id … --el open_scheduled_for … --es open_zone_id …`, reported `LaunchState: COLD`) produced the correct `提醒对应服药：…` line and a working Taken action. The PendingIntent, payload, parsing, and Detail rendering are therefore all correct.

**Root cause.** `MainActivity` reads its intent extras only in `onCreate` and does not override `onNewIntent`:

- `app/src/main/java/com/example/medicationreminder/MainActivity.kt:34-48` — `onCreate` is the only lifecycle method; `:37-38` reads the extras; `:41-45` is the sole `MedicationAppEntry(...)` call site.
- `MainActivity.kt:50-55` — `intentDoseOccurrence()` reads `intent`; the class has no `onNewIntent` and no `setIntent` call (`app/src` contains zero occurrences of `onNewIntent`, `launchMode`, `singleTop`, or `setIntent(`).
- `app/src/main/AndroidManifest.xml:14-21` — `<activity android:name=".MainActivity" android:exported="true">` with a launcher filter and **no** `launchMode`.
- `app/src/main/java/com/example/medicationreminder/ui/MedicationAppEntry.kt:16,24,27` — the occurrence reaches Compose through `LocalNotificationDoseOccurrence`, computed once per Activity creation.
- `app/src/main/java/com/example/medicationreminder/ui/MedicationApp.kt:158,162-177` — `MedicationAppContent` reads that local; `openedNotificationTarget` (`:162`) is a one-shot guard and the navigation effect (`:171-176`) therefore also runs at most once per Activity instance.
- `app/src/main/java/com/example/medicationreminder/ui/MedicationDetailScreen.kt:96-104,203` — with a null explicit occurrence the producer takes `viewModel.nextActionableOccurrence(...)` and the label falls back to `detail_next_occurrence`.

The same entry contract is used by the alarm's status-bar `showIntent` (`app/src/main/java/com/example/medicationreminder/reminders/ReminderScheduler.kt:58-66`), so that path loses the extras identically.

**Consequence.** The plan's invariant 1 and its notification scenario ("Reminder notification opens Detail with the exact reminder occurrence identity", `docs/feature-plans/medication-detail-dose-actions/README.md`, and scenario 5 of `06-verification-rollout.md`) hold only when the app process has no live activity. In the common case the reminder cannot be actioned for the occurrence it represents.

**Coverage gap that allowed this.** The only test that targets this contract is `app/src/test/java/com/example/medicationreminder/ui/MedicationDetailNavigationContractTest.kt:9-30`, which asserts `contains(...)` on the source text of `MedicationApp.kt`; `app/src/test/java/com/example/medicationreminder/MainActivityOccurrenceParsingTest.kt` covers only the pure parser. Both Phase 5 reviews already recorded this limitation (`2026-09-08-0128-…:41-45`, `2026-09-08-0231-…:35-37`) and deferred runtime/device navigation evidence to Phase 6; Phase 6 then recorded the device smoke as unavailable (`2026-09-08-0730-…:41-43`). The plan's own scenarios (`06-verification-rollout.md:15-26`) never include a reminder opened while the app is already running, which is why the defect survived all six Review Gates.

### MAJOR / NON-BLOCKING — ColorOS blocks the alarm-triggered receiver start after process death, so a due reminder is silently dropped

**Reproduce.** Ensure an undecided upcoming dose (`dumpsys alarm` shows its `REMIND.<doseTimeId>`), kill the app process, then wait for the dose time.

**Actual.** The alarm fires, the system refuses to start the app, and no reminder is posted; the one-shot alarm is consumed, so the reminder is lost with no retry.

**Evidence.** `21:16:00.010 V AlarmManager: sending alarm Alarm{… com.example.medicationreminder} uid 10437` immediately followed by `21:16:00.015 W OplusAppStartupManager: prevent start com.example.medicationreminder, cmp ComponentInfo{com.example.medicationreminder/com.example.medicationreminder.reminders.ReminderReceiver}`; the process was never started, no notification was posted, and `REMIND.12` disappeared from `dumpsys alarm`.

Related OEM behavior observed in the same run: `21:05:29.171 NotificationService--OplusNotificationManagerServiceExtImpl: onUidGone : uid = [10437] … clear all notification` removes already-posted reminders when the app's uid dies. Recovery only happens when the app process next runs: at `21:28:13` four previously posted reminders (ids 106–109) were re-posted through the `ReminderDismissReceiver` deleteIntent path, which is the designed "dismissed while undecided → re-post" behavior (itself the reason those ids reappeared) [causal link inferred; the deleteIntent broadcasts were not captured directly].

This is an OEM restriction, not an application-logic defect, and it is consistent with the ColorOS reminder-suppression entries already recorded in `docs/memory.md`. It remains a reminder-reliability gap on this device because the app's self-heal (`reminders/MissedReminderReposter.kt`, gated by the `repost_missed_reminders` preference, default off) can only run once the process is alive again.

### MINOR / NON-BLOCKING — an open Detail screen does not refresh when the dose boundary passes

With Detail open on the current occurrence, the screen continued to render `下一次：周日, 9月 13 • 9:02 下午` (then `• 9:25 下午`) at both 21:02:15 and 21:25:20/21:25:41, i.e. after those instants had passed, with the action buttons still enabled. Re-entering Detail recomputes correctly, and the occurrence itself remains legitimately decidable, so this is presentation staleness rather than an incorrect write (`MedicationApp.kt:92` fixes `now` per composition, and `MedicationDetailScreen.kt:93,111-115` only re-derive on plan/occurrence change).

### MINOR / NON-BLOCKING — occurrence changeover under a mounted Detail renders the previous occurrence for a frame

Source analysis (not device-observed; becomes newly reachable once the CRITICAL finding is fixed): `produceState` remembers its result state **without keys** and only restarts the producer, so on an explicit-occurrence change A→B the `initialState` at `MedicationDetailScreen.kt:93` still holds the state derived from A while `state` (`:112`) has been reset to `null` by the key change; `doseState = state ?: initialState` (`:143`) therefore renders A's occurrence and its stale/decided flags for the duration of one suspend decision lookup (`MedicationViewModel.kt:105-111`), while the label at `:203` already uses the current non-null occurrence. A tap inside that window would target A. The window is sub-reaction-time, so this is a determinism/polish item; the same value-equal occurrence re-delivered leaves every key, state, and effect untouched.

### INFO — coverage boundaries of this run

- The notification's `已服用` / `已跳过` action buttons were **not tapped**: ColorOS's grouped shade exposes the app's notification as a single clickable card and publishes no action nodes to the accessibility layer. `dumpsys notification --noredact` confirms both actions are present and bound to broadcast PendingIntents into `ReminderActionReceiver`/`ReminderDismissReceiver`, and they converge on the same `DoseDecisionUseCase` as the UI buttons exercised here.
- Landscape rendering requires scrolling to reach the action buttons (the screen is a `LazyColumn`); not a defect.
- Rotation after a warm reminder tap, and the Room v2→v3 upgrade path, were not exercised (see build/install evidence).

## Verified-passing behavior (evidence)

- Detail content: name, alias, dosage, note, weekday set, times, `时区：跟随设备`, and the occurrence line all rendered correctly; both action buttons were `clickable` and `enabled` while an undecided actionable occurrence existed (visual confirmation captured).
- Taken persistence: `dose_events` row `{medicationId: 4, doseTimeId: 13, scheduledForEpochMillis: 1789305900000 (2026-09-13 21:25 local), scheduledLocalEpochDay: 20709, status: TAKEN}` after a Detail-originated decision reached through the cold entry path; the buttons then reported `enabled=false` and the status chip read `已标记为服用`.
- Skipped persistence: `dose_events` row `{medicationId: 4, doseTimeId: 15, scheduledForEpochMillis: 1789306680000 (2026-09-13 21:38 local), status: SKIPPED}`; the corresponding reminder notification (id 115) was cancelled by the app as part of the decision effects.
- Decision-aware scheduling: with today's 20:00 slot passed, `dumpsys alarm` listed only next-day alarms (`08:00`, `12:39`, `20:00` + the test medication's slot); after a decision the pending reminder advanced from `REMIND.11` to the following day at the same local time; `REMIND.10`/`REMIND.11` were replaced — not duplicated — when a dose time changed.
- Edit → return: removing a weekday moved the occurrence to the next enabled day and re-armed the alarm for that day; re-adding it moved both back to the current day; changing the dose time produced a **new** `doseTimeId` with no inherited decision and re-enabled the action buttons (stale decision not leaked); toggling a weekday kept the same slot identity and correctly suppressed the already-decided occurrence, advancing `下一次` to the next undecided day.
- Lifecycle: background/foreground round trip preserved the screen and occurrence; forced landscape rendered and remained navigable; after `kill -9` the process was gone and a cold relaunch showed consistent profile data with all alarms still registered.
- Deleted medication: Detail degraded to `此药物已不存在。` instead of crashing, and the delete cascaded the medication's schedule, dose slots, and dose events while leaving the pre-existing profile rows intact.
- History and Settings screens rendered correctly against the v3 schema (records listed with `已服用`; notification and exact-alarm permissions reported as granted).

## Correctness assessment

The persistence, transaction, scheduling, receiver, and Detail layers behaved correctly in every exercised path: decisions are persisted once per logical local day, decisions suppress the corresponding reminder and advance scheduling, unchanged dose slots keep identity while changed times create new ones, and stale state does not leak across edits. The feature's notification entry contract, however, is **not** satisfied for an already-running app, which is the common case; the defect is in the Activity/Compose ingress, above the layers verified by the automated suite.

## Architecture assessment

The reviewed implementation still matches the architecture index: persistence owns decision truth and stable logical-day identity, scheduling is decision-aware, and Detail is read-only with the editor retained. This review identifies one missing contract detail — the occurrence ingress is bound to a single Activity creation rather than to "the activity received this reminder intent" — which the Phase 2 contract document describes only as "Extend `MainActivity`/Compose entry state so Detail can receive an explicit occurrence when opened from a reminder" (`02-dose-occurrence-contract.md`, step 4) without distinguishing cold from already-running delivery. No persistence, schema, scheduler, or receiver change is implied by the correction.

## Test assessment

Automated verification remains green (80 unit tests) and the reviewed source compiles and installs, but the suite cannot detect this class of defect: the only test aimed at the contract asserts source text, and the parser tests are pure-function. An Activity-level regression test is feasible with the existing stack (Robolectric 4.16.1 and `unitTests.isIncludeAndroidResources = true` are already configured) — `ActivityController.newIntent(Intent)` for warm delivery and `recreate()` for configuration change; asserting the rendered `提醒对应服药` text additionally requires `androidx.compose.ui:ui-test-junit4`, which the project does not currently declare. Device verification remains necessary for OEM delivery semantics.

## Recommended correction (not implemented in this review run)

1. Make the notification target a lifecycle-driven value rather than a creation-time constant: parse the extras in one `applyIntent(intent)` used by both `onCreate` and an `onNewIntent` override, holding the result in observable state (`onNewIntent` must call `super.onNewIntent(intent)` and `setIntent(intent)`).
2. Keep the occurrence reachable across configuration change — hold it in state that survives recreation (a `rememberSaveable`-backed target or the Activity-scoped `MedicationViewModel`, whose `error`/`clearError` pair already models a one-shot handoff) instead of relying on framework relaunch-intent semantics.
3. Replace the boolean one-shot guard (`MedicationApp.kt:162,171-177`) with value-based deduplication keyed on the delivered target, and navigate with `launchSingleTop = true` so a repeated reminder does not push duplicate Detail entries.
4. Add the missing plan scenarios and a warm-path regression test; the source-text navigation contract should be replaced rather than re-pinned.

Fuller analysis, options, and blast radius are in the session record; the fix itself requires its own implementation and Review Gate.

## Blocking / non-blocking summary

Blocking findings: 1 (notification entry loses the explicit occurrence while the app is running).

Non-blocking findings: 4 (OEM alarm-start blocking; dose-boundary staleness; occurrence-changeover frame; coverage boundaries).

## Disposition

**CHANGES REQUIRED.**

This run completes the Phase 6 device-smoke item with a **blocking** result and therefore converts the Phase 6 "device smoke unavailable" confidence gap into a concrete defect on the notification entry contract. No implementation change was made, so the Phase 6 Review Gate is not invalidated by a code change; it is superseded for the device-verification portion by this review. The feature must remain on `codex/feature-medication-detail-dose-actions`; no merge, PR, or release action was taken, and the CRITICAL finding requires implementation plus a new Review Gate before any merge decision.

## Documentation updated with this review

- `docs/review/README.md` — `Latest review` pointer and archive index row.
- `docs/architecture/README.md` — feature status row.
- `docs/feature-plans/medication-detail-dose-actions/README.md` — branch status and new invariant 8.
- `docs/feature-plans/medication-detail-dose-actions/02-dose-occurrence-contract.md` — warm-delivery step, test, and exit criterion.
- `docs/feature-plans/medication-detail-dose-actions/06-verification-rollout.md` — scenarios 13–14 and device-smoke status.
- `docs/memory.md` — verification evidence entry, device smoke evidence section, backlog items.
- `docs/operations.md` — app-private data extraction, screenshot capture, cold-open simulation, and ColorOS reminder-blocking rows.
- `AGENTS.md` — branch table row for `codex/feature-medication-detail-dose-actions`.
