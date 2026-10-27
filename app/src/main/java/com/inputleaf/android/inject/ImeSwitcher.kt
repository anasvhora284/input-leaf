package com.inputleaf.android.inject

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Accessibility mode types through the Input Leaf keyboard, but only while the mouse is on
 * this screen: it is selected on Enter and the user's own keyboard comes back on Leave,
 * disconnect and stop. If the app dies first, [InputLeafIME] hands itself back the next
 * time it starts, because [wanted] is false in a fresh process.
 */
internal object ImeSwitcher {
    private const val TAG = "ImeSwitcher"
    private const val PREFS = "ime_switcher"
    private const val KEY_PREVIOUS = "previous_ime"

    /** True while an Accessibility session wants keys on this screen. Never persisted. */
    @Volatile
    var wanted = false
        private set

    /**
     * Android 11+ lets the Accessibility service switch keyboards silently. Older versions
     * can only do it through the picker, so there the keyboard stays until the session ends
     * instead of popping the picker on every Enter.
     */
    private val silent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun ourId(context: Context): String =
        ComponentName(context, InputLeafIME::class.java).flattenToShortString()

    private fun currentId(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)

    private fun isOurs(context: Context, id: String?): Boolean =
        id != null && ComponentName.unflattenFromString(id) == ComponentName(context, InputLeafIME::class.java)

    /** Mouse entered in Accessibility mode with the keyboard enabled. */
    fun useOurs(context: Context) {
        val wasWanted = wanted
        wanted = true
        val current = currentId(context)
        if (isOurs(context, current)) return
        if (!silent && wasWanted) return
        if (current != null) prefs(context).edit().putString(KEY_PREVIOUS, current).apply()
        val switched = AccessibilityInputService.getInstance()?.switchKeyboard(ourId(context)) == true
        Log.i(TAG, "Switch to Input Leaf keyboard: $switched")
    }

    /** Mouse left ([leaving]) or the session ended: give the user's keyboard back. */
    fun restore(context: Context, leaving: Boolean = false) {
        if (leaving && !silent) return
        wanted = false
        if (!isOurs(context, currentId(context))) return
        val target = previousId(context)
        val handedBack = InputLeafIME.getInstance()?.handBack(target) == true ||
            (target != null && AccessibilityInputService.getInstance()?.switchKeyboard(target) == true)
        Log.i(TAG, "Restore keyboard ${target ?: "(previous)"}: $handedBack")
    }

    /**
     * The keyboard to return to. Not checked against the enabled list: Android 11+ hides
     * other apps' keyboards from it. A keyboard disabled since is rejected by the switch,
     * which then falls back to the previous one.
     */
    fun previousId(context: Context): String? = prefs(context).getString(KEY_PREVIOUS, null)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
