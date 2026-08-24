# Medication Reminder — repository rules

## Project

Single-module Kotlin/Compose Android app under `app/`: Room persistence, private photo storage, medication schedules, device/manual time-zone modes, exact-alarm fallback, persistent non-swipeable reminder notifications, reboot/time-change receivers, dark Material 3 screens (home, editing, history, settings).

`compileSdk 37`, `targetSdk 35`, `minSdk 26`.

`local.properties` is machine-specific (SDK path) and intentionally ignored, as are generated `.gradle/`, `.kotlin/`, `build/`, and `app/build/` outputs.

## Tooling (verified on this workstation)

Android Studio `D:\AndroidStudio` (bundled JDK `D:\AndroidStudio\jbr`), Android SDK `%LOCALAPPDATA%\Android\Sdk` (Platform 37.0), Gradle `9.5.0`, AGP `9.3.0`, Kotlin `2.3.10`, KSP `2.3.11`.

## Branches

Each feature has its own branch; keep branches focused on their named behavior and add/update the related Room, scheduling, notification, and Compose tests before merging. Commit naming rule: `README.md`. Chinese-language feature scope: `app/AGENTS.md`.

| Branch | Tip | Status |
| --- | --- | --- |
| `feature-lockscreen-notification` | `9561fef` | Merged into master (`a583915`, v0.005). |
| `codex/feature-persistent-notification` | `a583915` | Tracks master; behavior suite-verified, device smoke pending. |
| `codex/feature-chinese-language` | `b594fc2` | Strings + Settings selector in master via v0.005; notification copy localization, tests, and device verification pending. |
| `codex/feature-per-slot-dosage` | `40dac11` | Not started. |
| `codex/feature-notification-guidance` | `40afac8` | Merged into master (`40afac8`, v0.018). |

## Verification

Canonical command-line build for this workstation. Managed shells may not expose `JAVA_HOME`/`GRADLE_USER_HOME`; with both empty, the wrapper can mis-create its lock under `C:\.gradle`, so set them explicitly. The temporary Gradle distribution (`...\Temp\CodexAndroidBuild\...`) no longer exists — invoke the wrapper through Java directly:

```bash
export JAVA_HOME='D:/AndroidStudio/jbr'
export GRADLE_USER_HOME='C:/Users/tiany/.gradle'
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest assembleDebug lintDebug --no-daemon --offline --console=plain
```

(PowerShell equivalent: `$env:JAVA_HOME = 'D:\AndroidStudio\jbr'; $env:GRADLE_USER_HOME = 'C:\Users\tiany\.gradle'` then the same `java -cp ...` line.)

`--offline` fits the cached-dependency state; omit only when resolving a new dependency, then re-add it for later runs. Using the local Android/Gradle caches is user-approved; do not re-ask for normal verification runs.

Known shell/tooling pitfalls (MSYS path mangling with adb, signature-mismatch installs, stale daemons, cache cleanup rules): `docs/operations.md`. Read it before running Gradle or adb in a new session.

## Device

```powershell
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.example.medicationreminder 1
```

## History

Verification evidence, device setup, tooling quirks, and backlog: `docs/memory.md`. Command-environment problems and fixes: `docs/operations.md`.
