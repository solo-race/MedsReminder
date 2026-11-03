package com.example.medicationreminder.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.Locale

/** OEM screens are best-effort navigation, never evidence that autostart is enabled. */
internal data class AutostartTarget(val packageName: String, val activityName: String)

internal fun autostartTargets(manufacturer: String, brand: String): List<AutostartTarget> {
    val names = setOf(manufacturer.lowercase(Locale.ROOT), brand.lowercase(Locale.ROOT))
    return when {
        names.any { it in setOf("oppo", "realme", "oneplus") } -> listOf(
            AutostartTarget("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            AutostartTarget("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            AutostartTarget("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            AutostartTarget("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        )
        names.any { it in setOf("xiaomi", "redmi", "poco") } -> listOf(
            AutostartTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        names.any { it in setOf("huawei", "honor") } -> listOf(
            AutostartTarget("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )
        names.contains("vivo") -> listOf(
            AutostartTarget("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        )
        else -> emptyList()
    }
}

/** A successful launch says nothing about the current autostart permission. */
internal enum class AutostartSettingsResult { OEM_SCREEN_OPENED, MANUAL_GUIDANCE_REQUIRED }

internal fun openAutostartSettings(
    context: Context,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND,
): AutostartSettingsResult {
    // Do not enumerate installed packages: explicit activity launches work without QUERY_ALL_PACKAGES.
    for (target in autostartTargets(manufacturer, brand)) {
        val intent = Intent().setComponent(ComponentName(target.packageName, target.activityName))
        if (tryOpenSettings(context, intent)) return AutostartSettingsResult.OEM_SCREEN_OPENED
    }
    // App Details and general Settings are not autostart screens. Stay in the app
    // and explain the manual route rather than silently opening an unrelated page.
    return AutostartSettingsResult.MANUAL_GUIDANCE_REQUIRED
}

/** Only invoked by the separately labelled action in the manual guide. */
internal fun openSystemSettingsForAutostartGuide(context: Context): Boolean =
    tryOpenSettings(context, Intent(Settings.ACTION_SETTINGS))

private fun tryOpenSettings(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
} catch (_: RuntimeException) {
    // Vendor launch implementations can reject an activity with other runtime
    // failures. Preserve the manual guide instead of crashing or claiming success.
    false
}
