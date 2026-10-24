# Autostart guidance and detail readability

Current correction base: `4cd0f2f4f1b23e6e3a1246224896e76589dadec2`.
Work branch: `codex/recover-autostart-pending-dose`.

This correction is a fresh reconstruction on 2026-10-24, not a recovery of the
previous virtual machine's exact files, commits or test evidence. Read the latest
review via [`review/README.md`](review/README.md) before implementation decisions.

## Autostart navigation contract

- Preserve the existing best-effort OEM activity candidates. No new, unverified
  Oplus activity names have been added. These private/vendor routes are not a
  stable Android API and are not claimed to work on PLB110 / OPPO Find X8s+.
- `openAutostartSettings` returns only `OEM_SCREEN_OPENED` or
  `MANUAL_GUIDANCE_REQUIRED`. A screen opening is navigation evidence only,
  never evidence that autostart is enabled. No permission flag is written and no
  switch is changed or inferred on resume.
- Missing, private or runtime-failing OEM activities lead to manual guidance.
  Unknown manufacturers launch no external activity. App Details and generic
  system Settings are **not** automatic fallbacks for an autostart action.
- The manual guide remains visible until Close or system Back. Its visibility
  and system-settings failure state use `rememberSaveable`; its instructions
  scroll, including at large text sizes. An error stays above the scrollable
  instructions rather than becoming a transient Toast or scrolling out of view.
- A separately labelled **Open system Settings** button opens only generic
  Settings. Copy explicitly says this does not go directly to autostart. The
  guide remains open when returning from Settings, including recreation, and
  an unavailable/blocked launch keeps the guide open with a visible error.
- English and Simplified Chinese copy distinguishes autostart from App Details,
  battery settings and background-activity permissions. Allowing background
  activity is not treated as allowing autostart.
- Explicit activity launches are attempted without a package-enumeration
  preflight. Android's package-visibility guidance permits launching activities
  without visibility; querying first could hide an otherwise launchable target.
  No `QUERY_ALL_PACKAGES` or new package queries are added.

## Manual route and evidence limits

The official ColorOS 14 user manual, printed page 110 (PDF page 111), documents
**Settings → Apps → Auto launch**. The guide identifies OPPO / ColorOS and tells
users to search Settings when their system's menu differs. It does not claim
that this document establishes the exact current menu or deep link on the user's
PLB110 / Find X8s+.

The repository's 2026-09-21 device review found ColorOS autostart policy could
block a dead process even when background activity and device-idle exemptions
were already allowed. That is historical device evidence supporting the
separation of these settings, not verification of this reconstructed patch.
Guidance alone does not establish cold-process alarm reliability.

## Existing detail readability

The existing full-width identity/dosage, occurrence, schedule and note cards are
retained. Dosage units are not inferred. Chinese times use 24-hour notation,
daily schedules use a localized label and reminder times wrap. The companion
pending-occurrence correction on this work branch is documented separately;
this settings change does not alter persistence, alarms or dose decisions.

## Current verification

- Passed on 2026-10-24: resource XML parsing, duplicate-resource checks,
  English/Chinese resource-key parity, and `git diff --check`.
- Passed in the fresh 135-test suite: 13 autostart tests
  covering OEM success, missing/private activities, other runtime launch
  failures, fall-through only to existing OEM candidates, unknown OEMs with no
  external launch, case-insensitive manufacturer/brand aliases, navigation-only
  result semantics, the separate generic Settings action and its failures.
- One of those tests is explicitly a **source-level contract check** for saved
  guide/error state, scrolling, Close/Back and retaining the guide during a
  system-settings launch. It does not establish actual Compose rendering,
  lifecycle restoration or device navigation behavior.
- Fresh `testDebugUnitTest assembleDebug` passed: 135 tests, zero failures/errors/skips, and a new debug APK. Standard full `lintDebug` passed with zero errors and 22 warnings, without exclusions or dependency/repository changes.
- Device/Compose UI acceptance remains unrun. Old commits, CI results and workstation test counts are not evidence for these files. No device action or push is included; the local correction is kept on its own branch.

## Required device / UI acceptance

1. On PLB110 / OPPO Find X8s+ / ColorOS, use Settings → Autostart. Check either
   an actual autostart screen or the manual guide, never App Details or an
   automatically opened general Settings page. Returning must not claim a grant.
2. For missing/private OEM activities and unknown brands, confirm the guide
   remains until Close or Back. Repeat opening, closing and reopening it.
3. Use **Open system Settings**, then return. Rotate/recreate while the guide is
   open and while Settings is in front. Verify guide/error/scroll restoration.
   With an unavailable or blocked Settings launch, confirm the error is visible
   even after scrolling to the bottom, instructions remain available and retry
   or Close still works.
4. Check English/Chinese, small viewport and 200% font: title, instructions,
   error and both actions remain readable/reachable. Confirm the wording never
   treats App Details, battery or background-activity controls as autostart.
5. Retain detail readability checks for long names/aliases/dosages/notes,
   photo/no photo, daily/partial schedules, several times, both zone modes,
   pending/loading/stale/already-decided occurrences and Home/notification entry.
6. Repeat the real-alarm cold-process test only under the user's chosen
   autostart policy. Changing that device setting is a separate user decision.

## References

- [Android package-visibility use cases](https://developer.android.com/training/package-visibility/use-cases).
- [Official ColorOS 14.0 user manual, p. 110](https://s3.ap-southeast-1.amazonaws.com/sgp-sow-cms-oppo-com/cms/userManual/manual/f888f4f6-4f81-4759-8aaa-115f25e31bb4/ColorOS_14.0_User_Manual_V1.0_20231227.pdf).
- [Historical device review](review/2026-09-21-0159-sgt-cold-process-alarm-verification.md).
- Existing OEM activity-name reference, retained rather than expanded:
  [AutoStarter helper](https://github.com/judemanutd/AutoStarter/blob/master/autostarter/src/main/java/com/judemanutd/autostarter/AutoStartPermissionHelper.kt).
