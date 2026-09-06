# Phase 6 — Verification and Rollout

## Automated verification

Add focused coverage before merge:

- Repository tests for stable dose-time IDs and transactional decision semantics.
- Scheduler tests for decision-aware next-occurrence selection.
- Receiver/use-case tests for stale, duplicate, and race-adjacent actions where practical.
- Existing `NextDoseCalculatorTest` regression coverage must remain green.
- Existing reminder redaction/privacy tests must remain green.

## Required scenarios

1. 08:00 dose, user marks Taken at 07:30; no 08:00 reminder.
2. Same case, app relaunched at 07:45; still no 08:00 reminder.
3. Same case, TIME_SET/TIMEZONE_CHANGED/package recovery triggers rescheduling; still no 08:00 reminder.
4. Same case, alarm fire races with decision; receiver suppresses display.
5. 08:00 notification opened at 08:05; Taken applies to 08:00, not the next dose.
6. Medication with multiple daily times keeps occurrence identities separate.
7. User marks 08:00 Taken, edits only note/photo/name before 08:00; decision remains effective.
8. Add/remove/reorder times preserves IDs for unchanged slots.
9. Double tap / concurrent Taken-Skipped creates one decision.
10. Deleted/disabled medication or removed slot ignores stale action.
11. DST gap and overlap behavior does not regress.
12. Existing edit, delete, notification redaction, persistent reminder, and history behavior remain intact.

## Build/lint gate

Run the repository's canonical verification command from `AGENTS.md`:

```bash
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest assembleDebug lintDebug --no-daemon --offline --console=plain
```

Use the documented `JAVA_HOME` and `GRADLE_USER_HOME` environment when running on the verified workstation.

## Device smoke

On the existing OPPO/ColorOS device when available:

- verify Home → Detail → Edit navigation;
- verify notification → exact occurrence Detail;
- verify pre-alarm Taken and Skipped each cancel/suppress the pending reminder;
- relaunch app before the original alarm time;
- edit metadata after a pre-alarm decision;
- verify next-day/next-slot scheduling remains present;
- inspect `dumpsys alarm` for the expected next occurrence rather than the decided one.

## Documentation update before merge

- Update the architecture index if final file/class names differ from the plan.
- Record verification evidence and any OEM-specific observations in `docs/memory.md`.
- Update branch/status documentation in `AGENTS.md` if this repository continues to track active feature branches there.

## Merge gate

Do not merge until the persistence identity, occurrence propagation, transactional decision, scheduler guard, Detail UI, automated tests, and available device smoke checks are all reconciled against this plan.
