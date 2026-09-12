package com.inputleaf.android.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.inputleaf.android.inject.InputInjector
import com.inputleaf.android.inject.InputLeafIME
import com.inputleaf.android.inject.KeyMapUtils
import com.inputleaf.android.inject.KeysymAction
import com.inputleaf.android.inject.KeysymInjection
import com.inputleaf.android.inject.KeysymResolver
import com.inputleaf.android.inject.ProtocolScanCodeDecoder
import com.inputleaf.android.model.InputLeapEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuInputInjector"

/**
 * Shizuku caches the user-service process by version. Bump this whenever
 * [InputInjectorService] changes, or the old process is reused and the new AIDL methods
 * are missing at runtime.
 */
private const val SERVICE_VERSION = 12

/**
 * Wrapper for Shizuku-based input injection.
 * Handles binding to the privileged InputInjectorService and translating
 * InputLeap events to Android input events.
 */
class ShizukuInputInjector(
    private var screenWidth: Int,
    private var screenHeight: Int
) : InputInjector {

    /**
     * Updates the display bounds on rotation. Exists so a single injector can be reused
     * across connects: constructing a new one per connect made two instances share one
     * Shizuku user service, and tearing down the old one closed the HID devices the new
     * one was already using.
     */
    fun updateScreenBounds(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }
    override val name: String
        get() = if (usesSystemPointer) {
            "Shizuku (system pointer + ADB keys)"
        } else {
            "Shizuku (ADB-level injection)"
        }

    /**
     * True once a real HID pointer is registered, so Android draws its own cursor —
     * which, unlike an app overlay, renders above the notification shade and Quick
     * Settings. When false everything falls back to `injectInputEvent` exactly as before.
     */
    var usesSystemPointer: Boolean = false
        private set

    var onServiceDisconnectedCallback: (() -> Unit)? = null

    private var service: IInputInjector? = null
    private var isBound = false
    private var connectDeferred: CompletableDeferred<Boolean>? = null

    // Track absolute mouse position (InputLeap sends absolute coords,
    // but we may need to synthesize relative movements)
    private var mouseX = 0f
    private var mouseY = 0f
    
    // Track button state for proper motion event sequencing
    private var buttonState = 0
    
    // Modifier key state (for meta state in key events)
    private var metaState = 0
    private val scanCodeDecoder = ProtocolScanCodeDecoder()
    
    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            "com.inputleaf.android",
            InputInjectorService::class.java.name
        )
        // Bumped when the user-service code changes, or Shizuku reuses the stale process.
    ).daemon(false).processNameSuffix("input_injector").version(SERVICE_VERSION)
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Shizuku service connected")
            service = IInputInjector.Stub.asInterface(binder)
            isBound = true
            connectDeferred?.complete(true)
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Shizuku service disconnected")
            notifyDisconnected()
            connectDeferred?.complete(false)
        }
    }

    private fun notifyDisconnected() {
        val wasActive = isBound || service != null
        service = null
        isBound = false
        // The HID device dies with the service process; recovery re-opens it.
        usesSystemPointer = false
        if (wasActive) {
            onServiceDisconnectedCallback?.invoke()
        }
    }
    
    /**
     * Check if Shizuku is available and we have permission.
     */
    override fun isAvailable(): Boolean {
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
    override suspend fun connect(): Boolean {
        if (!isAvailable()) {
            Log.e(TAG, "Shizuku not available or permission not granted")
            return false
        }
        if (isBound && service != null) {
            // A live binding still needs the HID pointer checked: a previous session may
            // have closed it, or the user service may have been restarted underneath us.
            // Returning early without this left usesSystemPointer stale-true while no
            // device existed, which suppressed the drawn overlay and showed no cursor.
            openSystemPointer()
            return true
        }
        
        val deferred = CompletableDeferred<Boolean>()
        connectDeferred = deferred
        
        val bound = try {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
            withTimeout(5000) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Shizuku service bind timeout")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind Shizuku service", e)
            false
        } finally {
            connectDeferred = null
        }

        if (bound) openSystemPointer()
        return bound
    }

    /**
     * Registers the HID pointer, degrading silently to `injectInputEvent` if `/dev/uhid`
     * is unavailable — an SELinux denial on a hardened ROM, say. Binding still counts as
     * success in that case, so a failure here never makes a device worse than before.
     */
    private fun openSystemPointer() {
        val svc = service ?: return
        usesSystemPointer = try {
            svc.openVirtualPointer()
        } catch (e: Exception) {
            Log.w(TAG, "HID pointer unavailable; using injected motion events", e)
            false
        }
        if (usesSystemPointer) {
            Log.i(TAG, "HID pointer active — Android draws the cursor")
        }
    }
    
    /**
     * Unbind from the Shizuku service.
     */
    override fun disconnect() {
        if (isBound || service != null) {
            try {
                service?.destroy()
                Shizuku.unbindUserService(serviceArgs, serviceConnection, true)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding Shizuku service", e)
            }
            service = null
            isBound = false
            usesSystemPointer = false
        }
    }

    /**
     * Shows or hides the on-screen keyboard while the HID keyboard is attached, so the
     * user can reach their IME's emoji and GIF pickers mid-session.
     * @return the new visibility state, or false if the service is gone
     */
    fun toggleSoftKeyboard(): Boolean = try {
        service?.toggleSoftKeyboard() ?: false
    } catch (e: Exception) {
        Log.w(TAG, "Failed to toggle soft keyboard", e)
        false
    }

    private fun motionAction(): Int =
        if (buttonState != 0) MotionEvent.ACTION_MOVE else MotionEvent.ACTION_HOVER_MOVE
    
    /**
     * Send an InputLeap event to be injected.
     */
    override fun send(event: InputLeapEvent) {
        val svc = service ?: return
        
        try {
            when (event) {
                // The server handing control over or taking it away is the only safe
                // moment to re-establish where the pointer is. Resyncing on distance
                // travelled instead is what made the old prototype jump to a corner.
                is InputLeapEvent.Enter -> {
                    // Seed the absolute base from the border crossing, otherwise relative
                    // moves accumulate from the previous session's position and the
                    // cursor appears mid-screen instead of at the edge.
                    mouseX = event.x.toFloat().coerceIn(0f, screenWidth.toFloat())
                    mouseY = event.y.toFloat().coerceIn(0f, screenHeight.toFloat())
                    if (usesSystemPointer) {
                        svc.resyncPointer()
                        svc.movePointerAbsolute(
                            mouseX.toInt(), mouseY.toInt(), screenWidth, screenHeight,
                        )
                    }
                }

                is InputLeapEvent.Leave -> {
                    if (usesSystemPointer) svc.resyncPointer()
                }

                is InputLeapEvent.MouseMoveAbs -> {
                    mouseX = event.x.toFloat().coerceIn(0f, screenWidth.toFloat())
                    mouseY = event.y.toFloat().coerceIn(0f, screenHeight.toFloat())
                    if (usesSystemPointer) {
                        svc.movePointerAbsolute(
                            mouseX.toInt(), mouseY.toInt(), screenWidth, screenHeight,
                        )
                    } else {
                        svc.injectMotionEvent(motionAction(), mouseX, mouseY, buttonState)
                    }
                }

                is InputLeapEvent.MouseMoveRel -> {
                    mouseX = (mouseX + event.dx).coerceIn(0f, screenWidth.toFloat())
                    mouseY = (mouseY + event.dy).coerceIn(0f, screenHeight.toFloat())
                    if (usesSystemPointer) {
                        // Accumulated into an absolute position, then sent absolutely —
                        // the HID device never sees a delta, so nothing can drift.
                        svc.movePointerAbsolute(
                            mouseX.toInt(), mouseY.toInt(), screenWidth, screenHeight,
                        )
                    } else {
                        svc.injectMotionEvent(motionAction(), mouseX, mouseY, buttonState)
                    }
                }

                is InputLeapEvent.MouseDown -> {
                    buttonState = buttonState or inputLeapButtonToAndroid(event.buttonId)
                    if (usesSystemPointer) {
                        svc.pointerButton(event.buttonId, true)
                    } else {
                        svc.injectMotionEvent(MotionEvent.ACTION_DOWN, mouseX, mouseY, buttonState)
                    }
                }

                is InputLeapEvent.MouseUp -> {
                    buttonState = buttonState and inputLeapButtonToAndroid(event.buttonId).inv()
                    if (usesSystemPointer) {
                        svc.pointerButton(event.buttonId, false)
                    } else {
                        svc.injectMotionEvent(MotionEvent.ACTION_UP, mouseX, mouseY, buttonState)
                    }
                }

                is InputLeapEvent.MouseWheel -> {
                    if (usesSystemPointer) {
                        svc.pointerWheel(event.xDelta, event.yDelta)
                    } else {
                        // InputLeap sends 120 units per notch, Android expects -1 to 1
                        val vScroll = event.yDelta / 120f
                        val hScroll = event.xDelta / 120f
                        svc.injectScrollEvent(mouseX, mouseY, hScroll, vScroll)
                    }
                }
                
                is InputLeapEvent.KeyDown -> {
                    Log.d(
                        TAG,
                        "KeyDown: keysym=0x${event.keyId.toString(16)} mask=${event.mask} " +
                            "button=${event.scancode}",
                    )
                    handleKeyEvent(svc, event.keyId, event.mask, event.scancode, isDown = true)
                }
                
                is InputLeapEvent.KeyUp -> {
                    Log.d(
                        TAG,
                        "KeyUp: keysym=0x${event.keyId.toString(16)} mask=${event.mask} " +
                            "button=${event.scancode}",
                    )
                    handleKeyEvent(svc, event.keyId, event.mask, event.scancode, isDown = false)
                }
                
                is InputLeapEvent.KeyRepeat -> {
                    handleKeyRepeat(svc, event.keyId, event.mask, event.scancode, event.count)
                }
                
                else -> {
                    // Ignore non-input events
                }
            }
        } catch (e: DeadObjectException) {
            Log.w(TAG, "Shizuku service binder is dead", e)
            notifyDisconnected()
        } catch (e: Exception) {
            if (e is RemoteException || e.cause is DeadObjectException || e.cause is RemoteException) {
                Log.w(TAG, "Shizuku service remote exception / dead binder", e)
                notifyDisconnected()
            } else {
                Log.e(TAG, "Failed to inject event", e)
            }
        }
    }
    
    private fun handleKeyEvent(
        svc: IInputInjector,
        keysym: Int,
        mask: Int,
        button: Int,
        isDown: Boolean,
    ) {
        val scancode = scanCodeDecoder.toEvdev(button, keysym)

        // Prefer the real HID keyboard: Android then treats the key as hardware input,
        // which keeps the user's own IME selected and its emoji/GIF pickers reachable.
        //
        // A HID keyboard sends key POSITIONS, not characters, and Android resolves them
        // through the physical-keyboard layout the user picked for this device. That is
        // the whole point rather than a limitation: Gboard supplies physical layouts for
        // its enabled languages, so Ctrl+Space on the phone switches script (the circular
        // EN / ka badge) and these same scancodes then arrive as Gujarati. Injecting
        // Unicode here instead would bypass the layout and defeat that.
        //
        // `scancode != 0` is the discriminator. A key with no physical position -- a
        // compose result, a character the server synthesised -- has no scancode, falls
        // through to KeysymResolver below, and is delivered as text.
        if (scancode != 0 && svc.injectHidKey(scancode, isDown)) {
            return
        }

        val shortcutModifiers = KeyMapUtils.hasShortcutModifiers(metaState) ||
            KeyMapUtils.protocolMaskHasShortcuts(mask)
        val injectionMeta = metaState or KeyMapUtils.androidMetaFromProtocolMask(mask)
        when (val resolved = KeysymResolver.resolve(
            keysym,
            scancode,
            isDown,
            shortcutModifiers = shortcutModifiers,
        )) {
            is KeysymAction.KeyEventAction -> {
                Log.d(TAG, "Mapped to Android keyCode: ${resolved.keyCode} evdev=$scancode")
                KeysymInjection.applyKeyEventAction(
                    action = resolved,
                    isDown = isDown,
                    metaState = metaState,
                    onMetaStateChanged = { metaState = it },
                ) { keyEventAction, keyCode, updatedMetaState ->
                    svc.injectKeyEvent(
                        keyEventAction,
                        keyCode,
                        resolved.scanCode,
                        updatedMetaState or KeyMapUtils.androidMetaFromProtocolMask(mask),
                    )
                }
            }
            is KeysymAction.Text -> {
                if (!injectTextOrLog(svc, resolved.char, keysym)) {
                    injectPhysicalFallback(svc, scancode, isDown, injectionMeta)
                }
            }
            is KeysymAction.Ignore -> {
                if (isDown) {
                    Log.w(
                        TAG,
                        "Ignoring key id=0x${keysym.toString(16)} button=$button evdev=$scancode",
                    )
                }
            }
        }
    }

    private fun handleKeyRepeat(
        svc: IInputInjector,
        keysym: Int,
        mask: Int,
        button: Int,
        count: Int,
    ) {
        val scancode = scanCodeDecoder.toEvdev(button, keysym)
        val shortcutModifiers = KeyMapUtils.hasShortcutModifiers(metaState) ||
            KeyMapUtils.protocolMaskHasShortcuts(mask)
        val injectionMeta = metaState or KeyMapUtils.androidMetaFromProtocolMask(mask)
        when (val resolved = KeysymResolver.resolve(
            keysym,
            scancode,
            isDown = true,
            shortcutModifiers = shortcutModifiers,
        )) {
            is KeysymAction.KeyEventAction -> {
                repeat(count) {
                    svc.injectKeyEvent(
                        KeyEvent.ACTION_DOWN,
                        resolved.keyCode,
                        resolved.scanCode,
                        injectionMeta,
                    )
                }
            }
            is KeysymAction.Text -> {
                var injected = true
                repeat(count) {
                    if (!injectTextOrLog(svc, resolved.char, keysym)) injected = false
                }
                if (!injected) {
                    injectPhysicalFallback(svc, scancode, isDown = true, injectionMeta)
                }
            }
            is KeysymAction.Ignore -> Unit
        }
    }

    private fun injectTextOrLog(svc: IInputInjector, char: String, keysym: Int): Boolean {
        if (svc.injectText(char)) return true
        val ime = InputLeafIME.getInstance()
        if (ime != null) {
            Log.w(TAG, "Shizuku injectText failed for '$char'; falling back to IME commitText")
            ime.commitText(char)
            return true
        }
        Log.e(TAG, "injectText failed for char='$char' keysym=0x${keysym.toString(16)} ($keysym)")
        return false
    }

    private fun injectPhysicalFallback(
        svc: IInputInjector,
        scancode: Int,
        isDown: Boolean,
        metaState: Int,
    ) {
        val keyCode = KeyMapUtils.scancodeToAndroidKeyCode(scancode)
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return
        Log.w(TAG, "Falling back to physical keyCode=$keyCode evdev=$scancode")
        val action = if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        svc.injectKeyEvent(action, keyCode, scancode, metaState)
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
    
}

