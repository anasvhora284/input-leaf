package com.inputleaf.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BatteryOptimizationHelperTest {

    @Test
    fun `isColorOsOrDirectSettings returns true for OnePlus, OPPO, and Realme`() {
        assertThat(BatteryOptimizationHelper.isColorOsOrDirectSettings("OnePlus")).isTrue()
        assertThat(BatteryOptimizationHelper.isColorOsOrDirectSettings("OPPO")).isTrue()
        assertThat(BatteryOptimizationHelper.isColorOsOrDirectSettings("realme")).isTrue()
        assertThat(BatteryOptimizationHelper.isColorOsOrDirectSettings("Samsung")).isFalse()
        assertThat(BatteryOptimizationHelper.isColorOsOrDirectSettings("Google")).isFalse()
    }

    @Test
    fun `getOemComponents returns Huawei startup managers`() {
        val components = BatteryOptimizationHelper.getOemComponents("HUAWEI")
        assertThat(components).isNotEmpty()
        assertThat(components.map { it.packageName }).contains("com.huawei.systemmanager")
    }

    @Test
    fun `getOemComponents returns Honor startup managers`() {
        val components = BatteryOptimizationHelper.getOemComponents("HONOR")
        assertThat(components).isNotEmpty()
        assertThat(components.map { it.packageName }).contains("com.huawei.systemmanager")
    }

    @Test
    fun `getOemComponents returns Samsung Smart Manager activities`() {
        val components = BatteryOptimizationHelper.getOemComponents("Samsung")
        assertThat(components).isNotEmpty()
        assertThat(components.map { it.packageName }).contains("com.samsung.android.sm")
    }

    @Test
    fun `getOemComponents returns Xiaomi and Redmi powerkeeper and autostart`() {
        val xiaomi = BatteryOptimizationHelper.getOemComponents("Xiaomi")
        assertThat(xiaomi).isNotEmpty()
        assertThat(xiaomi.map { it.packageName }).contains("com.miui.powerkeeper")

        val redmi = BatteryOptimizationHelper.getOemComponents("Redmi")
        assertThat(redmi).isNotEmpty()

        val poco = BatteryOptimizationHelper.getOemComponents("Poco")
        assertThat(poco).isNotEmpty()
    }

    @Test
    fun `getOemComponents returns Vivo and iQOO battery activities`() {
        val components = BatteryOptimizationHelper.getOemComponents("vivo")
        assertThat(components).isNotEmpty()
        assertThat(components.map { it.packageName }).contains("com.vivo.abe")
    }

    @Test
    fun `getOemComponents returns Asus mobile manager`() {
        val components = BatteryOptimizationHelper.getOemComponents("asus")
        assertThat(components).isNotEmpty()
        assertThat(components.map { it.packageName }).contains("com.asus.mobilemanager")
    }

    @Test
    fun `getOemComponents returns Lenovo security manager`() {
        val components = BatteryOptimizationHelper.getOemComponents("lenovo")
        assertThat(components).isNotEmpty()
        assertThat(components.map { it.packageName }).contains("com.lenovo.security")
    }

    @Test
    fun `getOemComponents returns Nokia evenwell power saving`() {
        val components = BatteryOptimizationHelper.getOemComponents("nokia")
        assertThat(components).isNotEmpty()
        assertThat(components.map { it.packageName }).contains("com.evenwell.powersaving.g3")
    }

    @Test
    fun `getOemComponents returns empty for stock AOSP or Google Pixel`() {
        assertThat(BatteryOptimizationHelper.getOemComponents("Google")).isEmpty()
        assertThat(BatteryOptimizationHelper.getOemComponents("Generic")).isEmpty()
    }
}
