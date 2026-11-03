# Medication Reminder — repository rules

## Project

Single-module Kotlin/Compose Android app under `app/`: Room persistence, private photo storage, medication schedules, device/manual time-zone modes, exact-alarm fallback, persistent non-swipeable reminder notifications, reboot/time-change receivers, dark Material 3 screens (home, editing, history, settings).

`compileSdk 37`, `targetSdk 35`, `minSdk 26`.

`local.properties` is machine-specific (SDK path) and intentionally ignored, as are generated `.gradle/`, `.kotlin/`, `build/`, and `app/build/` outputs.

## Documentation entry points

Use these repository documents as the default context before implementation or review work:

1. `docs/review/README.md` — canonical review archive index. **Always follow and read its `Latest review` pointer first** before making architecture or feature implementation decisions. New reviews are archived as timestamp-locked documents; do not overwrite or retitle older reviews.
2. `docs/architecture/README.md` — architecture index and cross-layer contracts. It also links back to the canonical review archive and records the default reading order.
3. `docs/feature-plans/` — feature-specific implementation plans. Read the relevant plan after the latest review and architecture index; if a newer review corrects an older plan assumption, update the plan before implementation proceeds.
4. `docs/memory.md` — verification evidence, project history, device setup notes, and backlog.
5. `docs/operations.md` — command-environment problems, Gradle/adb operational rules, and workstation-specific fixes.

For review work, create a new file under `docs/review/` using the review time in Singapore time (`Asia/Singapore`) in both filename and H1 title, then update `docs/review/README.md` so its `Latest review` pointer references that new document in the same change.

## Tooling (historical Windows workstation)

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
| `codex/feature-repost-missed-reminders` | `4dc52ee` | v0.019: Settings-gated self-heal re-posting; suite + device verified. |
| `codex/feature-medication-detail-dose-actions` | `2e05c90` (historical source) | Implementation through Phase 6 and reminder-entry/startup corrections are included in master via `7de78b4` / `701ffec`. Later autostart/manual-guide, Home-opened Detail pending selection, keyed state and DST corrections (`2cbdad2`) are included in master `de2f6b6`; see the canonical latest review. Historical automated/device results apply only to their recorded scope; the latest correction's device acceptance remains pending. This records merged ancestry, not source-branch deletion or new device acceptance. |
| `codex/feature-interval-reminders-dose-summary` | `2b6822e` (implementation baseline) | Master `de2f6b6` integrated with history preserved. Supervisor relayed independent PASS for plan `be77e1a`, P1 `2f75eb6`, P2 `5cb5fa0` and P3 `86faa89`. P4 delivered (B), awaiting final independent review: 175 tests pass, lint has 0 errors, debug build succeeds, beta.5/code 5 prepared. Device acceptance and formal release signing remain pending. |

## Verification

For the interval-reminder feature, the user has directed the main assistant to pull the relevant commit into its own VM for subsequent builds and automated validation. Do not repeatedly attempt Android builds in the Codex cloud environment. The user later authorized an initial validation attempt and continuing static work when blocked; distinguish actual results from deferred VM/local checks. VM JDK/SDK readiness and results must be verified there; old reports, including the 135-test correction report, do not establish current feature verification. See the feature plan's validation section.

P4 resolved the cloud Robolectric lock using its supported local dependency directory with an already cached SDK jar. The optional Gradle property `-ProbolectricDependencyDir=<directory>` forwards `robolectric.dependency.dir` to unit tests; ordinary runs retain the default resolver. The fresh full run passed all 175 tests, including Room/notification integration. See [local validation steps](docs/feature-plans/interval-reminders-dose-summary/local-validation.md) and `docs/memory.md` for exact commands, prior failures, and device/signing gaps. No permission or network-policy changes were made.

The following command is a historical Windows workstation recipe, not the current VM's setup. Managed shells on that workstation may not expose `JAVA_HOME`/`GRADLE_USER_HOME`; with both empty, the wrapper can mis-create its lock under `C:\.gradle`, so set them explicitly. The temporary Gradle distribution (`...\Temp\CodexAndroidBuild\...`) no longer exists — invoke the wrapper through Java directly:

```bash
export JAVA_HOME='D:/AndroidStudio/jbr'
export GRADLE_USER_HOME='C:/Users/tiany/.gradle'
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest assembleDebug lintDebug --no-daemon --offline --console=plain
```

(PowerShell equivalent: `$env:JAVA_HOME = 'D:\AndroidStudio\jbr'; $env:GRADLE_USER_HOME = 'C:\Users\tiany\.gradle'` then the same `java -cp ...` line.)

`--offline` matched that workstation's cached dependencies; it is not a claim about the assistant VM's caches. Using the local Android/Gradle caches is user-approved; do not re-ask for normal verification runs in the designated environment.

Known shell/tooling pitfalls (MSYS path mangling with adb, signature-mismatch installs, stale daemons, cache cleanup rules): `docs/operations.md`. Read it before running Gradle or adb in a new session.

## Device

```powershell
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.example.medicationreminder 1
```

## History

Verification evidence, device setup, tooling quirks, and backlog: `docs/memory.md`. Command-environment problems and fixes: `docs/operations.md`.

## Current requested feature plan

The interval-reminder and dose-summary feature is implemented on `codex/feature-interval-reminders-dose-summary`. Its implementation baseline is merge `2b6822e`, which combines the original plan with master `de2f6b6` and correction `2cbdad2`. Its entry point is [`docs/feature-plans/interval-reminders-dose-summary/README.md`](docs/feature-plans/interval-reminders-dose-summary/README.md). The earlier medication-detail implementation is already in master; the supervisor relayed independent plan/P1/P2/P3 PASS (commits above). P4 is B, pending final independent review, with fresh full-suite/lint/debug evidence and explicit device/signing gaps. Earlier failed or deferred checks remain historical records, not retroactive passes. Preserve existing Home-opened Detail pending selection, explicit notification/keyed-state/DST fixes and autostart manual guidance. Follow the plan's current phase and independent review rules. Do not modify `master`, create PRs, merge, tag, or release without the user's explicit instruction.
