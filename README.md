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

## Commit naming

Use the following format for every repository commit:

```text
YYYYMMDD_medstime_v0.00x
```

`YYYYMMDD` is the local commit date, and `x` is the next unused monotonically increasing sequence number for the repository, starting at `001`; do not reset the suffix when the date changes. The latest commit is `20260823_medstime_v0.007`.
