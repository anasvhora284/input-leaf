package com.inputleaf.android.shizuku.uhid

import android.util.Log

/**
 * Controls `Settings.Secure.show_ime_with_hard_keyboard`.
 *
 * Once a real HID keyboard is attached, Android hides the soft keyboard — which is the
 * point, since it gives the screen back while typing from the computer. But it also hides
 * the user's emoji and GIF pickers, so this provides a way to summon the keyboard on
 * demand and put it away again.
 *
 * The value is restored on disconnect. An earlier build did the inverse — it forced the
 * soft keyboard *off* and never restored it (`Suppressed soft keyboard (saved original:
 * 1)` in logcat), leaving the setting changed behind the user's back.
 *
 * Runs `settings` as a subprocess: the Shizuku user service has shell privileges but no
 * `Context` to reach `Settings.Secure` through.
 */
internal class SoftKeyboardToggle(
    private val exec: (Array<String>) -> String? = ::runCommand,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {

    private var savedValue: String? = null

    /** Remembers the user's setting so [restore] can put it back. */
    fun remember() {
        if (savedValue != null) return
        val current = exec(arrayOf("settings", "get", "secure", NAME))?.trim()
        savedValue = if (current.isNullOrEmpty() || current == "null") "0" else current
    }

    fun isShown(): Boolean =
        exec(arrayOf("settings", "get", "secure", NAME))?.trim() == "1"

    fun setShown(shown: Boolean) {
        remember()
        exec(arrayOf("settings", "put", "secure", NAME, if (shown) "1" else "0"))
        log("show_ime_with_hard_keyboard -> ${if (shown) 1 else 0}")
    }

    /** @return the new state */
    fun toggle(): Boolean {
        val next = !isShown()
        setShown(next)
        return next
    }

    fun restore() {
        val value = savedValue ?: return
        savedValue = null
        exec(arrayOf("settings", "put", "secure", NAME, value))
        log("show_ime_with_hard_keyboard restored to $value")
    }

    companion object {
        private const val TAG = "SoftKeyboardToggle"
        private const val NAME = "show_ime_with_hard_keyboard"

        private fun runCommand(command: Array<String>): String? = try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.w(TAG, "Command failed: ${command.joinToString(" ")}", e)
            null
        }
    }
}
