package com.inputleaf.android.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

private const val TAG = "BatteryOptHelper"

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

    fun requestExemption(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        Log.d(TAG, "Requesting battery exemption on manufacturer=$manufacturer")

        // Build an ordered list: OEM-specific intents first, AOSP fallbacks last
        val intents = mutableListOf<Intent>()

        // 1. Add OEM-specific intents based on device manufacturer
        when {
            manufacturer.contains("oneplus") || manufacturer.contains("oppo") ||
            manufacturer.contains("realme") -> {
                // ColorOS / OxygenOS / Realme UI
                // ColorOS blocks the standard AOSP battery dialog, so go directly to app settings
                // which has a Battery section the user can tap
                intents += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                // Also try the battery optimization list as fallback
                intents += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }

            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                // EMUI / MagicUI
                intents += componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                intents += componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }

            manufacturer.contains("samsung") -> {
                // One UI
                intents += componentIntent(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
                intents += componentIntent(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            }

            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
            manufacturer.contains("poco") -> {
                // MIUI / HyperOS
                intents += componentIntent(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                intents += componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }

            manufacturer.contains("vivo") -> {
                intents += componentIntent(
                    "com.vivo.abe",
                    "com.vivo.applicationbased.energy.options.TopActivity"
                )
                intents += componentIntent(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
            }

            manufacturer.contains("asus") -> {
                intents += componentIntent(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.autostart.AutoStartActivity"
                )
            }

            manufacturer.contains("lenovo") -> {
                intents += componentIntent(
                    "com.lenovo.security",
                    "com.lenovo.security.purebackground.PureBackgroundActivity"
                )
            }

            manufacturer.contains("nokia") -> {
                intents += componentIntent(
                    "com.evenwell.powersaving.g3",
                    "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"
                )
            }
        }

        // 2. AOSP standard per-app dialog (works on Pixel, stock, many OEMs)
        intents += Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )

        // 3. Last resort: open the full battery optimization list
        intents += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        // Try each intent in order; first one that successfully starts wins
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Try to start directly - resolveActivity isn't always reliable for OEM components
                context.startActivity(intent)
                Log.d(TAG, "Launched: ${intent.component ?: intent.action}")
                return
            } catch (e: Exception) {
                Log.d(TAG, "Intent not available: ${intent.component ?: intent.action}")
                // Continue to next intent
            }
        }

        // If absolutely nothing worked, open general app settings
        try {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open any settings page", e)
        }
    }

    private fun componentIntent(pkg: String, cls: String): Intent {
        return Intent().apply {
            component = ComponentName(pkg, cls)
        }
    }
}
