package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PointerDeviceTest {

    @Test
    fun `missing or unknown storage stays on the relative mouse`() {
        assertThat(PointerDevice.fromStorage(null)).isEqualTo(PointerDevice.MOUSE)
        assertThat(PointerDevice.fromStorage("")).isEqualTo(PointerDevice.MOUSE)
        assertThat(PointerDevice.fromStorage("tablet")).isEqualTo(PointerDevice.MOUSE)
    }

    @Test
    fun `stylus storage round trips`() {
        assertThat(PointerDevice.fromStorage("stylus")).isEqualTo(PointerDevice.STYLUS)
        assertThat(PointerDevice.STYLUS.storageValue).isEqualTo("stylus")
        assertThat(PointerDevice.MOUSE.storageValue).isEqualTo("mouse")
    }
}
