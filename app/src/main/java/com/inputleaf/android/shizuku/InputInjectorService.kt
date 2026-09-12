package com.inputleaf.android.shizuku

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.inputleaf.android.shizuku.uhid.AccelerationCurve
import com.inputleaf.android.shizuku.uhid.HidKeyboard
import com.inputleaf.android.shizuku.uhid.RelativePointer
import com.inputleaf.android.shizuku.uhid.SoftKeyboardToggle
import com.inputleaf.android.shizuku.uhid.UhidChannel
import com.inputleaf.android.shizuku.uhid.UhidDiagnostics

/**
 * Shizuku UserService that runs with shell (ADB) privileges.
 * This service can call InputManager.injectInputEvent() because the shell user
 * has the INJECT_EVENTS permission.
 * 
 * This class is instantiated by Shizuku in a separate process with elevated privileges.
 */
class InputInjectorService : IInputInjector.Stub() {

    private val inputManager: HiddenInputManager.Target? =
        try {
            HiddenInputManager.resolve()
        } catch (e: Throwable) {
            android.util.Log.e("InputInjectorService", "Failed to resolve InputManager", e)
            null
        }
    
    companion object {
        private const val POINTER_DEVICE_NAME = "Input Leaf Pointer"
        private const val KEYBOARD_DEVICE_NAME = "Input Leaf Keyboard HID"
        private const val TICK_MS = 8L

        // Injection mode: async (don't wait for injection to complete)
        private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
        // Wait until the system reports whether text injection was accepted.
        private const val INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 1
        
        // Mouse pointer properties
        private val POINTER_PROPERTIES = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
        )
    }
    
    private var lastDownTime: Long = 0
    
    override fun injectMotionEvent(action: Int, x: Float, y: Float, buttonState: Int): Boolean {
        return try {
            val now = SystemClock.uptimeMillis()
            
            // Track down time for proper event sequencing
            if (action == MotionEvent.ACTION_DOWN || 
                action == MotionEvent.ACTION_BUTTON_PRESS) {
                lastDownTime = now
            }
            
            val pointerCoords = arrayOf(
                MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    pressure = 1.0f
                    size = 1.0f
                }
            )
            
            val event = MotionEvent.obtain(
                lastDownTime,      // downTime
                now,               // eventTime
                action,            // action
                1,                 // pointerCount
                POINTER_PROPERTIES,
                pointerCoords,
                0,                 // metaState
                buttonState,       // buttonState
                1.0f,              // xPrecision
                1.0f,              // yPrecision
                0,                 // deviceId (0 = virtual)
                0,                 // edgeFlags
                InputDevice.SOURCE_MOUSE,
                0                  // flags
            )
            
            val result = HiddenInputManager.inject(
                inputManager,
                event,
                INJECT_INPUT_EVENT_MODE_ASYNC
            )

            event.recycle()
            result
        } catch (e: Exception) {
            android.util.Log.e("InputInjectorService", "Failed to inject motion event", e)
            false
        }
    }
    
    override fun injectScrollEvent(x: Float, y: Float, hScroll: Float, vScroll: Float): Boolean {
        return try {
            val now = SystemClock.uptimeMillis()
            
            val pointerCoords = arrayOf(
                MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    pressure = 0f
                    size = 0f
                    setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
                    setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
                }
            )
            
            val event = MotionEvent.obtain(
                now,               // downTime
                now,               // eventTime
                MotionEvent.ACTION_SCROLL,
                1,                 // pointerCount
                POINTER_PROPERTIES,
                pointerCoords,
                0,                 // metaState
                0,                 // buttonState
                1.0f,              // xPrecision
                1.0f,              // yPrecision
                0,                 // deviceId
                0,                 // edgeFlags
                InputDevice.SOURCE_MOUSE,
                0                  // flags
            )
            
            val result = HiddenInputManager.inject(
                inputManager,
                event,
                INJECT_INPUT_EVENT_MODE_ASYNC
            )

            event.recycle()
            result
        } catch (e: Exception) {
            android.util.Log.e("InputInjectorService", "Failed to inject scroll event", e)
            false
        }
    }
    
    override fun injectKeyEvent(action: Int, keyCode: Int, scanCode: Int, metaState: Int): Boolean {
        return try {
            val now = SystemClock.uptimeMillis()
            
            val event = KeyEvent(
                now,        // downTime
                now,        // eventTime
                action,     // action (ACTION_DOWN or ACTION_UP)
                keyCode,    // keyCode
                0,          // repeat
                metaState,  // metaState (modifiers)
                -1,         // deviceId (-1 = virtual device)
                scanCode,   // scanCode (Linux input event code)
                KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD
            )
            
            HiddenInputManager.inject(
                inputManager,
                event,
                INJECT_INPUT_EVENT_MODE_ASYNC
            )
        } catch (e: Exception) {
            android.util.Log.e("InputInjectorService", "Failed to inject key event", e)
            false
        }
    }

    override fun injectText(text: String): Boolean {
        if (text.isEmpty()) {
            return true
        }
        return try {
            // `adb shell input text` maps characters through KeyCharacterMap, which has
            // no Cyrillic/Gujarati keys on a typical device and exits non-zero. Inject a
            // KEYCODE_UNKNOWN ACTION_MULTIPLE event with the Unicode string instead.
            val now = SystemClock.uptimeMillis()
            val event = KeyEvent(now, text, -1, KeyEvent.FLAG_FROM_SYSTEM)
            event.source = InputDevice.SOURCE_KEYBOARD
            HiddenInputManager.inject(
                inputManager,
                event,
                INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT
            )
        } catch (e: Exception) {
            android.util.Log.e("InputInjectorService", "Failed to inject text", e)
            false
        }
    }
    
    // --- HID pointer -------------------------------------------------------------
    // Guarded because Shizuku can bind more than one injector instance: an earlier build
    // opened /dev/uhid twice from two threads (see logcat.log, PIDs 11476/11477).
    private val uhidLock = Any()
    // Binder dispatches AIDL calls on a thread pool. Without this, moves arriving during
    // the ~150ms entry-correction loop ran concurrently with it, and the loop dragged the
    // cursor back to the entry point while the user was already moving away.
    private val pointerLock = Any()
    private var uhidChannel: UhidChannel? = null
    private var pointer: RelativePointer? = null
    private var uhidKeyboardChannel: UhidChannel? = null
    private var keyboard: HidKeyboard? = null
    private val softKeyboardToggle = SoftKeyboardToggle()
    // Emits queued pointer motion. Movement is rate-limited to keep Android's gain
    // constant, so a large move needs several reports rather than one.
    private var pacer: Thread? = null

    override fun openVirtualPointer(): Boolean = synchronized(uhidLock) {
        UhidDiagnostics.log("openVirtualPointer() called; alreadyOpen=${uhidChannel != null}")
        if (uhidChannel != null) return true
        val channel = UhidChannel.open(POINTER_DEVICE_NAME, RelativePointer.DESCRIPTOR)
            ?: run {
                UhidDiagnostics.log("openVirtualPointer() -> false (pointer channel null)")
                return false
            }
        uhidChannel = channel
        val instance = RelativePointer(channel, diag = { UhidDiagnostics.log(it) })
        pointer = instance
        startPacer(instance)
        UhidDiagnostics.log(
            "HID pointer ready; gain from AccelerationCurve " +
                "(resting ${AccelerationCurve.RESTING_GAIN}), pointer_speed=${readPointerSpeed()}"
        )
        android.util.Log.i("InputInjectorService", "HID pointer ready on /dev/uhid")

        // The keyboard is a separate device and strictly optional: if it fails, keys keep
        // flowing through injectKeyEvent and only the pointer benefits.
        val keyboardChannel = UhidChannel.open(KEYBOARD_DEVICE_NAME, HidKeyboard.DESCRIPTOR)
        if (keyboardChannel != null) {
            uhidKeyboardChannel = keyboardChannel
            keyboard = HidKeyboard(keyboardChannel)
            softKeyboardToggle.remember()
            android.util.Log.i("InputInjectorService", "HID keyboard ready on /dev/uhid")
        } else {
            android.util.Log.w("InputInjectorService", "HID keyboard unavailable; keys use injectKeyEvent")
        }
        return true
    }

    override fun closeVirtualPointer() = synchronized(uhidLock) {
        runCatching { pacer?.interrupt() }
        pacer = null
        runCatching { keyboard?.releaseAll() }
        runCatching { softKeyboardToggle.restore() }
        runCatching { uhidKeyboardChannel?.close() }
        runCatching { uhidChannel?.close() }
        uhidChannel = null
        uhidKeyboardChannel = null
        pointer = null
        keyboard = null
    }

    override fun injectHidKey(evdevCode: Int, isDown: Boolean): Boolean =
        synchronized(uhidLock) { keyboard }?.key(evdevCode, isDown) ?: false

    override fun releaseHidKeys() {
        synchronized(uhidLock) { keyboard }?.releaseAll()
    }

    override fun toggleSoftKeyboard(): Boolean = softKeyboardToggle.toggle()

    override fun isSoftKeyboardShown(): Boolean = softKeyboardToggle.isShown()

    override fun isVirtualPointerOpen(): Boolean = synchronized(uhidLock) { uhidChannel != null }

    override fun movePointerAbsolute(x: Int, y: Int, screenWidth: Int, screenHeight: Int) {
        synchronized(pointerLock) { movePointerAbsoluteLocked(x, y, screenWidth, screenHeight) }
    }

    private fun movePointerAbsoluteLocked(x: Int, y: Int, screenWidth: Int, screenHeight: Int) {
        if (logNextMove) {
            logNextMove = false
            val xPercent = if (screenWidth > 0) x * 100 / screenWidth else -1
            val yPercent = if (screenHeight > 0) y * 100 / screenHeight else -1
            UhidDiagnostics.log(
                "entry point from server: ($x, $y) on ${screenWidth}x$screenHeight " +
                    "= ${xPercent}% across, ${yPercent}% down"
            )
        }
        synchronized(uhidLock) { pointer }?.moveTo(x, y, screenWidth, screenHeight)
    }

    /** Set on resync so the first move after a border crossing is logged. */
    @Volatile
    private var logNextMove = false

    /** Current `pointer_speed`, only for the diagnostics line. */
    private fun readPointerSpeed(): String = try {
        val process = ProcessBuilder("settings", "get", "system", "pointer_speed")
            .redirectErrorStream(true).start()
        val value = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        value
    } catch (e: Exception) {
        "unknown"
    }

    /**
     * Drives [RelativePointer.drain] at ~125 Hz. Motion is rate-limited so the gain stays
     * in Android's constant segment, which means a long move is delivered over several
     * reports instead of one oversized one.
     */
    private fun startPacer(instance: RelativePointer) {
        pacer?.interrupt()
        pacer = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(TICK_MS)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                runCatching { synchronized(pointerLock) { instance.drain() } }
            }
        }, "pointer-pacer").apply {
            isDaemon = true
            start()
        }
    }

    override fun resyncPointer() {
        synchronized(pointerLock) { synchronized(uhidLock) { pointer }?.resync() }
        // The move straight after a resync is the border crossing, so record where the
        // server actually placed us versus the screen it thinks we have.
        logNextMove = true
    }

    override fun pointerButton(buttonId: Int, pressed: Boolean) {
        synchronized(pointerLock) { synchronized(uhidLock) { pointer }?.button(buttonId, pressed) }
    }

    override fun pointerWheel(horizontal: Int, vertical: Int) {
        synchronized(pointerLock) { synchronized(uhidLock) { pointer }?.wheel(horizontal, vertical) }
    }

    override fun destroy() {
        closeVirtualPointer()
    }
}
