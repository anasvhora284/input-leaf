package com.inputleaf.android.shizuku

import java.util.Base64

/** What [ClipboardHelperMain] prints and how [ShellClipboardReader] reads it back. */
internal object ClipboardHelperOutput {
    const val MARKER = "inputleaf-clip:"

    /** One line, base64 so any text survives the pipe; [MARKER] alone means no text. */
    fun encode(text: String?): String =
        MARKER + (text?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: "")

    /** The helper's text, or null when it printed nothing usable. */
    fun parse(output: String): String? {
        val encoded = output.lineSequence().firstOrNull { it.startsWith(MARKER) }?.removePrefix(MARKER)
        if (encoded.isNullOrEmpty()) return null
        return runCatching { String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) }.getOrNull()
    }

    /** Input Leaf's own APK among the files mapped into this process (/proc/self/maps). */
    fun apkIn(mapsLines: Sequence<String>): String? = mapsLines
        .map { it.substringAfterLast(' ') }
        .firstOrNull { it.contains("/com.inputleaf.android-") && it.endsWith("/base.apk") }
}
