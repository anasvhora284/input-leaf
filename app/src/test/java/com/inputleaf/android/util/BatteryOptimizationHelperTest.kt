package com.inputleaf.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BatteryOptimizationHelperTest {
    @Test
    fun `getOemComponents returns Huawei components for huawei and honor`() {
        val huawei = BatteryOptimizationHelper.getOemComponents("Huawei")
        assertThat(huawei).isNotEmpty()
        assertThat(huawei[0].packageName).isEqualTo("com.huawei.systemmanager")

        val honor = BatteryOptimizationHelper.getOemComponents("HONOR")
        assertThat(honor).isNotEmpty()
        assertThat(honor[0].packageName).isEqualTo("com.huawei.systemmanager")
    }

    @Test
    fun `getOemComponents returns Samsung components for samsung`() {
        val samsung = BatteryOptimizationHelper.getOemComponents("Samsung")
        assertThat(samsung).hasSize(2)
        assertThat(samsung[0].packageName).isEqualTo("com.samsung.android.lool")
    }

    @Test
    fun `getOemComponents returns Xiaomi components for xiaomi redmi poco`() {
        for (oem in listOf("Xiaomi", "Redmi", "Poco")) {
            val comps = BatteryOptimizationHelper.getOemComponents(oem)
            assertThat(comps).isNotEmpty()
            assertThat(comps[0].packageName).isEqualTo("com.miui.powerkeeper")
        }
    }

    @Test
    fun `getOemComponents returns Vivo components for vivo`() {
        val vivo = BatteryOptimizationHelper.getOemComponents("Vivo")
        assertThat(vivo).isNotEmpty()
        assertThat(vivo[0].packageName).isEqualTo("com.vivo.abe")
    }

    @Test
    fun `getOemComponents returns Asus components for asus`() {
        val asus = BatteryOptimizationHelper.getOemComponents("Asus")
        assertThat(asus).isNotEmpty()
        assertThat(asus[0].packageName).isEqualTo("com.asus.mobilemanager")
    }

    @Test
    fun `getOemComponents returns Lenovo components for lenovo`() {
        val lenovo = BatteryOptimizationHelper.getOemComponents("Lenovo")
        assertThat(lenovo).isNotEmpty()
        assertThat(lenovo[0].packageName).isEqualTo("com.lenovo.security")
    }

    @Test
    fun `getOemComponents returns Nokia components for nokia`() {
        val nokia = BatteryOptimizationHelper.getOemComponents("Nokia")
        assertThat(nokia).isNotEmpty()
        assertThat(nokia[0].packageName).isEqualTo("com.evenwell.powersaving.g3")
    }

    @Test
    fun `getOemComponents returns empty for unknown oem`() {
        val unknown = BatteryOptimizationHelper.getOemComponents("Google")
        assertThat(unknown).isEmpty()
    }

    @Test
    fun `isColorOsOrDirectSettings returns true for OnePlus Oppo Realme`() {
        assertThat(BatteryOptimizationHelper.isColorOsOrDirectSettings("OnePlus")).isTrue()
        assertThat(BatteryOptimizationHelper.isColorOsOrDirectSettings("OPPO")).isTrue()
        assertThat(BatteryOptimizationHelper.isColorOsOrDirectSettings("realme")).isTrue()
        assertThat(BatteryOptimizationHelper.isColorOsOrDirectSettings("Google")).isFalse()
        assertThat(BatteryOptimizationHelper.isColorOsOrDirectSettings("Samsung")).isFalse()
    }
}
