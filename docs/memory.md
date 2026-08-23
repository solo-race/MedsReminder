# Medication Reminder — session and verification memory

History and evidence only. Active rules live in `AGENTS.md`, `app/AGENTS.md`, and `README.md`; this file never overrides them.

## Commit timeline

| Commit | Date | What |
| --- | --- | --- |
| `2e6b4ec` | 2026-08-04 | v0.001 baseline: bootstrap of the single-module app (Room, photo store, schedules, time-zone modes, exact-alarm fallback, notification actions, receivers, dark M3 screens). |
| `5c94bb2` | 2026-08-05 | v0.002: usable version with basic features. |
| `3e59d19` | 2026-08-05 | v0.003. |
| `284f4b0` | 2026-08-23 | localization_proc_v0.001: English source + Simplified Chinese strings, DataStore `AppLanguage` preference, Settings selector; commit labeled "not verified". Reached master via the v0.005 merge. |
| `aaefe82` | 2026-08-23 | v0.003 (feature): persistent redacted lockscreen reminders with alias — `setOngoing(true)`/`setAutoCancel(false)`, `ReminderDismissReceiver` swipe resurrection, alias-based lockscreen title. Verified. |
| `8a6f9c7` | 2026-08-23 | v0.004: dose decisions deduped per schedule-zone local day. |
| `5057507` | 2026-08-23 | v0.005: merge of the lockscreen side branch (`284f4b0..8a6f9c7`) into master. |
| `eb8229b` | 2026-08-23 | v0.006: fix Activity owner lookup crash in `withAppLanguage` (`LocalizedContext` keeps the Activity in the base-context chain). |
| `794adc7` | 2026-08-23 | v0.007: move session history/verification evidence to `docs/memory.md`, slim root `AGENTS.md` and `README.md`, ignore generated `.kotlin/`. |
| `58c4f66` | 2026-08-23 | v0.008: release signing config (AGP 9 DSL, keystore.properties + env), R8 minify + resource shrink, `versionName 1.0.0-beta.1`, CI and release workflows. |
| `d52bbf8` | 2026-08-23 | v0.009: mark `gradlew` executable for CI. |
| `238c085` | 2026-08-23 | v0.010: setup-java v5, beta release history, README commit reference. |
| `84adb3f` | 2026-08-23 | Add MIT license (copyright `solo-race`); plain message outside the version format. |

| `cf7ae74` | 2026-08-24 | v0.011: fix localized-context crashes (time picker `BadTokenException`, exact-alarm `startActivity` exception) via resources-only ContextWrapper; refresh notifications/exact-alarm states on resume (`LifecycleResumeEffect`). Verified on device, debug + release. |
| `a84bce0` | 2026-08-24 | v0.012: align time/weekday formatting with app language — provide localized `LocalConfiguration`, per-call locale-aware formatters replace static `DateTimeFormatter`s. Verified EN/ZH on device. |
| `88afc03` | 2026-08-24 | v0.013: consistent Material icons in bottom navigation (`material-icons-extended`, BOM-pinned) replacing text glyphs that rendered at unequal sizes; all three icons now standard 24dp. User-confirmed on device. |
| `67c5e7f` | 2026-08-24 | v0.014: merge `codex/fix-localized-context-crashes` into master. |

## Beta release

- First beta `v1.0.0-beta.1` published 2026-08-23 as a GitHub release with the signed, minified `app-release.apk`; built by `.github/workflows/release.yml` (tag `v*` → signed `assembleRelease` → `softprops/action-gh-release`).
- Signing: local `release.keystore` + `keystore.properties` (both gitignored); CI gets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` repo secrets. Key alias `medsreminder`; key password equals store password (Windows keytool ignored `-keypass` — keep them equal if the keystore is regenerated).
- Second beta `v1.0.0-beta.2` published 2026-08-24 from tag on master `67c5e7f`; same workflow and signing path as beta.1. Contains v0.011–v0.014. CI run `32655679291`; CI-built APK installed and smoke-tested on device (Settings screen renders, nav icons equal size, 0 crashes).

## Verification evidence

- 2026-08-04 session (v0.001 era): `testDebugUnitTest`, `assembleDebug`, `lintDebug` passed; 5 scheduling tests; lint 0 errors / 14 warnings; APK at `app/build/outputs/apk/debug/app-debug.apk`; Android Studio sync not confirmed (IDE first-run blocked it).
- 2026-08-23 session (v0.005 tree): full suite green via `java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest assembleDebug lintDebug` with `JAVA_HOME=D:\AndroidStudio\jbr`: 14 unit tests, 0 failures (`NextDoseCalculatorTest` 9, `ReminderRedactionTest` 5); lint 0 errors / 16 warnings; report `app/build/reports/lint-results-debug.html`.
- 2026-08-23 reconciliation: `codex/feature-persistent-notification` fast-forwarded `5c94bb2..5057507`; no code authored; conformance anchors spot-checked; device smoke blocked — OPPO not attached (adb kill/start/reconnect all empty).

## Device smoke evidence (2026-08-23, PLB110 / ColorOS, feature-persistent-notification)

- Reminder lifecycle: 3 posted ids 101–103, flags `ONGOING_EVENT`, 2 actions (Taken/Skipped), channel `medication_reminders`. Swipe-dismiss resurrects via `deleteIntent` (user-observed). Taken/Skipped clear the tray and write dose events with `scheduledForEpochMillis` equal to the alarm's `origWhen`.
- Delete medication (UI): confirm dialog (Chinese on device locale) removes rows from `medications`, `medication_schedules`, `dose_times`, `dose_events`, cancels live alarms (0 remaining; residual `REMIND.` hits in `dumpsys alarm` are `*walarm*` history), and clears visible reminders via the mutation hook.
- ColorOS OEM behaviors (reported, no workarounds added per plan contingency):
  - `BOOT_COMPLETED` delivery: first `SKIPPED … reason: oplus startup`, later `DEFERRED … reason: mBroadcastConsumerDeferApply`; app process never started post-boot; no re-post. `MY_PACKAGE_REPLACED` same: `SKIPPED … reason: mBroadcastConsumerSkip`. Receiver re-post logic never executed on this device through OS paths; logic itself covered by `NextDoseCalculatorTest` (`mostRecentOverdueOccurrence`, 4 cases).
  - `am force-stop` clears the tray, cancels alarms, and sets the stopped state (blocks broadcasts until an explicit launch).
  - `am kill` refused to kill the process; `run-as … kill -9 <pid>` killed it and ColorOS cleared the tray without firing the delete intent. Process-death path not user-reachable.
  - Lockscreen: notifications never render on the 乐划锁屏 lockscreen and its pull-down is quick-settings only, despite `vis=PUBLIC` and `lock_screen_show_notifications=1` / `lock_screen_allow_private_notifications=1`. Lockscreen presentation is OEM-side.
- Scheduling on this device: inexact fallback alarm window is widened by ColorOS to 1h (`windowLength=3600000` in dumpsys); observed fires ~1–2 min after dose time. `install -r -g` is the only working grant path for POST_NOTIFICATIONS. A relaunch whose init lands after the dose time correctly schedules the next day — verify the alarm's `origWhen` epoch right after relaunch instead of waiting blind.
- App UI language on this device follows device locale (Chinese), contrary to the "default English" assumption; mixed-language evidence is expected on the localization branch, out of scope here.

## Device environment

- Physical device: OPPO Find X8s+, ADB model `PLB110`, USB debugging enabled. Android SDK Google USB Driver installed; Windows uses `winusb.inf`. If `adb devices` stays empty, Android's OEM USB-driver guidance applies.
- v0.001-era smoke on PLB110: APK install/launch OK; dark home screen, add-medication fields (dosage, note, photo control, schedule controls), default three daily times, weekday/time-zone controls, save + persistence after force-stop/relaunch, Settings permission screen; logcat showed no crash entries.
- No emulator available: no AVD/system image configured.

## Tooling quirks

- The managed shell cannot exec `.\gradlew.bat` / `./gradlew` (Win32 error 193). Use the canonical PowerShell bootstrap in `AGENTS.md`, or the `java -cp` wrapper invocation above.
- With empty `JAVA_HOME`/`GRADLE_USER_HOME`, the wrapper may create its lock file under `C:\.gradle`; set both variables.

## Backlog

- Device verification matrix: notification permission denial/grant, exact-alarm permission prompt, manual-zone travel prompt, 30-day history retention still open. Done on device: permission grant via `install -g`, inexact-alarm fallback, Taken/Skipped actions, force-stop behavior, reboot/package-replace recovery (OEM-blocked, see smoke evidence), delete-medication clearing.
- Editor flows still unexercised on device: add/remove times, long notes, photo picker/camera/replacement/removal, enable/pause reminders, history review. Exercised: delete medication.
- Planned tests not yet written: Room, photo store, repository, scheduler DST/zone-change cases, Compose UI/instrumented.
- Lint: 16 non-blocking warnings to review; plan AGP 10 migration (external Kotlin / legacy DSL settings), add Android 12 data-extraction rules if backup behavior changes, raise `targetSdk` only after device testing.
- Android Studio project sync still unconfirmed end-to-end.
