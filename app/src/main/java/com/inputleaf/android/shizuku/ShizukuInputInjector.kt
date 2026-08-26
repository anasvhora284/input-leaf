package com.inputleaf.android.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
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
 * Wrapper for Shizuku-based input injection.
 * Handles binding to the privileged InputInjectorService and translating
 * InputLeap events to Android input events.
 */
class ShizukuInputInjector(
    private val screenWidth: Int,
    private val screenHeight: Int
) : InputInjector {
    override val name: String = "Shizuku (ADB-level injection)"
    
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
    ).daemon(false).processNameSuffix("input_injector")
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Shizuku service connected")
            service = IInputInjector.Stub.asInterface(binder)
            isBound = true
            connectDeferred?.complete(true)
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Shizuku service disconnected")
            service = null
            isBound = false
            connectDeferred?.complete(false)
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
            return true
        }
        
        val deferred = CompletableDeferred<Boolean>()
        connectDeferred = deferred
        
        return try {
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
    }
    
    /**
     * Unbind from the Shizuku service.
     */
    override fun disconnect() {
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
    override fun send(event: InputLeapEvent) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject event", e)
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

