package com.example.medicationreminder.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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

internal enum class AutostartSettingsResult { OEM_SCREEN, APP_DETAILS, SYSTEM_SETTINGS, UNAVAILABLE }

internal fun openAutostartSettings(
    context: Context,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND,
): AutostartSettingsResult {
    // Do not enumerate installed packages: explicit activity launches work without QUERY_ALL_PACKAGES.
    for (target in autostartTargets(manufacturer, brand)) {
        val intent = Intent().setComponent(ComponentName(target.packageName, target.activityName))
        if (tryOpenSettings(context, intent)) return AutostartSettingsResult.OEM_SCREEN
    }
    if (tryOpenSettings(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}")))) {
        return AutostartSettingsResult.APP_DETAILS
    }
    return if (tryOpenSettings(context, Intent(Settings.ACTION_SETTINGS))) {
        AutostartSettingsResult.SYSTEM_SETTINGS
    } else AutostartSettingsResult.UNAVAILABLE
}

private fun tryOpenSettings(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
