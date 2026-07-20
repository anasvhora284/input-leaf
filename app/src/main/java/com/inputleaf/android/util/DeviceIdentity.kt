package com.inputleaf.android.util

import android.os.Build
import androidx.compose.ui.graphics.Color
import com.inputleaf.android.R
import com.jaredrummler.android.device.DeviceName

object DeviceIdentity {

    fun getMarketingName(): String {
        return try {
            val name = DeviceName.getDeviceName()
            if (isUnresolvedName(name)) {
                formatFallbackName()
            } else {
                name
            }
        } catch (e: Exception) {
            formatFallbackName()
        }
    }

    fun requestMarketingName(context: android.content.Context, onResult: (String) -> Unit) {
        try {
            // 1. Try to get the user-set or default system device name (e.g. "OnePlus Nord 4")
            val systemName = android.provider.Settings.Global.getString(
                context.contentResolver,
                android.provider.Settings.Global.DEVICE_NAME
            )
            
            if (!isUnresolvedName(systemName) && systemName != null) {
                onResult(systemName)
                return
            }

            // 2. Fallback to external library (async)
            DeviceName.with(context).request { info, _ ->
                val name = info?.marketName ?: info?.model ?: info?.codename
                if (!isUnresolvedName(name) && name != null) {
                    onResult(name)
                } else {
                    onResult(getMarketingName())
                }
            }
        } catch (e: Exception) {
            onResult(getMarketingName())
        }
    }

    private fun isUnresolvedName(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        if (name.equals(Build.MODEL, ignoreCase = true)) return true
        if (name.equals(Build.DEVICE, ignoreCase = true)) return true
        if (name.equals(Build.PRODUCT, ignoreCase = true)) return true
        return false
    }

    private fun formatFallbackName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model
        else "$manufacturer $model"
    }

    fun getManufacturerName(): String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    fun getInternalModelCode(): String = Build.MODEL

    fun getAndroidVersion(): String = "Android ${Build.VERSION.RELEASE}"

    fun getBrandLogoRes(): Int {
        return when (Build.MANUFACTURER.lowercase()) {
            "google" -> R.drawable.ic_brand_google
            "samsung" -> R.drawable.ic_brand_samsung
            "oneplus" -> R.drawable.ic_brand_oneplus
            "xiaomi" -> R.drawable.ic_brand_xiaomi
            "redmi" -> R.drawable.ic_brand_redmi
            "poco", "pocophone" -> R.drawable.ic_brand_poco
            "realme" -> R.drawable.ic_brand_realme
            "vivo" -> R.drawable.ic_brand_vivo
            "oppo" -> R.drawable.ic_brand_oppo
            "motorola" -> R.drawable.ic_brand_motorola
            "nokia", "hmd global" -> R.drawable.ic_brand_nokia
            "nothing" -> R.drawable.ic_brand_nothing
            "iqoo" -> R.drawable.ic_brand_iqoo
            "tecno" -> R.drawable.ic_brand_tecno
            "infinix" -> R.drawable.ic_brand_infinix
            "asus" -> R.drawable.ic_brand_asus
            "honor" -> R.drawable.ic_brand_honor
            "lava" -> R.drawable.ic_brand_lava
            "micromax" -> R.drawable.ic_brand_micromax
            "lenovo" -> R.drawable.ic_brand_lenovo
            else -> R.drawable.ic_brand_android
        }
    }

    fun getBrandColor(): Color {
        return when (Build.MANUFACTURER.lowercase()) {
            "google" -> Color(0xFF4285F4)
            "samsung" -> Color(0xFF1428A0)
            "oneplus" -> Color(0xFFEB0029)
            "xiaomi" -> Color(0xFFFF6700)
            "redmi" -> Color(0xFFFF0000)
            "poco", "pocophone" -> Color(0xFFFFC107)
            "realme" -> Color(0xFFFFC107)
            "vivo" -> Color(0xFF415FFF)
            "oppo" -> Color(0xFF1BA784)
            "motorola" -> Color(0xFF5C2D91)
            "nokia", "hmd global" -> Color(0xFF124191)
            "nothing" -> Color(0xFFE0E0E0)
            "iqoo" -> Color(0xFFFF5722)
            "tecno" -> Color(0xFF0077C0)
            "infinix" -> Color(0xFFFF6600)
            "asus" -> Color(0xFF00549E)
            "honor" -> Color(0xFF0088CE)
            "lava" -> Color(0xFFE31E24)
            "micromax" -> Color(0xFF0072BC)
            "lenovo" -> Color(0xFFE2231A)
            else -> Color(0xFF3DDC84)
        }
    }
}
