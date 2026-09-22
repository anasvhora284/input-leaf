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
            if (cached != null && !cached.isRoot) {
                cached.close()
            }
        }
        return try {
            Shell.getShell().isRoot
        } catch (_: Throwable) {
            false
        }
    }

    internal fun isSuPresent(exists: (String) -> Boolean = { File(it).exists() }): Boolean {
        return suPaths.any(exists)
    }

    internal fun hasRootMarker(exists: (String) -> Boolean = { File(it).exists() }): Boolean {
        return rootedMarkers.any(exists)
    }

    private fun grantedOrNull(): Boolean? {
        return try {
            Shell.isAppGrantedRoot()
        } catch (_: Throwable) {
            null
        }
    }
}
