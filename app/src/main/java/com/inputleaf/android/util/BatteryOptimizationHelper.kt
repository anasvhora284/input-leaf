package com.inputleaf.android.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

private const val TAG = "BatteryOptHelper"

data class OemComponent(val packageName: String, val className: String)

/**
 * OEM-aware helper to request battery optimization exemption.
 *
 * Many Android OEMs (OnePlus, OPPO, Huawei, Xiaomi, Vivo, Samsung)
 * override or block the stock AOSP intent
 * [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS].
 *
 * This helper tries manufacturer-specific settings activities first,
 * falling back gracefully through a chain until one succeeds.
 */
object BatteryOptimizationHelper {

    fun getOemComponents(manufacturer: String): List<OemComponent> {
        val m = manufacturer.lowercase()
        return when {
            m.contains("huawei") || m.contains("honor") -> listOf(
                OemComponent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                OemComponent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
            m.contains("samsung") -> listOf(
                OemComponent("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                OemComponent("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")
            )
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> listOf(
                OemComponent("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                OemComponent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            m.contains("vivo") -> listOf(
                OemComponent("com.vivo.abe", "com.vivo.applicationbased.energy.options.TopActivity"),
                OemComponent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
            )
            m.contains("asus") -> listOf(
                OemComponent("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
            )
            m.contains("lenovo") -> listOf(
                OemComponent("com.lenovo.security", "com.lenovo.security.purebackground.PureBackgroundActivity")
            )
            m.contains("nokia") -> listOf(
                OemComponent("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity")
            )
            else -> emptyList()
        }
    }

    fun isColorOsOrDirectSettings(manufacturer: String): Boolean {
        val m = manufacturer.lowercase()
        return m.contains("oneplus") || m.contains("oppo") || m.contains("realme")
    }

    fun requestExemption(context: Context) {
        val manufacturer = Build.MANUFACTURER
        Log.d(TAG, "Requesting battery exemption on manufacturer=$manufacturer")

        val intents = mutableListOf<Intent>()

        if (isColorOsOrDirectSettings(manufacturer)) {
            intents += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            intents += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            for (comp in getOemComponents(manufacturer)) {
                intents += Intent().apply {
                    component = ComponentName(comp.packageName, comp.className)
                }
            }
        }

        // 2. AOSP standard per-app prompt dialog (requires REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intents += Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )

        // 3. Android 12+ (API 31+) dedicated App Battery Usage page
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            intents += Intent("android.settings.APP_BATTERY_USAGE").apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }

        // 4. AOSP App Info page (Application Details Settings -> Battery section)
        intents += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

        // 5. Fallback: full system-wide battery optimization list
        intents += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Launched: ${intent.component ?: intent.action}")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Intent not available: ${intent.component ?: intent.action}", e)
            }
        }
    }
}
