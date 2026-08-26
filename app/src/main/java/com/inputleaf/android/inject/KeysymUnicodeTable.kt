package com.inputleaf.android.inject

/**
 * Maps X11 keysyms to Unicode strings using the official keysym table.
 * See https://www.cl.cam.ac.uk/~mgk25/ucs/keysyms.txt
 */
object KeysymUnicodeTable {
    fun lookup(keysym: Int): String? {
        if (keysym >= 0x01000000) {
            val codePoint = keysym and 0xFFFFFF
            if (codePoint in 1..0x10FFFF) {
                return try {
                    String(Character.toChars(codePoint))
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            return null
        }
        return KeysymUnicodeTableData.TABLE[keysym] ?: directUnicodeFallback(keysym)
    }

    /**
     * Input Leap / Deskflow send UTF-32 KeyIDs on the wire (truncated to u16 for BMP).
     * Scripts without legacy X11 keysyms (Gujarati U+0A85, etc.) arrive as the Unicode
     * code point. Cyrillic may arrive as U+0410 rather than legacy keysym 0x06e1.
     */
    private fun directUnicodeFallback(keyId: Int): String? {
        if (keyId < 0x100 || keyId > 0x10FFFF) return null
        if (isInputLeapControlKeyId(keyId)) return null
        if (!Character.isValidCodePoint(keyId)) return null
        val type = Character.getType(keyId)
        if (type == Character.CONTROL.toInt() ||
            type == Character.UNASSIGNED.toInt() ||
            type == Character.PRIVATE_USE.toInt()
        ) {
            return null
        }
        return try {
            String(Character.toChars(keyId))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun isInputLeapControlKeyId(keyId: Int): Boolean {
        return keyId in 0xE000..0xEFFF ||
            keyId in 0xEE00..0xEEFF ||
            keyId in 0xEF00..0xEFFF
    }
}
