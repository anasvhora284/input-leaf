package com.inputleaf.android.privilege

import com.google.common.truth.Truth.assertThat
import com.topjohnwu.superuser.Shell
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

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

    @Test
    fun `default path probe finds nothing on a machine without su`() {
        assertThat(LibSuRootAccess.isSuPresent()).isFalse()
        assertThat(LibSuRootAccess.hasRootMarker()).isFalse()
    }

    @Test
    fun `availability follows the shell grant and a marker when grant is unknown`() {
        val previous = LibSuRootAccess.pathExists
        try {
            mockStatic(Shell::class.java).use { shells ->
                shells.`when`<Boolean?> { Shell.isAppGrantedRoot() }.thenReturn(true)
                assertThat(LibSuRootAccess.availability()).isEqualTo(RootAvailability.GRANTED)

                shells.`when`<Boolean?> { Shell.isAppGrantedRoot() }.thenReturn(false)
                assertThat(LibSuRootAccess.availability()).isEqualTo(RootAvailability.DENIED)

                shells.`when`<Boolean?> { Shell.isAppGrantedRoot() }.thenReturn(null)
                LibSuRootAccess.pathExists = { false }
                assertThat(LibSuRootAccess.availability()).isEqualTo(RootAvailability.MISSING)

                LibSuRootAccess.pathExists = { it == "/proc/ksu" }
                assertThat(LibSuRootAccess.availability()).isEqualTo(RootAvailability.UNKNOWN)

                shells.`when`<Boolean?> { Shell.isAppGrantedRoot() }
                    .thenThrow(IllegalStateException("no shell"))
                assertThat(LibSuRootAccess.availability()).isEqualTo(RootAvailability.UNKNOWN)
            }
        } finally {
            LibSuRootAccess.pathExists = previous
        }
    }

    @Test
    fun `requestAccess drops a cached non-root shell then reports the new shell`() {
        mockStatic(Shell::class.java).use { shells ->
            val cached = mock(Shell::class.java)
            val fresh = mock(Shell::class.java)
            `when`(cached.isRoot).thenReturn(false)
            `when`(fresh.isRoot).thenReturn(true)
            shells.`when`<Shell> { Shell.getCachedShell() }.thenReturn(cached)
            shells.`when`<Shell> { Shell.getShell() }.thenReturn(fresh)

            assertThat(LibSuRootAccess.requestAccess()).isTrue()
            verify(cached).close()

            val alreadyRoot = mock(Shell::class.java)
            `when`(alreadyRoot.isRoot).thenReturn(true)
            shells.`when`<Shell> { Shell.getCachedShell() }.thenReturn(alreadyRoot)
            assertThat(LibSuRootAccess.requestAccess()).isTrue()
            verify(alreadyRoot, never()).close()

            shells.`when`<Shell> { Shell.getCachedShell() }.thenReturn(null)
            shells.`when`<Shell> { Shell.getShell() }.thenThrow(IllegalStateException("su denied"))
            assertThat(LibSuRootAccess.requestAccess()).isFalse()
        }
    }
}
