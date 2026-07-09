package com.inputleaf.android.inject

import android.util.Log
import android.view.KeyEvent

object KeyMapUtils {
    private const val TAG = "KeyMapUtils"

    fun updateMetaState(keyCode: Int, isDown: Boolean, currentMetaState: Int): Int {
        val metaFlag = when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT ->
                KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT ->
                KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT ->
                KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT ->
                KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
            else -> 0
        }

        return if (isDown) {
            currentMetaState or metaFlag
        } else {
            currentMetaState and metaFlag.inv()
        }
    }

    /**
     * Convert X11 keysym to Android keycode.
     * X11 keysym values: https://www.cl.cam.ac.uk/~mgk25/ucs/keysyms.txt
     */
    fun keysymToAndroidKeyCode(keysym: Int): Int {
        val normalizedKeysym = when {
            keysym in 0xEF00..0xEFFF -> keysym + 0x1000
            else -> keysym
        }

        return when (normalizedKeysym) {
            in 0x61..0x7a -> KeyEvent.KEYCODE_A + (normalizedKeysym - 0x61)
            in 0x41..0x5a -> KeyEvent.KEYCODE_A + (normalizedKeysym - 0x41)
            in 0x30..0x39 -> KeyEvent.KEYCODE_0 + (normalizedKeysym - 0x30)
            in 0xFFBE..0xFFC9, in 0xffbe..0xffc9 -> {
                val base = if (normalizedKeysym >= 0xFFBE) normalizedKeysym - 0xFFBE else normalizedKeysym - 0xffbe
                KeyEvent.KEYCODE_F1 + base
            }
            0xFF08, 0xff08 -> KeyEvent.KEYCODE_DEL
            0xFF09, 0xff09 -> KeyEvent.KEYCODE_TAB
            0xFF0D, 0xff0d -> KeyEvent.KEYCODE_ENTER
            0xFF0A, 0xff0a -> KeyEvent.KEYCODE_ENTER
            0xFF1B, 0xff1b -> KeyEvent.KEYCODE_ESCAPE
            0xFFFF, 0xffff -> KeyEvent.KEYCODE_FORWARD_DEL
            0xFF50, 0xff50 -> KeyEvent.KEYCODE_MOVE_HOME
            0xFF51, 0xff51 -> KeyEvent.KEYCODE_DPAD_LEFT
            0xFF52, 0xff52 -> KeyEvent.KEYCODE_DPAD_UP
            0xFF53, 0xff53 -> KeyEvent.KEYCODE_DPAD_RIGHT
            0xFF54, 0xff54 -> KeyEvent.KEYCODE_DPAD_DOWN
            0xFF55, 0xff55 -> KeyEvent.KEYCODE_PAGE_UP
            0xFF56, 0xff56 -> KeyEvent.KEYCODE_PAGE_DOWN
            0xFF57, 0xff57 -> KeyEvent.KEYCODE_MOVE_END
            0xFF63, 0xff63 -> KeyEvent.KEYCODE_INSERT

            0xFFE1, 0xffe1 -> KeyEvent.KEYCODE_SHIFT_LEFT
            0xFFE2, 0xffe2 -> KeyEvent.KEYCODE_SHIFT_RIGHT
            0xFFE3, 0xffe3 -> KeyEvent.KEYCODE_CTRL_LEFT
            0xFFE4, 0xffe4 -> KeyEvent.KEYCODE_CTRL_RIGHT
            0xFFE5, 0xffe5 -> KeyEvent.KEYCODE_CAPS_LOCK
            0xFFE7, 0xffe7 -> KeyEvent.KEYCODE_META_LEFT
            0xFFE8, 0xffe8 -> KeyEvent.KEYCODE_META_RIGHT
            0xFFE9, 0xffe9 -> KeyEvent.KEYCODE_ALT_LEFT
            0xFFEA, 0xffea -> KeyEvent.KEYCODE_ALT_RIGHT
            0xFFEB, 0xffeb -> KeyEvent.KEYCODE_META_LEFT
            0xFFEC, 0xffec -> KeyEvent.KEYCODE_META_RIGHT
            0xFFED, 0xffed -> KeyEvent.KEYCODE_META_LEFT
            0xFFEE, 0xffee -> KeyEvent.KEYCODE_META_RIGHT

            0xFFAF, 0xffaf -> KeyEvent.KEYCODE_NUMPAD_DIVIDE
            0xFFAA, 0xffaa -> KeyEvent.KEYCODE_NUMPAD_MULTIPLY
            0xFFAD, 0xffad -> KeyEvent.KEYCODE_NUMPAD_SUBTRACT
            0xFFAB, 0xffab -> KeyEvent.KEYCODE_NUMPAD_ADD
            0xFF8D, 0xff8d -> KeyEvent.KEYCODE_NUMPAD_ENTER
            0xFFB0, 0xffb0 -> KeyEvent.KEYCODE_NUMPAD_0
            0xFFB1, 0xffb1 -> KeyEvent.KEYCODE_NUMPAD_1
            0xFFB2, 0xffb2 -> KeyEvent.KEYCODE_NUMPAD_2
            0xFFB3, 0xffb3 -> KeyEvent.KEYCODE_NUMPAD_3
            0xFFB4, 0xffb4 -> KeyEvent.KEYCODE_NUMPAD_4
            0xFFB5, 0xffb5 -> KeyEvent.KEYCODE_NUMPAD_5
            0xFFB6, 0xffb6 -> KeyEvent.KEYCODE_NUMPAD_6
            0xFFB7, 0xffb7 -> KeyEvent.KEYCODE_NUMPAD_7
            0xFFB8, 0xffb8 -> KeyEvent.KEYCODE_NUMPAD_8
            0xFFB9, 0xffb9 -> KeyEvent.KEYCODE_NUMPAD_9
            0xFFAE, 0xffae -> KeyEvent.KEYCODE_NUMPAD_DOT

            0xFF7F, 0xff7f -> KeyEvent.KEYCODE_NUM_LOCK
            0xFF14, 0xff14 -> KeyEvent.KEYCODE_SCROLL_LOCK

            0xFF61, 0xff61 -> KeyEvent.KEYCODE_SYSRQ
            0xFF13, 0xff13 -> KeyEvent.KEYCODE_BREAK

            0x20 -> KeyEvent.KEYCODE_SPACE
            0x21 -> KeyEvent.KEYCODE_1
            0x22 -> KeyEvent.KEYCODE_APOSTROPHE
            0x23 -> KeyEvent.KEYCODE_POUND
            0x24 -> KeyEvent.KEYCODE_4
            0x25 -> KeyEvent.KEYCODE_5
            0x26 -> KeyEvent.KEYCODE_7
            0x27 -> KeyEvent.KEYCODE_APOSTROPHE
            0x28 -> KeyEvent.KEYCODE_9
            0x29 -> KeyEvent.KEYCODE_0
            0x2a -> KeyEvent.KEYCODE_8
            0x2b -> KeyEvent.KEYCODE_EQUALS
            0x2c -> KeyEvent.KEYCODE_COMMA
            0x2d -> KeyEvent.KEYCODE_MINUS
            0x2e -> KeyEvent.KEYCODE_PERIOD
            0x2f -> KeyEvent.KEYCODE_SLASH
            0x3a -> KeyEvent.KEYCODE_SEMICOLON
            0x3b -> KeyEvent.KEYCODE_SEMICOLON
            0x3c -> KeyEvent.KEYCODE_COMMA
            0x3d -> KeyEvent.KEYCODE_EQUALS
            0x3e -> KeyEvent.KEYCODE_PERIOD
            0x3f -> KeyEvent.KEYCODE_SLASH
            0x40 -> KeyEvent.KEYCODE_AT
            0x5b -> KeyEvent.KEYCODE_LEFT_BRACKET
            0x5c -> KeyEvent.KEYCODE_BACKSLASH
            0x5d -> KeyEvent.KEYCODE_RIGHT_BRACKET
            0x5e -> KeyEvent.KEYCODE_6
            0x5f -> KeyEvent.KEYCODE_MINUS
            0x60 -> KeyEvent.KEYCODE_GRAVE
            0x7b -> KeyEvent.KEYCODE_LEFT_BRACKET
            0x7c -> KeyEvent.KEYCODE_BACKSLASH
            0x7d -> KeyEvent.KEYCODE_RIGHT_BRACKET
            0x7e -> KeyEvent.KEYCODE_GRAVE

            0x1008FF14 -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            0x1008FF15 -> KeyEvent.KEYCODE_MEDIA_PAUSE
            0x1008FF16 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            0x1008FF17 -> KeyEvent.KEYCODE_MEDIA_NEXT
            0x1008FF11 -> KeyEvent.KEYCODE_VOLUME_DOWN
            0x1008FF13 -> KeyEvent.KEYCODE_VOLUME_UP
            0x1008FF12 -> KeyEvent.KEYCODE_VOLUME_MUTE
            0x1008FF26 -> KeyEvent.KEYCODE_BACK
            0x1008FF27 -> KeyEvent.KEYCODE_FORWARD

            else -> {
                Log.w(TAG, "Unknown keysym: 0x${keysym.toString(16)} ($keysym)")
                KeyEvent.KEYCODE_UNKNOWN
            }
        }
    }

    /**
     * Map Android keycode to Linux input event scan code.
     */
    fun keycodeToScanCode(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT  -> 42
            KeyEvent.KEYCODE_SHIFT_RIGHT -> 54
            KeyEvent.KEYCODE_CTRL_LEFT   -> 29
            KeyEvent.KEYCODE_CTRL_RIGHT  -> 97
            KeyEvent.KEYCODE_ALT_LEFT    -> 56
            KeyEvent.KEYCODE_ALT_RIGHT   -> 100
            KeyEvent.KEYCODE_META_LEFT   -> 125
            KeyEvent.KEYCODE_META_RIGHT  -> 126
            KeyEvent.KEYCODE_CAPS_LOCK   -> 58

            KeyEvent.KEYCODE_ESCAPE -> 1
            KeyEvent.KEYCODE_DEL    -> 14
            KeyEvent.KEYCODE_TAB    -> 15
            KeyEvent.KEYCODE_ENTER  -> 28
            KeyEvent.KEYCODE_SPACE  -> 57

            KeyEvent.KEYCODE_DPAD_UP    -> 103
            KeyEvent.KEYCODE_DPAD_LEFT  -> 105
            KeyEvent.KEYCODE_DPAD_RIGHT -> 106
            KeyEvent.KEYCODE_DPAD_DOWN  -> 108

            KeyEvent.KEYCODE_INSERT      -> 110
            KeyEvent.KEYCODE_FORWARD_DEL -> 111
            KeyEvent.KEYCODE_MOVE_HOME   -> 102
            KeyEvent.KEYCODE_MOVE_END    -> 107
            KeyEvent.KEYCODE_PAGE_UP     -> 104
            KeyEvent.KEYCODE_PAGE_DOWN   -> 109

            KeyEvent.KEYCODE_F1  -> 59
            KeyEvent.KEYCODE_F2  -> 60
            KeyEvent.KEYCODE_F3  -> 61
            KeyEvent.KEYCODE_F4  -> 62
            KeyEvent.KEYCODE_F5  -> 63
            KeyEvent.KEYCODE_F6  -> 64
            KeyEvent.KEYCODE_F7  -> 65
            KeyEvent.KEYCODE_F8  -> 66
            KeyEvent.KEYCODE_F9  -> 67
            KeyEvent.KEYCODE_F10 -> 68
            KeyEvent.KEYCODE_F11 -> 87
            KeyEvent.KEYCODE_F12 -> 88

            KeyEvent.KEYCODE_1 -> 2
            KeyEvent.KEYCODE_2 -> 3
            KeyEvent.KEYCODE_3 -> 4
            KeyEvent.KEYCODE_4 -> 5
            KeyEvent.KEYCODE_5 -> 6
            KeyEvent.KEYCODE_6 -> 7
            KeyEvent.KEYCODE_7 -> 8
            KeyEvent.KEYCODE_8 -> 9
            KeyEvent.KEYCODE_9 -> 10
            KeyEvent.KEYCODE_0 -> 11

            KeyEvent.KEYCODE_Q -> 16
            KeyEvent.KEYCODE_W -> 17
            KeyEvent.KEYCODE_E -> 18
            KeyEvent.KEYCODE_R -> 19
            KeyEvent.KEYCODE_T -> 20
            KeyEvent.KEYCODE_Y -> 21
            KeyEvent.KEYCODE_U -> 22
            KeyEvent.KEYCODE_I -> 23
            KeyEvent.KEYCODE_O -> 24
            KeyEvent.KEYCODE_P -> 25
            KeyEvent.KEYCODE_A -> 30
            KeyEvent.KEYCODE_S -> 31
            KeyEvent.KEYCODE_D -> 32
            KeyEvent.KEYCODE_F -> 33
            KeyEvent.KEYCODE_G -> 34
            KeyEvent.KEYCODE_H -> 35
            KeyEvent.KEYCODE_J -> 36
            KeyEvent.KEYCODE_K -> 37
            KeyEvent.KEYCODE_L -> 38
            KeyEvent.KEYCODE_Z -> 44
            KeyEvent.KEYCODE_X -> 45
            KeyEvent.KEYCODE_C -> 46
            KeyEvent.KEYCODE_V -> 47
            KeyEvent.KEYCODE_B -> 48
            KeyEvent.KEYCODE_N -> 49
            KeyEvent.KEYCODE_M -> 50

            KeyEvent.KEYCODE_MINUS         -> 12
            KeyEvent.KEYCODE_EQUALS        -> 13
            KeyEvent.KEYCODE_LEFT_BRACKET  -> 26
            KeyEvent.KEYCODE_RIGHT_BRACKET -> 27
            KeyEvent.KEYCODE_BACKSLASH     -> 43
            KeyEvent.KEYCODE_SEMICOLON     -> 39
            KeyEvent.KEYCODE_APOSTROPHE    -> 40
            KeyEvent.KEYCODE_GRAVE         -> 41
            KeyEvent.KEYCODE_COMMA         -> 51
            KeyEvent.KEYCODE_PERIOD        -> 52
            KeyEvent.KEYCODE_SLASH         -> 53

            KeyEvent.KEYCODE_NUM_LOCK    -> 69
            KeyEvent.KEYCODE_SCROLL_LOCK -> 70

            KeyEvent.KEYCODE_SYSRQ -> 99
            KeyEvent.KEYCODE_BREAK -> 119

            else -> 0
        }
    }
}
