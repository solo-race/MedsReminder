# English/Chinese interface feature

These instructions apply to the Android app under `app/` while working on
`codex/feature-chinese-language`. The repository-level `AGENTS.md` remains
authoritative for the baseline, branch scope, tooling, and verification
commands.

## Feature scope

- Add an English/Chinese interface option in Settings.
- Keep English as the default so existing installs and users retain the
  current behavior until they choose another language.
- Persist the selection locally with the existing DataStore-backed
  `AppPreferences`; do not add an account, sync service, or database field for
  UI language.
- Apply the selected language to the Compose UI, navigation/tab labels, edit
  screens, settings copy, validation/errors, notification content, and
  notification channel names/descriptions where Android permits it.
- Do not translate medication names, notes, dosage values, time-zone IDs, or
  other user-entered/stored data.
- Keep scheduling, Room data, photo storage, notification actions, and
  time-zone behavior unchanged except where a localized display string is
  required.

## Localization rules

- Move user-visible literals out of Kotlin and into Android resources. Add the
  English source resource under `app/src/main/res/values/strings.xml` and the
  approved Simplified Chinese resource under
  `app/src/main/res/values-zh-rCN/strings.xml`.
- Use stable, semantic resource names and placeholders/plurals for dynamic
  text; do not build translated sentences by concatenating fragments.
- Use the selected app locale consistently for Compose `stringResource` calls,
  Java time day names, and background notification work. System-rendered text
  such as Android's permission dialog cannot be translated by the app; only
  the app's surrounding status/help text should be localized.
- Keep the approved translation draft as the source of truth. Do not add
  Chinese resources or silently revise wording before approval.
- Replace the manifest's hard-coded application label with a string resource
  if the app label is included in the language switch.
- Account for notification channels being persistent OS objects: use the
  localized channel metadata when creating/updating channels, and verify the
  result on a device with channels that already exist.

## Expected touchpoints

Review and update only the files needed for this behavior, especially:

- `app/src/main/java/com/example/medicationreminder/ui/MedicationApp.kt`
  for navigation labels, screen copy, day labels, and Settings UI.
- `app/src/main/java/com/example/medicationreminder/data/settings/AppPreferences.kt`
  for the persisted language choice and its default.
- `app/src/main/java/com/example/medicationreminder/MainActivity.kt` and
  `MedicationReminderApplication.kt` for applying the locale early enough for
  both foreground and background work.
- `app/src/main/java/com/example/medicationreminder/reminders/ReminderNotifications.kt`
  for localized channel metadata, reminder text, travel prompts, and action
  labels.
- `app/src/main/res/values/strings.xml` and
  `app/src/main/res/values-zh-rCN/strings.xml` for the resource catalog.

Search the whole `app/src/main` tree for remaining user-visible literals,
including fallback error messages and the manifest label; do not assume all
copy is in the main Compose file.

## Branch progress

- Established English source strings and approved Simplified Chinese resource
  strings under `app/src/main/res/values*`.
- Moved the approved navigation, screen, editor, settings, dialog, time-zone,
  status, and validation copy out of Kotlin and into resources.
- Added a DataStore-backed `AppLanguage` preference with English as the
  default, a Settings language selector, localized Compose context switching,
  localized weekday names, and a resource-backed application label.
- Commit `b594fc2` (`localization_proc_v0.001`) is the branch tip and is also
  in master through the v0.005 lockscreen merge. The strings and selector
  changes have not been verified as a feature; Gradle, lint, unit-test, and
  device checks remain pending.
- Reminder notification/channel/travel copy still needs localization, followed
  by the tests and device verification described below.

## Branch commit naming

- Use `localization_proc_v0.00x` for commits on this feature branch.
- The first version is `localization_proc_v0.001`; increment the numeric suffix
  for later branch commits.
- Append a documentation tag after the version tag in the commit message.
  This amended implementation commit uses:
  `localization_proc_v0.001: strings established, button added, not verified`

## Acceptance and verification

- English is selected by default, the language choice survives process death
  and relaunch, and changing it updates the visible app without losing
  medication form state.
- Verify Today/Home, History, Settings, add/edit medication, time-zone
  selection, photo dialogs, delete confirmation, empty states, errors, and
  weekday labels in both languages.
- Verify reminder and manual-time-zone notifications, their action labels, and
  channel names/descriptions in both languages. Confirm Taken/Skipped and
  travel actions still work.
- Confirm existing medication data remains unchanged and schedules are still
  calculated and rescheduled exactly as before.
- Add focused tests for preference default/read/write behavior and any locale
  or resource-selection logic that is introduced. Update Compose/instrumented
  coverage for Settings and the primary navigation labels where practical.
- Run `./gradlew.bat testDebugUnitTest assembleDebug lintDebug` from the
  repository root and record any device-only checks separately.

