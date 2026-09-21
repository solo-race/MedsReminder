# 2026-09-21 01:59 SGT — Cold-process alarm reliability verification

## Scope and disposition

Verification only on PLB110 / OPPO Find X8s+ / ColorOS, serial `3B158L00JL000000`, package `com.example.medicationreminder`. No implementation or test changes, installation, force-stop, synthetic receiver broadcast, commit, push, merge or PR occurred.

**Baseline: OEM_BLOCKED. Single-variable autostart-enabled control: PASS.** The current APK still encounters the OEM startup restriction recorded on 2026-09-13 when its process is absent and autostart is disabled. Exact registration succeeds; ColorOS blocks receiver startup at delivery. This is not an established App code defect. No production-code change is recommended from this evidence. This review does not close the feature or authorize merge/release, and does not supersede the earlier warm-entry verification outside this scope.

## Reviewed implementation and device state

- Branch `codex/feature-medication-detail-dose-actions`, HEAD `75b84bb11b92bf56f6744b8706de2692eba7fc55`, plus 14 modified and 2 untracked preexisting files. Full status and structured diff are archived below.
- Local APK and pulled installed APK SHA-256 both `06A94A20A4D8DAEA6B2C0C705954DF7DCF1C5FA6930CBF20897D10C458060C8D`; 20,760,602 bytes. No reinstall needed.
- Structured `worktree-diff.json` SHA-256 `0FD1DB1F72E57F99D2096EDD965C29A9DD6B30BD43B5347DA15869C7D15108F5`.
- `worktree-content-manifest.txt` SHA-256 `8EB1E4BDEA3A484022FC2D043689D95ECA8214F1AB3B13550C68004483306DCC`. It lists exact-byte hashes of all 16 preexisting changed/untracked files, including both new tests, excluding this report and evidence artifacts. These identifiers distinguish the dirty worktree from HEAD; APK equality identifies the installed binary without claiming a fresh source-to-build reproduction.
- Version code 1 / `1.0.0-beta.1`; first install `2026-09-08 19:48:10`, last update `2026-09-21 00:39:24`, unchanged throughout.
- Initial PID 7695, `stopped=false`, exact-alarm appop `allow`, `POST_NOTIFICATIONS granted=true`. Permissions were checked separately and never changed.

All times below are 2026-09-21 UTC+08 / SGT unless a different date is given.

## Findings ordered by impact

### OEM limitation — delivery to a dead process is blocked with original autostart setting

The sole temporary medication was `[E2E-COLD-PROCESS-ALARM]`, medication/schedule ID 8, device zone `Asia/Shanghai`, daily schedule. The baseline slot was 24, scheduled for `1789925400000` (01:30).

After backgrounding, `run-as` killed PID 7695 without force-stop. At 01:26:50 the process was absent, package remained `stopped=false`, and `REMIND.24` remained registered as `RTC_WAKEUP`, `window=0`, `exactAllowReason=permission`, with Alarm clock trigger 01:30. PID was still absent at 01:29:59. All three preconditions were satisfied.

At 01:30:00.014 AlarmManager logged `sending alarm` for `REMIND.24` (+14 ms). At 01:30:00.024 `OplusAppStartupManager` logged `prevent start` for this package's `ReminderReceiver`, by `broadcast system[alarmManger]`. At 01:30:27 there was still no App PID. No package NotificationRecord or active slot-24 successor was present after fire. This is **OEM_BLOCKED**, not APP_FAILURE or INVALID_TEST.

### Single-variable control — allowing App autostart enables the complete cold-process chain

Before adjustment, App background behavior was already fully allowed and the package was in the device-idle user whitelist. Those settings were left unchanged. The target App's autostart setting alone was changed OFF → ON for the controlled fire.

Navigation incident: an unrelated Music switch was accidentally enabled, then restored OFF before the control began; the enabled-list comparison showed only the target App added. A Telegram confirmation was canceled without changing its setting. These incidents are disclosed rather than treating the navigation sequence as error-free.

The same temporary medication was edited to 01:45, yielding slot 25, `scheduledFor=1789926300000`. At 01:40:15 PID 17239 had been killed with `run-as`, PID was absent, `stopped=false`, and the exact `REMIND.25` alarm remained. PID was still absent at 01:42:38 and 01:44:07. The App was not opened before delivery.

| Evidence | Direct runtime result |
| --- | --- |
| AlarmManager send | 01:45:00.007, +7 ms |
| Process startup | 01:45:00.034, new PID 31220, for ReminderReceiver broadcast |
| Receiver | Enqueue 01:45:00.008; dispatch 01:45:00.087; DELIVERED, finish +1.157 s |
| Notification | ID 125; postTime `1789926301236`; screenshot confirms visible reminder |
| Notification when | `1789926301231`; posting-time evidence, not the scheduledFor value |
| Next occurrence | `REMIND.25`, `1790012700000`, 2026-09-22 01:45, exact Alarm clock |

Control result: **PASS**. OFF → ON changed OEM_BLOCKED → PASS without changing the APK. The most likely causal explanation is ColorOS autostart policy, not failure to register an exact alarm. The target App's autostart was restored OFF afterward; the original 8 allowed / 89 disallowed totals and Music OFF were restored. Therefore the original cold-process reliability limitation remains present on the device at handoff.

### Evidence gaps — received extras and exact-permission recovery

Broadcast extras remained `STRIPPED`. Directly observed: `requestCode=25`, action `REMIND.25`, notification action identities `TAKEN.25.1789926300000` / `SKIPPED.25.1789926300000`, the database schedule, matching alarm trigger and next occurrence. Full received `{medicationId, doseTimeId, scheduledFor, zoneId}` consistency is **source/runtime consistency inferred**, not directly observed. No logging probes or production changes were introduced.

F/G remain **BLOCKED / NOT EXECUTED**. The system exact-alarm page remained grey and ON. Appops remained allow; `canScheduleExactAlarms()=true` is inferred from the guarded successful exact registration, not directly instrumented. Current Manifest registers SystemEventReceiver for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. There is no direct evidence explaining why the switch is grey, and no forced appops manipulation was attempted. Previously reported unit-test results do not establish device recovery behavior.

## Cold-start rebuild and identity stability

At 01:50:41 PID 31220 was killed without force-stop; PID absent and `stopped=false` were confirmed. Normal launch produced PID 32703 at 01:50:44. Before and after launch, active alarms contained exactly one each for slots 6, 7, 8, 9, 25, with unchanged trigger epochs:

| Slot | Trigger epoch |
| --- | --- |
| 6 / 7 | `1789948800000` each |
| 8 | `1789965540000` |
| 9 | `1789992000000` |
| 25 | `1790012700000` |

New Alarm objects with stable actions and triggers support successful re-registration without duplication. The `scheduleAll()` call itself is inferred from current Application source plus runtime rebuilding, not directly logged. This single cold-rebuild comparison supports idempotency for the observed schedule; it is not an exhaustive lifecycle stress test.

## Data preservation and closeout

The temporary medication was deleted through App UI with its name visible in the confirmation. Read-only SQLite comparisons of before/final snapshots found every row identical in medications (2), medication_schedules (2), dose_times (4), dose_events (14), room_master_table (1); user_version remained 3. The two original photo files and preferences file retained identical listing, sizes and mtimes; file contents were not byte-compared.

Final active reminders are only original slots 6/7/8/9; temporary slots 24/25 no longer have active alarms. Installation times and both permissions remained unchanged. No original medication, schedule, history or photo was changed. No existing repository files were deleted. Only this review and the archive pointer were added/updated outside generated evidence; production, test and Gradle files were untouched.

## Architecture and test implications

Exact-alarm authorization and successful registration do not guarantee ColorOS will start a dead application process. Both receiver delivery and successor scheduling depend on that OEM startup decision. The controlled success supports the existing scheduling/notification chain under permitted startup; it does not establish a code change that can bypass the OEM policy.

Do not modify production code on this evidence. Keep App defects, OEM restrictions and evidence gaps separate. Subsequent verification should cover F/G on a device where permission switching is safely supported, and repeat cold-process reliability under the user's chosen persistent autostart policy. Enabling it permanently requires a separate user decision; this run restored original settings.

## Evidence archive

Local raw artifacts and small extracts: [`app/build/e2e-cold-process-20260921-0122/`](../../app/build/e2e-cold-process-20260921-0122/). These generated artifacts are not tracked by Git and must accompany the review for independent raw-log inspection.

- `01-environment.txt`, `02-alarm-before-kill.txt`, `03-process-after-kill.txt`, `04-alarm-after-kill.txt`.
- `05-fire-key-lines.txt`, `06-process-after-fire.txt`, `06-receiver-after-fire.txt`.
- `07-notification-after-fire.txt`, `07-occurrence-identity.txt`, `08-alarm-after-fire.txt`, `08-rebuild.txt`.
- `09-data-before-after-summary.txt`, `10-result-summary.txt`; these are explicitly analysis summaries, not raw quotations.
- Extracts 01–08 identify source filenames, raw-file timestamps, filters and unchanged selected lines. Complete dumpsys/logcat files remain alongside them.
- `autostart-before.png`, `autostart-single-variable.png`, `autostart-restored.png`, `control-notification-visible.png`, `exact-settings-final.png`, `cleanup-confirm.png`.
