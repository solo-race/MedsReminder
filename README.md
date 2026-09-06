# Medication Reminder

An offline Android medication reminder built with Kotlin and Jetpack Compose. It stores medication details, private local photos, schedules, and the last 30 days of Taken/Skipped history on the device.

## Requirements

- Android Studio with JDK 17
- Android SDK Platform 37.0 (the app currently targets API 35)

Open this folder in Android Studio, allow Gradle to download dependencies, then run the `app` configuration on an Android 8.0 (API 26) or newer device/emulator.

## Permissions

- **Notifications**: required on Android 13+ to display reminders.
- **Exact alarms**: requested on Android 12+ for minute-accurate reminders. The app falls back to Android's best-effort alarm scheduling if denied.
- **Photos and camera**: selection uses the system photo picker and capture uses the system camera. Saved images are copied into app-private storage.

The app does not create an account, sync data, or send medication data off-device.

## Commit convention

Use concise Conventional Commits-style messages that describe the change:

```text
<type>: <summary>
```

Common types include `feat`, `fix`, `docs`, `test`, `ci`, `refactor`, `build`, `chore`, `perf`, and `revert`. Choose the type that matches the change and keep the summary specific to the commit's scope.

Do not encode dates, repository-wide sequence numbers, release versions, or the current branch tip in commit messages solely for bookkeeping.

Git tags are separate from commit naming. Create or push a tag only after the user explicitly approves that tag operation.

README files must not track a "latest commit", current HEAD SHA, or branch-tip commit message. Use Git history and branch refs when the current repository state is needed.
