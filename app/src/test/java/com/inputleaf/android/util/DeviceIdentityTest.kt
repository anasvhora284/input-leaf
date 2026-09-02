package com.inputleaf.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceIdentityTest {
    @Test
    fun `getMarketingName returns non-empty string`() {
        val name = DeviceIdentity.getMarketingName()
        assertThat(name).isNotEmpty()
    }

    @Test
    fun `getManufacturerName returns capitalized string`() {
        val name = DeviceIdentity.getManufacturerName("samsung")
        assertThat(name).isEqualTo("Samsung")
    }

    @Test
    fun `getInternalModelCode returns model or unknown`() {
        val code = DeviceIdentity.getInternalModelCode("Pixel 7")
        assertThat(code).isEqualTo("Pixel 7")
    }

    @Test
    fun `getAndroidVersion returns formatted android version`() {
        val version = DeviceIdentity.getAndroidVersion("14")
        assertThat(version).isEqualTo("Android 14")
    }

    @Test
    fun `getBrandLogoRes returns valid drawable resource for various brands`() {
        val brands = listOf("google", "samsung", "oneplus", "xiaomi", "redmi", "poco", "realme", "vivo", "oppo", "motorola", "nokia", "nothing", "iqoo", "tecno", "infinix", "asus", "honor", "lava", "micromax", "lenovo", "other")
        for (brand in brands) {
            val res = DeviceIdentity.getBrandLogoRes(brand)
            assertThat(res).isGreaterThan(0)
        }
    }

    @Test
    fun `getBrandColor returns non-null color for various brands`() {
        val brands = listOf("google", "samsung", "oneplus", "xiaomi", "redmi", "poco", "realme", "vivo", "oppo", "motorola", "nokia", "nothing", "iqoo", "tecno", "infinix", "asus", "honor", "lava", "micromax", "lenovo", "other")
        for (brand in brands) {
            val color = DeviceIdentity.getBrandColor(brand)
            assertThat(color).isNotNull()
        }
    }
}
