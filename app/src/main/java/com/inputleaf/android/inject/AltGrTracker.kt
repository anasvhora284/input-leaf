package com.inputleaf.android.inject

private const val ALT_GR_KEY_ID = 0xEF7E
private const val ISO_LEVEL3_SHIFT = 0xFE03
private const val PROTOCOL_MASK_ALT_GR = 0x0020

/**
 * Spots characters typed with AltGr (#82). A Windows server sends AltGr as a held Left
 * Ctrl plus a Right Alt that can't be pressed as a key, so the character would arrive as
 * Ctrl+key. The server already sends the composed character, with Ctrl and Alt cleared
 * from the mask, so such a character is typed as text instead.
 */
class AltGrTracker {
    private var held = false
    /** Buttons pressed as AltGr characters; their release and repeats follow the same path. */
    private val buttons = mutableSetOf<Int>()

    /** True if this key is an AltGr character that must be typed as text, not pressed. */
    fun typesChar(keysym: Int, mask: Int, button: Int, isDown: Boolean): Boolean {
        if (keysym == ALT_GR_KEY_ID || keysym == ISO_LEVEL3_SHIFT) held = isDown
        if (!isDown) return buttons.remove(button)
        val altGr = held || mask and PROTOCOL_MASK_ALT_GR != 0
        if (!altGr || KeysymUnicodeTable.lookup(keysym) == null || KeyMapUtils.protocolMaskHasShortcuts(mask)) {
            return false
        }
        buttons += button
        return true
    }

    /** True while [button] is held as an AltGr character. */
    fun isTyping(button: Int): Boolean = button in buttons

    fun clear() {
        held = false
        buttons.clear()
    }
}
