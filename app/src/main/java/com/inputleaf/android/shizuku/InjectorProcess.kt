package com.inputleaf.android.shizuku

import android.util.Log
import java.io.File
import kotlin.system.exitProcess

/** Lifetime of a process that exists only to host [InputInjectorService]. */
internal object InjectorProcess {
    private const val TAG = "InjectorProcess"

    /** Shizuku's reserved UserService destroy call: FIRST_CALL_TRANSACTION + 16777114. */
    const val SHIZUKU_DESTROY_TRANSACTION = 16777115

    private const val PROCESS_SUFFIX = ":input_injector"

    /**
     * Exit after the current binder call returns, so the caller sees its reply instead of
     * a DeadObjectException that would skip the rest of its own teardown.
     */
    fun exitSoon() {
        Thread {
            Thread.sleep(300)
            Log.i(TAG, "Injector process exiting pid=${android.os.Process.myPid()}")
            exitProcess(0)
        }.start()
    }

    /**
     * Older versions never exited, so a phone can hold one stale injector per past session.
     * Only one may run at a time: a new one ends the others with its own process name.
     */
    fun killOrphanedSiblings() {
        val me = android.os.Process.myPid()
        val myName = cmdline(me) ?: return
        if (!myName.endsWith(PROCESS_SUFFIX)) return
        File("/proc").listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            if (pid != me && cmdline(pid) == myName) {
                Log.w(TAG, "Ending orphaned injector pid=$pid")
                runCatching { android.os.Process.killProcess(pid) }
            }
        }
    }

    private fun cmdline(pid: Int): String? =
        runCatching { File("/proc/$pid/cmdline").readText().substringBefore('\u0000') }
            .getOrNull()?.takeIf { it.isNotEmpty() }
}
