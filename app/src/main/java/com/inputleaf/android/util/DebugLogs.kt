package com.inputleaf.android.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/** Serves only the debug log directory in log_paths.xml. */
class DebugLogProvider : FileProvider()

/**
 * "Share debug logs": this app's recent logcat plus the privileged injector's, in one text
 * file for an issue. Android lets an app read only its own logs, so the injector (shell or
 * root) reads and returns its own. Nothing logs typed text or clipboard contents.
 */
object DebugLogs {
    const val MAX_LINES = 3000

    /** Recent logcat lines, limited to [pid] when given. Empty if logcat can't run. */
    fun readLogcat(maxLines: Int, pid: Int? = null): String = runCatching {
        val command = mutableListOf("logcat", "-d", "-v", "threadtime", "-t", maxLines.toString())
        if (pid != null) command += "--pid=$pid"
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
    }.getOrDefault("")

    fun compose(header: String, appLog: String, injectorLog: String?): String = buildString {
        appendLine(header)
        appendLine()
        appendLine("=== App ===")
        appendLine(appLog.ifBlank { "(no lines)" })
        appendLine("=== Injector (Shizuku/root) ===")
        appendLine(injectorLog?.ifBlank { "(no lines)" } ?: "(not running)")
    }

    /** Writes the logs to the cache and returns the share sheet for them. */
    fun shareIntent(context: Context, versionName: String, injectorLog: String?): Intent {
        val header = "Input Leaf $versionName · ${Build.MANUFACTURER} ${Build.MODEL} · " +
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, "input-leaf-logs.txt")
        file.writeText(compose(header, readLogcat(MAX_LINES), injectorLog))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Input Leaf debug logs")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share debug logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
