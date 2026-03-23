package com.inputleaf.android.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.protocol.KeysymTable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuInputInjector"

/**
 * Wrapper for Shizuku-based input injection.
 * Handles binding to the privileged InputInjectorService and translating
 * InputLeap events to Android input events.
 */
class ShizukuInputInjector(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    private var service: IInputInjector? = null
    private var isBound = false
    
    // Track absolute mouse position (InputLeap sends absolute coords, 
    // but we may need to synthesize relative movements)
    private var mouseX = 0f
    private var mouseY = 0f
    
    // Track button state for proper motion event sequencing
    private var buttonState = 0
    
    // Modifier key state (for meta state in key events)
    private var metaState = 0
    
    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            "com.inputleaf.android",
            InputInjectorService::class.java.name
        )
    ).daemon(false).processNameSuffix("input_injector")
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Shizuku service connected")
            service = IInputInjector.Stub.asInterface(binder)
            isBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Shizuku service disconnected")
            service = null
            isBound = false
        }
    }
    
    /**
     * Check if Shizuku is available and we have permission.
     */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && 
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Bind to the Shizuku service. Must be called before sending events.
     * @return true if binding was initiated successfully
     */
    suspend fun bind(): Boolean {
        if (!isAvailable()) {
            Log.e(TAG, "Shizuku not available or permission not granted")
            return false
        }
        
        return try {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
            
            // Wait for service to connect (with timeout)
            val connected = CompletableDeferred<Boolean>()
            var attempts = 0
            while (service == null && attempts < 50) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            
            if (service != null) {
                Log.d(TAG, "Shizuku service bound successfully")
                true
            } else {
                Log.e(TAG, "Shizuku service bind timeout")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind Shizuku service", e)
            false
        }
    }
    
    /**
     * Unbind from the Shizuku service.
     */
    fun unbind() {
        if (isBound) {
            try {
                service?.destroy()
                Shizuku.unbindUserService(serviceArgs, serviceConnection, true)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding Shizuku service", e)
            }
            service = null
            isBound = false
        }
    }
    
    /**
     * Send an InputLeap event to be injected.
     */
    fun send(event: InputLeapEvent) {
        val svc = service ?: return
        
        try {
            when (event) {
                is InputLeapEvent.MouseMoveAbs -> {
                    mouseX = event.x.toFloat().coerceIn(0f, screenWidth.toFloat())
                    mouseY = event.y.toFloat().coerceIn(0f, screenHeight.toFloat())
                    
                    // Determine action based on button state
                    val action = if (buttonState != 0) {
                        MotionEvent.ACTION_MOVE
                    } else {
                        MotionEvent.ACTION_HOVER_MOVE
                    }
                    svc.injectMotionEvent(action, mouseX, mouseY, buttonState)
                }
                
                is InputLeapEvent.MouseMoveRel -> {
                    mouseX = (mouseX + event.dx).coerceIn(0f, screenWidth.toFloat())
                    mouseY = (mouseY + event.dy).coerceIn(0f, screenHeight.toFloat())
                    
                    val action = if (buttonState != 0) {
                        MotionEvent.ACTION_MOVE
                    } else {
                        MotionEvent.ACTION_HOVER_MOVE
                    }
                    svc.injectMotionEvent(action, mouseX, mouseY, buttonState)
                }
                
                is InputLeapEvent.MouseDown -> {
                    val button = inputLeapButtonToAndroid(event.buttonId)
                    buttonState = buttonState or button
                    svc.injectMotionEvent(MotionEvent.ACTION_DOWN, mouseX, mouseY, buttonState)
                }
                
                is InputLeapEvent.MouseUp -> {
                    val button = inputLeapButtonToAndroid(event.buttonId)
                    buttonState = buttonState and button.inv()
                    svc.injectMotionEvent(MotionEvent.ACTION_UP, mouseX, mouseY, buttonState)
                }
                
                is InputLeapEvent.MouseWheel -> {
                    // InputLeap sends 120 units per notch, Android expects -1 to 1
                    val vScroll = event.yDelta / 120f
                    val hScroll = event.xDelta / 120f
                    svc.injectScrollEvent(mouseX, mouseY, hScroll, vScroll)
                }
                
                is InputLeapEvent.KeyDown -> {
                    Log.d(TAG, "KeyDown: keysym=0x${event.keyId.toString(16)} (${event.keyId})")
                    val keyCode = keysymToAndroidKeyCode(event.keyId)
                    Log.d(TAG, "Mapped to Android keyCode: $keyCode")
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        updateMetaState(keyCode, true)
                        svc.injectKeyEvent(KeyEvent.ACTION_DOWN, keyCode, keycodeToScanCode(keyCode), metaState)
                    }
                }
                
                is InputLeapEvent.KeyUp -> {
                    Log.d(TAG, "KeyUp: keysym=0x${event.keyId.toString(16)} (${event.keyId})")
                    val keyCode = keysymToAndroidKeyCode(event.keyId)
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        svc.injectKeyEvent(KeyEvent.ACTION_UP, keyCode, keycodeToScanCode(keyCode), metaState)
                        updateMetaState(keyCode, false)
                    }
                }
                
                is InputLeapEvent.KeyRepeat -> {
                    // For repeat, just send another DOWN event
                    val keyCode = keysymToAndroidKeyCode(event.keyId)
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        val scanCode = keycodeToScanCode(keyCode)
                        repeat(event.count) {
                            svc.injectKeyEvent(KeyEvent.ACTION_DOWN, keyCode, scanCode, metaState)
                        }
                    }
                }
                
                else -> {
                    // Ignore non-input events
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject event", e)
        }
    }
    
    private fun inputLeapButtonToAndroid(buttonId: Int): Int {
        // InputLeap button IDs: 1=left, 2=middle, 3=right
        return when (buttonId) {
            1 -> MotionEvent.BUTTON_PRIMARY
            2 -> MotionEvent.BUTTON_TERTIARY  // middle
            3 -> MotionEvent.BUTTON_SECONDARY // right
            else -> 0
        }
    }
    
    private fun updateMetaState(keyCode: Int, isDown: Boolean) {
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
        
        metaState = if (isDown) {
            metaState or metaFlag
        } else {
            metaState and metaFlag.inv()
        }
    }
    
    /**
     * Convert X11 keysym to Android keycode.
     * X11 keysym values: https://www.cl.cam.ac.uk/~mgk25/ucs/keysyms.txt
     * 
     * Note: InputLeap may send keysyms in a different range (0xEFxx instead of 0xFFxx)
     * due to protocol differences. We handle both.
     */
    private fun keysymToAndroidKeyCode(keysym: Int): Int {
        // Normalize keysyms: InputLeap sometimes sends 0xEFxx instead of 0xFFxx
        val normalizedKeysym = when {
            keysym in 0xEF00..0xEFFF -> keysym + 0x1000  // Convert 0xEFxx to 0xFFxx
            else -> keysym
        }
        
        // Common keysyms (X11)
        return when (normalizedKeysym) {
            // Letters (lowercase a-z: 0x61-0x7a)
            in 0x61..0x7a -> KeyEvent.KEYCODE_A + (normalizedKeysym - 0x61)
            // Letters (uppercase A-Z: 0x41-0x5a) - same keycodes
            in 0x41..0x5a -> KeyEvent.KEYCODE_A + (normalizedKeysym - 0x41)
            
            // Numbers (0-9: 0x30-0x39)
            in 0x30..0x39 -> KeyEvent.KEYCODE_0 + (normalizedKeysym - 0x30)
            
            // Function keys F1-F12 (XK_F1 = 0xFFBE, XK_F12 = 0xFFC9)
            in 0xFFBE..0xFFC9, in 0xffbe..0xffc9 -> {
                val base = if (normalizedKeysym >= 0xFFBE) normalizedKeysym - 0xFFBE else normalizedKeysym - 0xffbe
                KeyEvent.KEYCODE_F1 + base
            }
            
            // Control keys (handle both upper and lower hex)
            0xFF08, 0xff08 -> KeyEvent.KEYCODE_DEL         // XK_BackSpace -> Android's DEL (backspace)
            0xFF09, 0xff09 -> KeyEvent.KEYCODE_TAB         // XK_Tab
            0xFF0D, 0xff0d -> KeyEvent.KEYCODE_ENTER       // XK_Return
            0xFF0A, 0xff0a -> KeyEvent.KEYCODE_ENTER       // XK_Linefeed (also enter)
            0xFF1B, 0xff1b -> KeyEvent.KEYCODE_ESCAPE      // XK_Escape
            0xFFFF, 0xffff -> KeyEvent.KEYCODE_FORWARD_DEL // XK_Delete
            0xFF50, 0xff50 -> KeyEvent.KEYCODE_MOVE_HOME   // XK_Home
            0xFF51, 0xff51 -> KeyEvent.KEYCODE_DPAD_LEFT   // XK_Left
            0xFF52, 0xff52 -> KeyEvent.KEYCODE_DPAD_UP     // XK_Up
            0xFF53, 0xff53 -> KeyEvent.KEYCODE_DPAD_RIGHT  // XK_Right
            0xFF54, 0xff54 -> KeyEvent.KEYCODE_DPAD_DOWN   // XK_Down
            0xFF55, 0xff55 -> KeyEvent.KEYCODE_PAGE_UP     // XK_Page_Up
            0xFF56, 0xff56 -> KeyEvent.KEYCODE_PAGE_DOWN   // XK_Page_Down
            0xFF57, 0xff57 -> KeyEvent.KEYCODE_MOVE_END    // XK_End
            0xFF63, 0xff63 -> KeyEvent.KEYCODE_INSERT      // XK_Insert
            
            // Modifiers
            0xFFE1, 0xffe1 -> KeyEvent.KEYCODE_SHIFT_LEFT  // XK_Shift_L
            0xFFE2, 0xffe2 -> KeyEvent.KEYCODE_SHIFT_RIGHT // XK_Shift_R
            0xFFE3, 0xffe3 -> KeyEvent.KEYCODE_CTRL_LEFT   // XK_Control_L
            0xFFE4, 0xffe4 -> KeyEvent.KEYCODE_CTRL_RIGHT  // XK_Control_R
            0xFFE5, 0xffe5 -> KeyEvent.KEYCODE_CAPS_LOCK   // XK_Caps_Lock
            0xFFE7, 0xffe7 -> KeyEvent.KEYCODE_META_LEFT   // XK_Meta_L
            0xFFE8, 0xffe8 -> KeyEvent.KEYCODE_META_RIGHT  // XK_Meta_R
            0xFFE9, 0xffe9 -> KeyEvent.KEYCODE_ALT_LEFT    // XK_Alt_L
            0xFFEA, 0xffea -> KeyEvent.KEYCODE_ALT_RIGHT   // XK_Alt_R
            0xFFEB, 0xffeb -> KeyEvent.KEYCODE_META_LEFT   // XK_Super_L (Win/Cmd key)
            0xFFEC, 0xffec -> KeyEvent.KEYCODE_META_RIGHT  // XK_Super_R
            0xFFED, 0xffed -> KeyEvent.KEYCODE_META_LEFT   // XK_Hyper_L
            0xFFEE, 0xffee -> KeyEvent.KEYCODE_META_RIGHT  // XK_Hyper_R
            
            // Numpad
            0xFFAF, 0xffaf -> KeyEvent.KEYCODE_NUMPAD_DIVIDE   // XK_KP_Divide
            0xFFAA, 0xffaa -> KeyEvent.KEYCODE_NUMPAD_MULTIPLY // XK_KP_Multiply
            0xFFAD, 0xffad -> KeyEvent.KEYCODE_NUMPAD_SUBTRACT // XK_KP_Subtract
            0xFFAB, 0xffab -> KeyEvent.KEYCODE_NUMPAD_ADD      // XK_KP_Add
            0xFF8D, 0xff8d -> KeyEvent.KEYCODE_NUMPAD_ENTER    // XK_KP_Enter
            0xFFB0, 0xffb0 -> KeyEvent.KEYCODE_NUMPAD_0        // XK_KP_0
            0xFFB1, 0xffb1 -> KeyEvent.KEYCODE_NUMPAD_1        // XK_KP_1
            0xFFB2, 0xffb2 -> KeyEvent.KEYCODE_NUMPAD_2        // XK_KP_2
            0xFFB3, 0xffb3 -> KeyEvent.KEYCODE_NUMPAD_3        // XK_KP_3
            0xFFB4, 0xffb4 -> KeyEvent.KEYCODE_NUMPAD_4        // XK_KP_4
            0xFFB5, 0xffb5 -> KeyEvent.KEYCODE_NUMPAD_5        // XK_KP_5
            0xFFB6, 0xffb6 -> KeyEvent.KEYCODE_NUMPAD_6        // XK_KP_6
            0xFFB7, 0xffb7 -> KeyEvent.KEYCODE_NUMPAD_7        // XK_KP_7
            0xFFB8, 0xffb8 -> KeyEvent.KEYCODE_NUMPAD_8        // XK_KP_8
            0xFFB9, 0xffb9 -> KeyEvent.KEYCODE_NUMPAD_9        // XK_KP_9
            0xFFAE, 0xffae -> KeyEvent.KEYCODE_NUMPAD_DOT      // XK_KP_Decimal
            
            // Lock keys
            0xFF7F, 0xff7f -> KeyEvent.KEYCODE_NUM_LOCK        // XK_Num_Lock
            0xFF14, 0xff14 -> KeyEvent.KEYCODE_SCROLL_LOCK     // XK_Scroll_Lock
            
            // Print/Pause/Break
            0xFF61, 0xff61 -> KeyEvent.KEYCODE_SYSRQ           // XK_Print
            0xFF13, 0xff13 -> KeyEvent.KEYCODE_BREAK           // XK_Pause
            
            // Punctuation and symbols
            0x20 -> KeyEvent.KEYCODE_SPACE
            0x21 -> KeyEvent.KEYCODE_1             // ! (shift+1)
            0x22 -> KeyEvent.KEYCODE_APOSTROPHE    // " (shift+')
            0x23 -> KeyEvent.KEYCODE_POUND         // #
            0x24 -> KeyEvent.KEYCODE_4             // $ (shift+4)
            0x25 -> KeyEvent.KEYCODE_5             // % (shift+5)
            0x26 -> KeyEvent.KEYCODE_7             // & (shift+7)
            0x27 -> KeyEvent.KEYCODE_APOSTROPHE    // '
            0x28 -> KeyEvent.KEYCODE_9             // ( (shift+9)
            0x29 -> KeyEvent.KEYCODE_0             // ) (shift+0)
            0x2a -> KeyEvent.KEYCODE_8             // * (shift+8)
            0x2b -> KeyEvent.KEYCODE_EQUALS        // + (shift+=)
            0x2c -> KeyEvent.KEYCODE_COMMA         // ,
            0x2d -> KeyEvent.KEYCODE_MINUS         // -
            0x2e -> KeyEvent.KEYCODE_PERIOD        // .
            0x2f -> KeyEvent.KEYCODE_SLASH         // /
            0x3a -> KeyEvent.KEYCODE_SEMICOLON     // : (shift+;)
            0x3b -> KeyEvent.KEYCODE_SEMICOLON     // ;
            0x3c -> KeyEvent.KEYCODE_COMMA         // < (shift+,)
            0x3d -> KeyEvent.KEYCODE_EQUALS        // =
            0x3e -> KeyEvent.KEYCODE_PERIOD        // > (shift+.)
            0x3f -> KeyEvent.KEYCODE_SLASH         // ? (shift+/)
            0x40 -> KeyEvent.KEYCODE_AT            // @
            0x5b -> KeyEvent.KEYCODE_LEFT_BRACKET  // [
            0x5c -> KeyEvent.KEYCODE_BACKSLASH     // \
            0x5d -> KeyEvent.KEYCODE_RIGHT_BRACKET // ]
            0x5e -> KeyEvent.KEYCODE_6             // ^ (shift+6)
            0x5f -> KeyEvent.KEYCODE_MINUS         // _ (shift+-)
            0x60 -> KeyEvent.KEYCODE_GRAVE         // `
            0x7b -> KeyEvent.KEYCODE_LEFT_BRACKET  // { (shift+[)
            0x7c -> KeyEvent.KEYCODE_BACKSLASH     // | (shift+\)
            0x7d -> KeyEvent.KEYCODE_RIGHT_BRACKET // } (shift+])
            0x7e -> KeyEvent.KEYCODE_GRAVE         // ~ (shift+`)
            
            // Media keys (XFree86 keysyms)
            0x1008FF14 -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE  // XF86AudioPlay
            0x1008FF15 -> KeyEvent.KEYCODE_MEDIA_PAUSE       // XF86AudioPause
            0x1008FF16 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS    // XF86AudioPrev
            0x1008FF17 -> KeyEvent.KEYCODE_MEDIA_NEXT        // XF86AudioNext
            0x1008FF11 -> KeyEvent.KEYCODE_VOLUME_DOWN       // XF86AudioLowerVolume
            0x1008FF13 -> KeyEvent.KEYCODE_VOLUME_UP         // XF86AudioRaiseVolume
            0x1008FF12 -> KeyEvent.KEYCODE_VOLUME_MUTE       // XF86AudioMute
            0x1008FF26 -> KeyEvent.KEYCODE_BACK              // XF86Back
            0x1008FF27 -> KeyEvent.KEYCODE_FORWARD           // XF86Forward
            
            else -> {
                Log.w(TAG, "Unknown keysym: 0x${keysym.toString(16)} ($keysym)")
                KeyEvent.KEYCODE_UNKNOWN
            }
        }
    }
    
    /**
     * Map Android keycode to Linux input event scan code.
     * These are the KEY_* constants from linux/input-event-codes.h.
     * Providing correct scan codes helps OEM frameworks (especially MIUI/HyperOS)
     * properly identify modifier keys.
     */
    private fun keycodeToScanCode(keyCode: Int): Int {
        return when (keyCode) {
            // Modifiers
            KeyEvent.KEYCODE_SHIFT_LEFT  -> 42   // KEY_LEFTSHIFT
            KeyEvent.KEYCODE_SHIFT_RIGHT -> 54   // KEY_RIGHTSHIFT
            KeyEvent.KEYCODE_CTRL_LEFT   -> 29   // KEY_LEFTCTRL
            KeyEvent.KEYCODE_CTRL_RIGHT  -> 97   // KEY_RIGHTCTRL
            KeyEvent.KEYCODE_ALT_LEFT    -> 56   // KEY_LEFTALT
            KeyEvent.KEYCODE_ALT_RIGHT   -> 100  // KEY_RIGHTALT
            KeyEvent.KEYCODE_META_LEFT   -> 125  // KEY_LEFTMETA (Super/Win)
            KeyEvent.KEYCODE_META_RIGHT  -> 126  // KEY_RIGHTMETA
            KeyEvent.KEYCODE_CAPS_LOCK   -> 58   // KEY_CAPSLOCK
            
            // Common keys
            KeyEvent.KEYCODE_ESCAPE -> 1
            KeyEvent.KEYCODE_DEL    -> 14   // KEY_BACKSPACE
            KeyEvent.KEYCODE_TAB    -> 15
            KeyEvent.KEYCODE_ENTER  -> 28
            KeyEvent.KEYCODE_SPACE  -> 57
            
            // Arrow keys
            KeyEvent.KEYCODE_DPAD_UP    -> 103
            KeyEvent.KEYCODE_DPAD_LEFT  -> 105
            KeyEvent.KEYCODE_DPAD_RIGHT -> 106
            KeyEvent.KEYCODE_DPAD_DOWN  -> 108
            
            // Navigation
            KeyEvent.KEYCODE_INSERT      -> 110
            KeyEvent.KEYCODE_FORWARD_DEL -> 111  // KEY_DELETE
            KeyEvent.KEYCODE_MOVE_HOME   -> 102
            KeyEvent.KEYCODE_MOVE_END    -> 107
            KeyEvent.KEYCODE_PAGE_UP     -> 104
            KeyEvent.KEYCODE_PAGE_DOWN   -> 109
            
            // Function keys (F1-F12)
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
            
            // Number row
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
            
            // Letters (Q row, A row, Z row)
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
            
            // Punctuation
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
            
            // Lock keys
            KeyEvent.KEYCODE_NUM_LOCK    -> 69
            KeyEvent.KEYCODE_SCROLL_LOCK -> 70
            
            // Print/Pause
            KeyEvent.KEYCODE_SYSRQ -> 99
            KeyEvent.KEYCODE_BREAK -> 119  // KEY_PAUSE
            
            else -> 0  // Unknown scan code
        }
    }
}
