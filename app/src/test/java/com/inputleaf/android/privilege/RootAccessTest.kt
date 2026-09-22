package com.inputleaf.android.privilege

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RootAccessTest {

    @Test
    fun `su present when any known path exists`() {
        assertThat(
            LibSuRootAccess.isSuPresent { path -> path == "/debug_ramdisk/su" },
        ).isTrue()
    }

    @Test
    fun `su missing when no known path exists`() {
        assertThat(LibSuRootAccess.isSuPresent { false }).isFalse()
    }

    @Test
    fun `unknown and granted root can be used for HID`() {
        assertThat(RootAvailability.UNKNOWN.canUse()).isTrue()
        assertThat(RootAvailability.GRANTED.canUse()).isTrue()
        assertThat(RootAvailability.DENIED.canUse()).isFalse()
        assertThat(RootAvailability.MISSING.canUse()).isFalse()
    }

    @Test
    fun `KernelSU sysfs marker counts as rooted even when su is hidden`() {
        assertThat(
            LibSuRootAccess.hasRootMarker { path -> path == "/sys/module/kernelsu" },
        ).isTrue()
        assertThat(LibSuRootAccess.hasRootMarker { false }).isFalse()
    }
}
