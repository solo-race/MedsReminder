# Operation environment notes — Windows workstation

Problems hit while running build/verification commands in managed shells, with the fixes that worked. Read this before running Gradle or adb in a new session.

## Gradle invocation

| Problem | Symptom | Fix |
| --- | --- | --- |
| `JAVA_HOME` / `GRADLE_USER_HOME` empty in managed shells | Wrapper mis-creates its lock under `C:\.gradle`, or picks a wrong JVM | Export both before every invocation: `export JAVA_HOME='D:/AndroidStudio/jbr'; export GRADLE_USER_HOME='C:/Users/tiany/.gradle'` |
| Temporary Gradle distribution deleted (`C:\Users\tiany\AppData\Local\Temp\CodexAndroidBuild\gradle\gradle-9.5.0`) | `gradle.bat` path from older docs no longer exists; AGENTS.md canonical command fails | Run the wrapper through Java directly: `java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <tasks>` — distribution resolves from `C:/Users/tiany/.gradle/wrapper/dists` |
| New dependency added but `--offline` build fails | `No cached version of <artifact> available for offline mode` (e.g. lint needs the `-desktop` variant of a BOM-pinned artifact) | Drop `--offline` for ONE resolution run, then re-run offline builds as usual |
| Daemon holds locks during cache cleanup | `Unable to delete directory` / file-in-use errors on `.gradle` | Stop daemons first: `java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --stop`; verify with `"D:/AndroidStudio/jbr/bin/jps.exe" -l` |

## adb (device)

| Problem | Symptom | Fix |
| --- | --- | --- |
| `adb` not on PATH | `command not found: adb` | Use full path: `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe` (or `/c/Users/tiany/AppData/Local/Android/Sdk/platform-tools/adb.exe` under MSYS) |
| MSYS path mangling breaks redirects and tool paths | `adb pull` to `D:/...` fails with "cannot create file/directory"; screenshots pulled to `/c/...` are invisible to host-side tools | `cd /d/<project>` first so the process cwd is real, then use **relative** destination paths; never pass absolute Windows paths to `adb pull` in this shell |
| Screenshot capture | `exec-out screencap -p > file` works when cwd is real | Pull into a gitignored dir (e.g. `app/build/screens/`) and delete after use |

## Device install

| Problem | Symptom | Fix |
| --- | --- | --- |
| Signature mismatch between debug and release installs | `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package ... signatures do not match` | Uninstall before switching variants: `adb uninstall com.example.medicationreminder` |
| `install -r` failure masked by `| tail -1` | App keeps running old code after "successful" session | After variant switches always uninstall first; verify what is installed via `adb shell dumpsys package com.example.medicationreminder | grep -E 'versionName|lastUpdateTime'` |
| ColorOS blocks `pm grant` for shell | `SecurityException: grantRuntimePermission: Neither user 2000 nor current process has GRANT_RUNTIME_PERMISSIONS` | Grant permissions through the app's own Settings flow (UI automation) instead of `pm grant` |
| Stale process after reinstall | UI dump shows old strings; window handle unchanged | `adb shell am force-stop <pkg>` then relaunch |
| Chinese IME swallows `input text` | Typed text does not reach Compose fields | Switch device keyboard to English first |
| Keyboard shifts layout mid-flow | Taps at remembered coordinates miss targets | Re-dump `uiautomator` after every keyboard show/hide before tapping |
| OEM logcat buffer survives clear | `logcat -c *:E` leaves old FATAL entries in `-b crash` | Filter by timestamp: `logcat -d -b crash -s AndroidRuntime:E -t "<HH:MM:SS>.000"` and count only entries newer than the test run |

## Cache layout (this project)

- Project-local, safe to delete entirely, rebuilt by next build:
  - `.gradle/` (~730 MB at peak; `caches/9.5.0/generated-gradle-jars` alone 182 MB)
  - `app/build/` (~200 MB; `outputs/mapping/release` R8 mapping 42 MB)
  - `build/`, `.kotlin/`
- User-global, prune conservatively:
  - `C:/Users/tiany/.gradle/caches/9.5.0/transforms` — safe to prune entries older than ~1 week (freed ~650 MB); next build re-creates what it needs
  - `C:/Users/tiany/.gradle/daemon/9.5.0/*.out.log` — safe to delete
  - `C:/Users/tiany/.gradle/.tmp/gradle-*` — safe to delete files older than a few days
  - `C:/Users/tiany/.gradle/caches/modules-2` — dependency store; deleting forces re-download (~543 MB here). Keep unless disk pressure demands it.
- Never delete: `D:/AndroidStudio/jbr` (JDK), `%LOCALAPPDATA%/Android/Sdk` (SDK), `release.keystore` / `keystore.properties` (project root, signing keys).

2026-08-24 cleanup result: project `.gradle`+`build`+`.kotlin` removed (~933 MB), stale global temp files and daemon logs cleared, week-old transform entries pruned (~660 MB). Post-cleanup verification: full suite green (`testDebugUnitTest assembleDebug assembleRelease lintDebug`, offline).
