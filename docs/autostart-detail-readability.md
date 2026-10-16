# Autostart guidance and detail readability

Base: `2e05c90` on `codex/feature-medication-detail-dose-actions`.
Work branch: `codex/fix-autostart-detail-readability`.

## Changes

- Settings includes a user-initiated OEM autostart navigation entry. Known OEM activities are best-effort; missing/private activities fall back to this application's system details, then system settings. All failures return guidance rather than crash. Opening a screen is not permission evidence. No permission flag is persisted or automatically changed, and no broad package query permission is requested.
- Detail uses separated full-width cards for medication identity/dosage, current occurrence, schedule and notes. Dosage units are never inferred. Chinese times use 24-hour notation, daily schedules collapse to a localized label, times wrap, and decided doses have a readable noninteractive status instead of disabled duplicate actions.
- Existing occurrence selection, ViewModel decisions, scheduling and database behavior are unchanged. Photos remain app-private; no screenshot or medication data is bundled in the repository.

## Verification

- Passed: resource XML parsing, duplicate-resource and English/Chinese resource-key parity checks; new UI resource-reference check; `git diff --check`.
- Added but NOT RUN: Robolectric OEM success/failure/fallback tests, locale/time/day display tests. Existing occurrence/navigation tests retained.
- `testDebugUnitTest assembleDebug lintDebug` could not start: the cloud environment cannot fetch the pinned Gradle 9.5.0 distribution (`java.net.SocketException: Network is unreachable`). No Android SDK is available at the standard cloud SDK locations checked. No dependency versions were changed to hide this limitation.
- Compilation, Android lint, unit tests and visual/device acceptance remain pending. The initial GitHub Actions run subsequently compiled production and test sources, completed unit tests and assembled the debug APK, but failed Compose lint on a new resource lookup. This was corrected to use stringResource; the follow-up CI result is pending. No APK was downloaded or installed.

## Required device checks

1. On OPPO/ColorOS, open Settings → Autostart & background activity. Verify direct OEM navigation or app-details fallback; manually find and enable autostart. Returning must not claim that permission was granted.
2. Verify missing/private OEM activities do not crash, unknown brands use app details, and fallback text explains manual navigation.
3. English/Chinese, small viewport and 200% font: long name/alias/dosage/note, photo/no photo, seven days/partial days, several times, device/manual time zone.
4. Pending/loading/stale/already-decided occurrences; Taken/Skipped still use the original exact occurrence and remain unavailable after a decision. Verify Home and notification entry, Edit and Back.
5. Repeat the existing real-alarm cold-process test under the user's chosen autostart setting. UI guidance alone does not establish alarm reliability.

## API references

- Android Settings: https://developer.android.com/reference/android/provider/Settings
- OEM activity-name reference (no library dependency added): https://github.com/judemanutd/AutoStarter/blob/master/autostarter/src/main/java/com/judemanutd/autostarter/AutoStartPermissionHelper.kt
- Existing device evidence: `docs/review/2026-09-21-0159-sgt-cold-process-alarm-verification.md`.
