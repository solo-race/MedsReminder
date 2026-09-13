# 2026-09-14 00:15 SGT — Device Smoke Verification (reminder entry warm delivery)

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed refs: `5e28a09` (entry-contract correction) and `0caed66` (`fix: deliver warm reminder intents to the running activity` — `android:launchMode="singleTop"` plus the delivery-contract test), applied on top of `2a56761`.

Previous reviews: `2026-09-13-2340-sgt-reminder-entry-warm-delivery-correction.md` (correction implemented, device smoke pending) and `2026-09-13-2230-sgt-device-smoke-verification.md` (CRITICAL notification-entry finding).

Device: OPPO Find X8s+ (PLB110, ColorOS), device clock `Asia/Shanghai`. The app was installed in place over existing data (`install -r`, no uninstall); the user's data was preserved throughout and the temporary test data created for this run was deleted through the app at the end.

Review type: device verification of the reminder-entry correction, plus the root-cause work needed to make the running-app case real on this ROM.

## Scope

1. Install the debug build over existing app data and confirm the data survives.
2. Drive the delivery the notification uses (NEW_TASK intent into the running task) and check that Detail binds the reminder's occurrence.
3. Tap a real reminder notification while the app is already running (foregrounded and backgrounded).
4. Re-check the cold path, repeated delivery, rotation, and Taken on the reminded occurrence.
5. Leave the device and the user's data as found.

Out of scope: the non-blocking findings of the 2026-09-13 22:30 review (ColorOS alarm-start blocking for a not-running process, Detail label staleness across a dose boundary, occurrence changeover frame) and OEM notification-presentation behaviour.

## Build and install

- APK built offline with the canonical wrapper invocation; final artifact `app/build/outputs/apk/debug/app-debug.apk`, 20,758,986 bytes, sha256 `4f1e34dd…d171dc`. The device runs this artifact (pulled `base.apk` byte-identical to the build).
- `adb install -r` → **Success**; `firstInstallTime` unchanged (2026-09-08 19:48:10) → upgrade path, not a clean install.
- Existing data survived: database stayed `user_version = 3` with the profile (medications 舍曲林 / 希德, 4 dose slots, 6 historical `TAKEN` rows) identical before and after the run.

## Findings

### MAJOR (found in this review, fixed and re-verified before the run closed) — the running app never receives the warm reminder intent unless the activity is declared singleTop

**Reproduce.** With the app running (foregrounded or backgrounded, task alive), send the reminder intent the way the notification's content `PendingIntent` does — a NEW_TASK intent with the four `open_*` extras and no activity flags:

```
adb shell am start -n com.example.medicationreminder/.MainActivity \
  --el open_medication_id 3 --el open_dose_time_id 9 \
  --el open_scheduled_for 1789214400000 --es open_zone_id Asia/Shanghai
```

**Actual (before the correction).** AMS reports `START u0 {flg=0x10000000 … (has extras)} with LAUNCH_MULTIPLE … result code=3` and `am` prints "intent has been delivered to currently running top-most instance", but the app never runs `onNewIntent`: an app-private probe file recorded only the earlier cold `onCreate` (`extras=null`), the screen stayed on Home, and no new activity instance was created. The same delivery with an explicit `--activity-single-top` invoked `onNewIntent` with the extras and bound the occurrence, isolating the missing top-match flag as the cause.

**Root cause.** `MainActivity` was declared with the default launch mode. On this ROM the "existing task root receives a NEW_TASK intent" path reports `START_DELIVERED_TO_TOP` without invoking `onNewIntent` on the existing instance, so the Compose ingress — correct since `5e28a09` — never sees the delivery. This is the same platform detail the 2026-09-13 22:30 review recorded as "no `launchMode`" in its root-cause evidence.

**Correction.** `android:launchMode="singleTop"` on `MainActivity` (`0caed66`), covered by `reminderEntryDeliversWarmIntentsToTheRunningInstance`, which asserts `ActivityInfo.LAUNCH_SINGLE_TOP` through the package manager.

**Device re-verification.** After the correction, the same plain delivery does invoke `onNewIntent` with the extras, both foregrounded and backgrounded, and Detail binds the reminder's occurrence.

### INFO — verification techniques for this device (recorded in `docs/operations.md`)

- Application log output is not visible in logcat for this package on PLB110, so app-side evidence used an app-private probe file (`files/entry-probe.txt`, read with `exec-out run-as … cat`) during diagnosis; the probe was removed before the final artifact.
- The OEM notification shade publishes no accessibility nodes to `uiautomator`; reminder cards were located from screenshots and tapped by coordinate. The OEM auto-groups reminders (`g:Aggregate_AlertingSection`), so the group card expands first and the child card fires the content intent.
- Heads-up banners were not observed for reminders, consistent with the OEM 锁屏/横幅 toggle notes already recorded in `docs/memory.md`.

## Verified flows (evidence)

| Check | Result | Evidence |
| --- | --- | --- |
| Warm delivery, app foregrounded | PASS | `onNewIntent … (has extras)` → `applyIntent medicationId=3` → Detail `提醒对应服药：周六, 9月 12 • 8:00 下午`, both actions enabled |
| Warm delivery, app backgrounded (task alive) | PASS | HOME → delivery with `flg=0x10400000` (NEW_TASK + brought to front) → app brought forward on Detail `提醒对应服药：周五, 9月 11 • 8:00 上午` for a second medication |
| Real notification tap, app running | PASS | A test medication's 00:04 dose alarm posted reminder id 119 (`dumpsys notification`: 2 actions, contentIntent → MainActivity, `when` = the dose instant); tapping the child card delivered `onNewIntent` with `mCallingUid=1000 mRealCallingUid=10224 mRealCallingPid=2849` (SystemUI) and the reminder's occurrence → Detail `提醒对应服药：周一, 9月 14 • 12:04 上午` |
| Taken on the reminded occurrence | PASS | Detail showed `已标记为服用` and the reminder notification (id 119) was cancelled by the decision; the decision wrote to the test medication, whose rows were deleted afterwards |
| Repeated delivery (same reminder twice) | PASS | two `onNewIntent` calls and two navigations; a single back press from Detail returned to Home — no duplicate Detail entries |
| Rotation with the reminder Detail open | PASS | two recreations; `onCreate` reported the launch intent carrying the `open_*` extras (`setIntent`), the ViewModel deduplicated the re-application (`reapplied=true`, no pending delivery, no replay), and the occurrence line stayed `提醒对应服药：…` |
| Cold path (regression) | PASS | `am start -S …` with the same extras → Detail `提醒对应服药：周六, 9月 12 • 8:00 下午` |
| Data preservation | PASS | Database after the run: same 2 medications, 4 dose slots, 6 `TAKEN` rows; alarms re-armed (09-14 08:00 / 12:39 / 20:00, 09-15 08:00); zero leftover app notifications; both temporary test medications and their cascaded rows deleted through the app |

## Architecture assessment

The reminder-entry contract now holds for both Activity lifetimes: the framework hands the intent to the running instance, the extras are parsed once in `applyIntent`, the occurrence is bound in Activity-scoped ViewModel state (surviving recreation without replay), and Detail remains read-only. The platform requirement that makes the delivery real on this ROM is the `singleTop` declaration, now pinned by a test. No persistence, Room, scheduler, or receiver code was involved in this correction.

## Test assessment

Automated verification for the verified artifact: 88 unit tests across 19 classes, 0 failures / 0 errors / 0 skipped; lint 0 errors / 11 warnings; APK sha256 `4f1e34dd…d171dc`. `MainActivityReminderEntryTest` (9 tests) covers cold and warm delivery, rebinding, recreation retention without replay, re-applied-intent dedup, repeated delivery, supersede-before-open atomicity, plain-launch fallback, and the launch-mode contract; the launch-mode test exists because Robolectric cannot reproduce the OEM delivery decision that only device evidence settled. Plan scenarios 13 and 14 are now executed on device; OEM delivery semantics remain device-verifiable only.

## Disposition

**PASS — the blocking reminder-entry finding is resolved and device-verified.**

The 2026-09-13 22:30 CRITICAL finding (a reminder opened while the app is already running loses its explicit occurrence) is closed with device evidence for foreground, background, and real-notification-tap delivery, and the corrections did not regress the cold path, scheduling, decisions, or the user's data. The non-blocking findings of that review remain unchanged. No merge, PR, or release action was taken.

## Documentation updated with this review

- `docs/review/README.md` — `Latest review` pointer and archive index row.
- `docs/architecture/README.md` — feature status cell.
- `docs/feature-plans/medication-detail-dose-actions/README.md` — branch status.
- `docs/feature-plans/medication-detail-dose-actions/02-dose-occurrence-contract.md` — singleTop delivery step and test.
- `docs/feature-plans/medication-detail-dose-actions/06-verification-rollout.md` — device smoke status.
- `docs/memory.md` — verification evidence, device smoke evidence, backlog.
- `docs/operations.md` — warm-delivery verification, app-log filtering, shade automation, and IME typing rows.
- `AGENTS.md` — branch table row for `codex/feature-medication-detail-dose-actions`.
