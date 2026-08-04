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

## Physical-device verification

The current debug APK was installed and launched on a connected OPPO Find X8s+ (ADB model `PLB110`). Smoke testing verified the dark home screen, medication creation fields (including dosage, note, photo control, and schedule controls), the default three daily times and weekday/time-zone controls, save and persistence after force-stop/relaunch, and the Settings permission-status screen. No matching app crash entries appeared in the captured logcat window.

The Android SDK Google USB Driver package is installed and Windows is using its `winusb.inf` ADB interface. For non-Google phones, Android's [OEM USB-driver guidance](https://developer.android.com/studio/run/oem-usb) may still require a manufacturer-specific driver. Notification-action behavior, exact-alarm grant/fallback, reboot/time-zone recovery, camera/photo replacement, and the full history workflow remain follow-up tests.

## Commit naming

Use the following format for every repository commit:

```text
YYYYMMDD_medstime_v0.00x
```

`YYYYMMDD` is the local commit date, and `x` is the next unused monotonically increasing sequence number for the repository, starting at `001`; do not reset the suffix when the date changes. The current baseline is `20260804_medstime_v0.001`.
