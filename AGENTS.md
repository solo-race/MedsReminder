# Medication Reminder — Next-session handoff

## Current state

- The repository is bootstrapped as a single-module Kotlin/Compose Android app under `app/`.
- The implementation includes Room persistence, private photo storage, medication schedules, device/manual time-zone modes, exact-alarm fallback scheduling, notification actions, reboot/time-change receivers, and dark Material 3 screens for home, editing, history, and settings.
- Tooling used successfully in the current environment: Android Studio `D:\AndroidStudio`, its bundled JDK, Android SDK Platform `37.0`, Gradle `9.5.0`, AGP `9.3.0`, Kotlin `2.3.10`, and KSP `2.3.11`. The app uses `compileSdk 37`, `targetSdk 35`, and `minSdk 26`.
- `local.properties` is intentionally ignored because it contains the machine-specific SDK path. Generated `.gradle/`, `.kotlin/`, `build/`, and `app/build/` outputs are also ignored.
- Baseline commit: `8889b9a` (`20260804_medstime_v0.001`).

## Planned feature branches

Each requested improvement has its own branch so implementation and verification can proceed independently in later conversations. All four branches start from the current baseline; no feature implementation has started yet.

- `codex/feature-per-slot-dosage`: add an opt-in per-time-slot dosage mode. Preserve the current fixed dosage as the default, persist an amount for each enabled `DoseTime`, expose the editor controls, and carry the selected amount through home, notifications, and dose history.
- `codex/feature-lockscreen-notification`: make reminder notifications visible on the lock screen while using generic redacted content so the medication name is never exposed. Verify channel visibility, notification redaction, and action behavior on a physical device.
- `codex/feature-persistent-notification`: make an active reminder non-dismissible by swipe and clear it only after `Taken` or `Skipped`. Preserve rescheduling and dose-event recording for both actions, including process/reboot recovery.
- `codex/feature-chinese-language`: add an English/Chinese interface option in Settings, persist the selection locally, add localized string resources, and verify the main screens, settings, notifications, and permission text in both languages.

Keep each feature branch focused on its named behavior. Add or update the related Room, scheduling, notification, and Compose tests on that branch before merging it.

## Verified in the current session

- `testDebugUnitTest`, `assembleDebug`, and `lintDebug` all pass.
- The scheduling unit suite contains 5 passing tests with no failures or errors.
- Android lint reports 0 errors and 14 non-blocking warnings; the report is at `app/build/reports/lint-results-debug.html` after a build.
- The debug APK is emitted at `app/build/outputs/apk/debug/app-debug.apk`.
- Android Studio was launched with this project, but its first-run/startup workflow prevented confirmation of a completed IDE sync. The command-line Gradle build is the authoritative verification so far.
- A physical OPPO Find X8s+ (ADB model `PLB110`) was detected after USB debugging was enabled. The Android SDK Google USB Driver package was installed; Windows is using its `winusb.inf` ADB interface. Android's OEM guidance still applies if a vendor-specific driver is needed.
- The debug APK installed and launched successfully on the phone. Smoke testing covered the dark home screen, add-medication fields (including the default three daily times, weekdays, and time-zone mode), saving a medication, persistence after force-stop/relaunch, and the Settings permission-status screen. The captured logcat window contained no matching app crash entries.
- No emulator test was run because no AVD/system image is configured; the physical-device smoke test is the current device verification.

## Remaining work for the next session

1. Complete Android Studio first-run setup and confirm that this project opens and syncs without errors. If the IDE still cannot sync, capture the exact Gradle/SDK error before changing versions.
2. On the connected physical device (and optionally an API 35+ emulator), exercise the remaining main flows: edit/delete medication, add/remove times, weekday selection, device versus manual time zone, long notes, photo picker, camera capture, photo replacement/removal, enable/pause reminders, and history review.
3. Exercise Android integration behavior on a device/emulator: notification permission denial/grant, exact-alarm denial/fallback, Taken and Skipped notification actions, reboot recovery, clock/time-zone changes, and the manual-zone travel prompt. Confirm old dose history is retained only for 30 days.
4. Add the planned Room, photo-store, repository, scheduler, and Compose UI/instrumented tests. Expand scheduling tests around daylight-saving transitions and device-zone changes as needed.
5. Review the 14 lint warnings. In particular, plan the AGP 10 migration away from external Kotlin/legacy DSL settings, add Android 12 data-extraction rules if backup behavior changes, and raise `targetSdk` only after testing the newer behavior.
6. Re-run the complete verification command and amend or create the next commit according to the naming rule in `README.md`.

## Useful commands

From the repository root, use the Gradle wrapper when its distribution is available:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

### Project verification backbone (Windows)

The verified routine command-line path for this workstation uses the Android
Studio bundled JDK and the installed Gradle 9.5 distribution. The managed
shell may not expose `JAVA_HOME` or `GRADLE_USER_HOME`; when those variables
are empty, the wrapper can incorrectly try to create its lock file under
`C:\.gradle`. Do not repeatedly retry that failing path. Use this bootstrap
from the repository root instead:

```powershell
$taskJavaHome = 'D:\AndroidStudio\jbr'
$env:JAVA_HOME = $taskJavaHome
$taskGradleHome = 'C:\Users\tiany\.gradle'
$env:GRADLE_USER_HOME = $taskGradleHome
$gradleCommand = 'C:\Users\tiany\AppData\Local\Temp\CodexAndroidBuild\gradle\gradle-9.5.0\bin\gradle.bat'
& $gradleCommand testDebugUnitTest assembleDebug lintDebug --no-daemon --offline --console=plain
```

This command has passed the unit tests, debug APK assembly, and Android lint.
`--offline` is appropriate when the project dependencies are already cached;
omit it only when intentionally resolving a newly required dependency. If the
temporary Gradle distribution is unavailable, use the wrapper after setting
the same Java and Gradle cache variables, or install/configure a normal
Gradle 9.5 distribution before building. Routine verification for this
project is user-approved to use the local Android/Gradle caches outside the
workspace; do not ask for separate approval on every normal verification run,
but retain the command's explicit cache paths and task list.

For a connected device, verify ADB and install the debug APK with:

```powershell
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.example.medicationreminder 1
```

If the wrapper distribution cannot be downloaded, use the Android Studio JDK with the installed Gradle 9.5 distribution available in the current workstation's temporary build directory, or configure a normal Gradle 9.5 installation before building.
