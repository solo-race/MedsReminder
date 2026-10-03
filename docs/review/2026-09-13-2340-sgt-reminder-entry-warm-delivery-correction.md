# 2026-09-13 23:40 SGT — Reminder Entry Warm-Delivery Correction

Reviewed repository: `solo-race/MedsReminder`

Reviewed branch: `codex/feature-medication-detail-dose-actions`

Reviewed ref: `5e28a09` (`fix: keep reminder occurrence on warm delivery`), applied on top of `175b1ec`. Previous review: `2026-09-13-2230-sgt-device-smoke-verification.md` (disposition CHANGES REQUIRED; one blocking finding in the reminder entry path).

Review type: corrective-implementation review of that blocking finding. This review ran the canonical automated gate, added runtime coverage, and used an independent code-review pass. No device transport was available in this session, so the device smoke scenarios recorded in the feature plan remain unexecuted for this correction.

## Scope

1. Implement the four recommended corrections of the 2026-09-13 22:30 review.
2. Keep the correction inside the Activity/Compose ingress; no persistence, Room schema, scheduler, or receiver change.
3. Replace the notification-path source-text contract assertion with runtime coverage of the warm path.
4. Re-run the canonical verification gate from `AGENTS.md`.

Out of scope: device smoke (no device this session), OEM delivery semantics, and the non-blocking findings of the previous review (ColorOS alarm-start blocking; dose-boundary presentation staleness; occurrence changeover frame).

## Implementation

- `MainActivity.kt` — one `applyIntent(intent, reapplied)` parses the reminder extras and hands a `ReminderEntry(medicationId, occurrence?)` to the Activity-scoped `MedicationViewModel`. It is called from `onCreate` (the launch intent, re-read on every Activity creation) and from a new `onNewIntent` override that calls `super.onNewIntent(intent)` and `setIntent(intent)`. `navigationDoseOccurrenceOrNull` still fails closed on an incomplete or malformed occurrence identity.
- `MedicationViewModel.kt` — `reminderEntry` is the pending delivery, `reminderOccurrence` is the occurrence Detail binds. `onReminderIntent(entry, reapplied)` deduplicates by value against the last delivered entry **only** when the launch intent is being re-applied, so recreation does not replay navigation while a repeated notification tap still counts. `reminderEntryOpened()` retires the pending delivery atomically, binds its occurrence, and returns the retired entry; `clearReminderOccurrence()` returns Detail to a schedule-derived occurrence when it is opened from Home.
- `MedicationApp.kt` — the entry effect consumes the delivery and navigates `Routes.detail(opened.medicationId)` with `launchSingleTop = true`, so the navigation target, the bound occurrence, and the retired delivery are the same value by construction and a repeated reminder cannot push duplicate Detail entries. Detail receives `reminderOccurrence?.takeIf { it.medicationId == id }`.
- `ui/MedicationAppEntry.kt` — deleted. The composition-local creation-time occurrence constant is gone; the entry is delivered through ViewModel state instead.
- `MainActivityReminderEntryTest.kt` (new) — Robolectric coverage: cold binding, warm binding, second reminder rebinding, recreation retention without replay, re-applied-intent dedup, repeated delivery re-opened, supersede-before-open atomicity, and plain-launch fallback. `MedicationDetailNavigationContractTest.kt` loses only the notification-path source-text test; its remaining wiring assertions are unchanged.

## Findings

### MINOR (found by this review's independent pass, fixed before the final gate) — the entry could be retired by a delivery other than the one navigated to

The first version retired whatever was pending while the effect navigated with the entry captured by its recomposition. Two deliveries landing inside the same main-thread dispatch window could bind the newer delivery's occurrence, drop that newer delivery, and still navigate to the older one — the same "wrong occurrence for the shown medication" failure mode the correction removes. Fixed by retiring atomically: `reminderEntryOpened()` now reads the pending value once, binds and clears it, and returns it; `MedicationApp.kt` navigates with the returned value. The independent re-review confirmed the mismatch closed with no new hazard: same-frame double delivery is now latest-wins, matching the single-slot pending field and `launchSingleTop`.

No other findings: requirements 1–6 of the previous review are satisfied, no dangling reference to the deleted entry module remains in `app/src`, and no persistence, scheduler, or receiver contract changed.

## Evidence

- Gate run before the atomicity fix: `testDebugUnitTest assembleDebug lintDebug` **BUILD SUCCESSFUL** in 1 m 47 s; 86 tests / 19 classes / 0 failures / 0 errors / 0 skipped; lint 0 errors; APK sha256 `e342908e…2de900`.
- Final gate run: `testDebugUnitTest assembleDebug lintDebug` **BUILD SUCCESSFUL** in 1 m 28 s (59 actionable tasks); 87 tests / 19 classes / 0 failures / 0 errors / 0 skipped; lint 0 errors / 11 warnings; APK `app/build/outputs/apk/debug/app-debug.apk` 20,758,986 bytes, sha256 `99e86d5e…a9bd1`.
- `MainActivityReminderEntryTest`: 8 tests, 0 failures. The warm-path assertions (`reminderOccurrence` bound with the pending delivery retired) can only be produced by the Compose effect consuming the delivery, so an ingress without `onNewIntent` fails `warmDeliveryBindsTheSameOccurrenceAsAColdLaunch`.
- Independent code review (separate agent, hi effort, verdict recorded): requirements 1–6 satisfied; no blocking finding; the one MINOR finding above was fixed and re-verified as closed.

## Architecture assessment

The correction keeps the reviewed layering intact: occurrence identity travels from the reminder intent into immutable ViewModel state; Detail stays read-only; navigation stays a Compose effect; Home-opened Detail still derives its occurrence from the schedule. What changed is the entry contract itself — it is now bound to reminder delivery for the whole Activity lifetime rather than to a single Activity creation, and the bound occurrence survives configuration change because it lives in the Activity-scoped ViewModel instead of relying on framework relaunch-intent semantics. No Room, schema, scheduler, or receiver contract is touched.

## Test assessment

The suite can now detect this defect class instead of asserting source text: `ActivityController.newIntent(Intent)` drives warm delivery, `recreate()` drives configuration change, and the assertions read the state the Detail screen actually binds. Still unverified: OEM delivery. Device smoke must confirm on PLB110 that a real notification tap with the process alive reaches `onNewIntent`, renders `提醒对应服药`, and that Taken/Skipped then apply to the reminder's occurrence (plan scenarios 13–14).

## Disposition

**Fix implemented; automated verification green; device smoke pending.**

The previous review's blocking finding is addressed in source with runtime coverage and a green canonical gate, so the feature is no longer blocked *in code*. It must not be merged, released, or closed until the device smoke re-runs plan scenarios 13–14 on the OPPO/ColorOS device (reminder opened with the task alive while foregrounded and backgrounded; the same reminder opened twice; rotation with the reminder Detail open). Reminder delivery is OEM-mediated and cannot be verified from this workstation. The branch remains `codex/feature-medication-detail-dose-actions`; no merge, PR, or release action was taken.

## Documentation updated with this review

- `docs/review/README.md` — `Latest review` pointer and archive index row.
- `docs/architecture/README.md` — feature status cell.
- `docs/feature-plans/medication-detail-dose-actions/README.md` — branch status.
- `docs/memory.md` — verification evidence and backlog.
- `AGENTS.md` — branch table row for `codex/feature-medication-detail-dose-actions`.
