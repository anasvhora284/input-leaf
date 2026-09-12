package com.inputleaf.android.shizuku.uhid

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends HID setup results to a file the Shizuku user service can write as shell.
 *
 * Exists because some OEM builds (OPPO/ColorOS among them) throttle or drop app logcat
 * output, which made `/dev/uhid` failures invisible during bring-up — the app reported a
 * working pointer while no device had been created. A file is not subject to that
 * filtering, and `adb pull` reaches it without root.
 *
 * Best-effort throughout: diagnostics must never break input injection.
 */
internal object UhidDiagnostics {

    private const val PATH = "/data/local/tmp/inputleaf_uhid.log"
    private const val MAX_BYTES = 1024 * 1024

    fun log(message: String) = append(message)

    fun log(message: String, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        append("$message: ${error.javaClass.name}: ${error.message}\n$stack")
    }

    private fun append(text: String) {
        try {
            val file = File(PATH)
            // Truncate rather than grow without bound across sessions.
            if (file.length() > MAX_BYTES) file.delete()
            val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            file.appendText("$stamp $text\n")
        } catch (_: Throwable) {
            // Diagnostics are never worth failing a session over.
        }
    }
}
