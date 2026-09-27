package com.inputleaf.android.privilege

import com.topjohnwu.superuser.Shell
import java.io.File

interface RootAccess {
    fun availability(): RootAvailability
    fun requestAccess(): Boolean
}

/**
 * libsu-backed root probe. [availability] never prompts; [requestAccess] may show
 * the Magisk/KernelSU `su` dialog and must not run on the main thread.
 *
 * KernelSU hides `/system/bin/su` from ungranted app UIDs, so [File.exists] alone
 * would report [RootAvailability.MISSING] on a rooted phone and skip HID attach.
 */
object LibSuRootAccess : RootAccess {
    /** Overridable in tests so availability can see a marker without a real su binary. */
    internal var pathExists: (String) -> Boolean = { File(it).exists() }

    private val suPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
        "/debug_ramdisk/su",
        "/system/bin/.ext/su",
        "/odm/bin/su",
    )

    private val rootedMarkers = listOf(
        "/sys/module/kernelsu",
        "/proc/ksu",
        "/debug_ramdisk/.magisk",
        "/sbin/.magisk",
        "/data/adb/magisk",
    )

    override fun availability(): RootAvailability {
        return when (grantedOrNull()) {
            true -> RootAvailability.GRANTED
            false -> RootAvailability.DENIED
            null -> if (isSuPresent() || hasRootMarker()) {
                RootAvailability.UNKNOWN
            } else {
                RootAvailability.MISSING
            }
        }
    }

    override fun requestAccess(): Boolean {
        // libsu caches the main shell process-wide. A denied or timed-out su prompt caches
        // a non-root shell, and every later getShell() would hand that same one back — so
        // "Grant root" could never succeed again without an app restart. Drop it first.
        runCatching {
            val cached = Shell.getCachedShell()
            if (cached != null && !cached.isRoot) cached.close()
        }
        return runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    internal fun isSuPresent(exists: (String) -> Boolean = pathExists): Boolean {
        return suPaths.any(exists)
    }

    internal fun hasRootMarker(exists: (String) -> Boolean = pathExists): Boolean {
        return rootedMarkers.any(exists)
    }

    private fun grantedOrNull(): Boolean? {
        return runCatching { Shell.isAppGrantedRoot() }.getOrNull()
    }
}
