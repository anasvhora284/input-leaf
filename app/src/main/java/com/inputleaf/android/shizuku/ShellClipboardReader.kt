package com.inputleaf.android.shizuku

import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Root cannot read the clipboard in the background, and root posing as shell makes Android
 * clear it (#77). The shell user can, so a root injector starts [ClipboardHelperMain] as
 * shell (uid 2000) for each new text clip and takes the text from its output.
 */
internal object ShellClipboardReader {
    private const val TAG = "ShellClipboardReader"
    private const val TIMEOUT_MS = 4_000L

    fun read(): String? {
        val apk = ownApk() ?: return null.also { Log.w(TAG, "Input Leaf's APK path not found") }
        val command = "CLASSPATH='$apk' exec app_process /system/bin ${ClipboardHelperMain::class.java.name}"
        return runCatching {
            val process = ProcessBuilder("su", "2000", "-c", command).start()
            var output = ""
            val reader = Thread { output = process.inputStream.bufferedReader().readText() }.apply { start() }
            reader.join(TIMEOUT_MS)
            if (reader.isAlive) {
                process.destroy()
                Log.w(TAG, "Clipboard helper timed out")
                return null
            }
            ClipboardHelperOutput.parse(output)
        }.onFailure { Log.w(TAG, "Clipboard helper failed", it) }.getOrNull()
    }

    /** libsu starts its daemon from its own jar, so find the APK this code was loaded from. */
    private fun ownApk(): String? = runCatching {
        File("/proc/self/maps").useLines { lines -> ClipboardHelperOutput.apkIn(lines) }
    }.getOrNull()
}

/** Run as the shell user by [ShellClipboardReader]: prints the clipboard text and exits. */
object ClipboardHelperMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val text = ClipboardAccess.readClipText()
        println(ClipboardHelperOutput.encode(text))
        exitProcess(0)
    }
}
