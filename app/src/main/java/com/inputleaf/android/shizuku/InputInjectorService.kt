package com.inputleaf.android.shizuku

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.inputleaf.android.shizuku.uhid.HidKeyboard
import com.inputleaf.android.shizuku.uhid.HidMouse
import com.inputleaf.android.shizuku.uhid.HidMouseEnterWarp
import com.inputleaf.android.shizuku.uhid.UhidChannel

/**
 * Shizuku UserService that runs with shell (ADB) privileges.
 * This service can call InputManager.injectInputEvent() because the shell user
 * has the INJECT_EVENTS permission.
 * 
 * This class is instantiated by Shizuku in a separate process with elevated privileges.
 */
class InputInjectorService : IInputInjector.Stub {

    /** Seam so the UHID lifecycle can be exercised without a real `/dev/uhid`. */
    private val openChannel: () -> UhidChannel?

    constructor() : this({ UhidChannel.openHandle() })

    internal constructor(openChannel: () -> UhidChannel?) {
        this.openChannel = openChannel
    }

    private val inputManager: HiddenInputManager.Target? =
        try {
            HiddenInputManager.resolve()
        } catch (e: Throwable) {
            android.util.Log.e("InputInjectorService", "Failed to resolve InputManager", e)
            null
        }
    
    companion object {
        private const val KEYBOARD_DEVICE_NAME = "Input Leaf Keyboard HID"
        private const val MOUSE_DEVICE_NAME = "Input Leaf Mouse HID"
        private const val VENDOR_ID = 0x1209
        private const val PRODUCT_KEYBOARD = 0x0001
        private const val PRODUCT_MOUSE = 0x0002
        private const val UNIQ_KEYBOARD = "inputleaf-kbd"
        private const val UNIQ_MOUSE = "inputleaf-mouse"
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

    private val keyboardLock = Any()
    private val mouseLock = Any()
    private var uhidKeyboardChannel: UhidChannel? = null
    private var keyboard: HidKeyboard? = null
    private var uhidMouseChannel: UhidChannel? = null
    private var mouse: HidMouse? = null
    private val mouseEnterWarp = HidMouseEnterWarp()
    /** Guarded by [mouseLock]. True only while an [openVirtualMouse] that created the device warped. */
    private var lastOpenWarpApplied = false

    override fun openVirtualKeyboard(): Boolean = synchronized(keyboardLock) {
        if (keyboard != null) return true
        val channel = openChannel() ?: return false
        return try {
            val startedAt = android.os.SystemClock.uptimeMillis()
            channel.createDevice(
                KEYBOARD_DEVICE_NAME,
                HidKeyboard.DESCRIPTOR,
                vendor = VENDOR_ID,
                product = PRODUCT_KEYBOARD,
                uniq = UNIQ_KEYBOARD,
            )
            uhidKeyboardChannel = channel
            keyboard = HidKeyboard(channel)
            android.util.Log.i(
                "InputInjectorService",
                "HID keyboard connected in ${android.os.SystemClock.uptimeMillis() - startedAt}ms pid=${android.os.Process.myPid()}",
            )
            true
        } catch (e: Exception) {
            android.util.Log.w("InputInjectorService", "HID keyboard create failed", e)
            runCatching { channel.close() }
            false
        }
    }

    override fun closeVirtualKeyboard() {
        synchronized(keyboardLock) {
            runCatching { keyboard?.releaseAll() }
            runCatching { uhidKeyboardChannel?.close() }
            uhidKeyboardChannel = null
            keyboard = null
        }
        android.util.Log.i("InputInjectorService", "HID keyboard disconnected")
    }

    override fun injectHidKey(evdevCode: Int, isDown: Boolean): Boolean =
        synchronized(keyboardLock) { keyboard }?.key(evdevCode, isDown) ?: false

    override fun releaseHidKeys() {
        synchronized(keyboardLock) { keyboard }?.releaseAll()
    }

    override fun openVirtualMouse(): Boolean = synchronized(mouseLock) {
        if (mouse != null) {
            // No CREATE2, so no warp was emitted; the client must send its own snap.
            lastOpenWarpApplied = false
            android.util.Log.i("InputInjectorService", "HID mouse already open (idempotent)")
            return true
        }
        val channel = openChannel() ?: return false
        return try {
            val startedAt = android.os.SystemClock.uptimeMillis()
            channel.createDevice(
                MOUSE_DEVICE_NAME,
                HidMouse.DESCRIPTOR,
                vendor = VENDOR_ID,
                product = PRODUCT_MOUSE,
                uniq = UNIQ_MOUSE,
            )
            uhidMouseChannel = channel
            val hidMouse = HidMouse(channel)
            mouse = hidMouse
            val pending = mouseEnterWarp.pending
            val plans = mouseEnterWarp.applyIfPending(hidMouse)
            lastOpenWarpApplied = pending != null && mouseEnterWarp.pending == null
            android.util.Log.i(
                "InputInjectorService",
                "HID mouse connected in ${android.os.SystemClock.uptimeMillis() - startedAt}ms pid=${android.os.Process.myPid()}",
            )
            if (pending != null) {
                android.util.Log.i(
                    "InputInjectorService",
                    "HID mouse enter warp from ${(pending.maxX) / 2},${(pending.maxY) / 2} " +
                        "to ${pending.x},${pending.y} speed=${pending.pointerSpeed} " +
                        "${if (lastOpenWarpApplied) "applied after UHID ready" else "kept pending"} " +
                        "hidReports=${plans.count { !it.isNoOp }}",
                )
            }
            true
        } catch (e: Exception) {
            android.util.Log.w("InputInjectorService", "HID mouse create failed", e)
            lastOpenWarpApplied = false
            runCatching { channel.close() }
            false
        }
    }

    override fun closeVirtualMouse() {
        synchronized(mouseLock) {
            runCatching { mouse?.releaseAll() }
            runCatching { uhidMouseChannel?.close() }
            uhidMouseChannel = null
            mouse = null
        }
        android.util.Log.i("InputInjectorService", "HID mouse disconnected")
    }

    override fun injectHidMouse(dx: Int, dy: Int, buttons: Int, wheel: Int): Boolean =
        synchronized(mouseLock) { mouse }?.move(dx, dy, buttons, wheel) ?: false
    
    override fun onHidMouseEnter(x: Int, y: Int, maxX: Int, maxY: Int, pointerSpeed: Int) {
        synchronized(mouseLock) {
            mouseEnterWarp.onEnter(x, y, maxX, maxY, pointerSpeed)
            android.util.Log.i(
                "InputInjectorService",
                "HID mouse enter stored $x,$y max=$maxX,$maxY speed=$pointerSpeed mouseOpen=${mouse != null}",
            )
        }
    }

    override fun onHidMouseLeave() {
        synchronized(mouseLock) { mouseEnterWarp.onLeave() }
    }

    override fun consumeEnterWarpApplied(): Boolean = synchronized(mouseLock) {
        val applied = lastOpenWarpApplied
        lastOpenWarpApplied = false
        applied
    }

    private val clientLock = Any()
    private var clientToken: android.os.IBinder? = null

    /**
     * Destroy the UHID devices from inside the process that owns the `/dev/uhid` fds
     * when the client goes away, instead of relying on this process being reaped.
     *
     * The explicit teardown in the client's disconnect() cannot help here: once the
     * client is gone those binder calls throw DeadObjectException and are swallowed, so
     * no UHID_DESTROY is ever written and the devices stay attached for as long as this
     * process lingers -- which on some OEM Shizuku builds is a long time.
     */
    private val clientDeathRecipient = android.os.IBinder.DeathRecipient {
        android.util.Log.w("InputInjectorService", "Client died; destroying UHID devices")
        closeVirtualKeyboard()
        closeVirtualMouse()
    }

    override fun attachClient(token: android.os.IBinder?) {
        if (token == null) return
        synchronized(clientLock) {
            runCatching { clientToken?.unlinkToDeath(clientDeathRecipient, 0) }
            clientToken = token
            // A token that is already dead throws here rather than calling back, so the
            // devices have to be torn down inline.
            val linked = runCatching { token.linkToDeath(clientDeathRecipient, 0) }.isSuccess
            if (!linked) {
                clientToken = null
                android.util.Log.w("InputInjectorService", "Client token already dead at attach")
                closeVirtualKeyboard()
                closeVirtualMouse()
            }
        }
    }

    override fun destroy() {
        synchronized(clientLock) {
            runCatching { clientToken?.unlinkToDeath(clientDeathRecipient, 0) }
            clientToken = null
        }
        closeVirtualKeyboard()
        closeVirtualMouse()
    }

    internal fun deathRecipientForTest(): android.os.IBinder.DeathRecipient = clientDeathRecipient
}
