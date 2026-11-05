# 2026-11-05 19:10 SGT — Single dosage editor and integrated release artifact

## Reviewed state and scope

- Feature commit: `1a9d18b390d60f2e5c19a2e142de48bd0b09bc46` on `codex/ui-single-dosage-input`.
- Integrated commit: `bbf8e9a77fbf7573f88f815ebdbd2d9e6c6ef033` on `master`; its source tree exactly matches the reviewed feature tree.
- Independent native reviewer: Carver. Final artifact review performed at 2026-11-05 19:10:44 SGT (`Asia/Singapore`).
- Scope: unified dosage editing, its validation and legacy-text behavior, release metadata/workflow, and the signed local `1.0.0-beta.6` / code 6 APK.

The [previous review](2026-10-24-2333-sgt-reconstructed-autostart-pending-dose.md) remains the source for the reconstructed autostart and pending-occurrence contracts. This bounded review does not replace those contracts or establish full device acceptance.

## Findings and disposition

**PASS — no blocking findings in the integrated local artifact.** The source and corrected publication plan also received independent PASS before integration.

The initial publication plan incorrectly described a private draft despite the existing tag-triggered workflow publishing immediately. The plan was corrected before any tag or remote mutation: pushing a `v*` tag is publication, and the local integrated artifact must pass review first. Published CI assets require separate verification after publication.

## Architecture implications

The editor has one numeric amount/unit area and generates its display text from those values. New entries require a positive amount and a unit. Existing text-only medication records retain their stored text when both structured fields remain blank; their text is not parsed. There is no Room schema, repository contract, schedule, or historical snapshot change. See the [feature contract](../feature-plans/interval-reminders-dose-summary/README.md).

## Verification evidence

- Same-source Gradle MCP build `b-3`: `testDebugUnitTest`, `assembleRelease`, and `lintRelease` succeeded; 194 tests passed, with no failures or skipped tests. Eleven tests cover the dosage input contract.
- Merged-master build `b-4`: `assembleRelease` succeeded. The archived APK is byte-identical to that output, at 1,761,022 bytes.
- Independent APK inspection confirmed package `com.example.medicationreminder`, version `1.0.0-beta.6`, version code `6`, and non-debuggable status. APK signature verification passed.
- Its release certificate matches the downloaded beta.5 APK: SHA-256 `abc99c2c6a5179f93eabf1300a5cf980234880fb67692967426d2e4f9eb18f37`.
- Debug device checks covered empty/zero rejection, creation with 0.5 tablet, and editing to 1 mg. After removing synthetic records, original medication, schedule and event rows, settings and photos matched the fresh pre-install baseline; the eight original alarms were active.

Local evidence is retained under `.codex/release-single-dosage/` (`build-summary.txt`, `merged-build-summary.txt`, `artifact-verification.json`, and `MedsReminder-v1.0.0-beta.6.apk`) and `.codex/ui-single-dosage/`. These ignored artifacts contain device data and are not release uploads. The tracked [release notes](../feature-plans/interval-reminders-dose-summary/release-notes.md) describe the delivered behavior and limitations.

## Remaining boundaries

This PASS covers the local integrated release artifact. Remote publication and verification of the separate CI-produced APK were not completed at review time. The user permits local delivery if sandbox credentials prevent publication.

The signed release APK has not received separate device acceptance. Acoustic behavior, lockscreen, reboot, and extended OEM background behavior remain unverified. An initial language refresh anomaly and one unresponsive Edit action were not reproduced in controlled retests; their causes remain unknown. Text-only legacy preservation has unit coverage but was not exercised on the device, whose original records already had structured doses.
