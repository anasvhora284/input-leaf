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

import com.inputleaf.android.inject.InputInjector

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
                    Log.d(TAG, "KeyDown: keysym=0x${event.keyId.toString(16)} (${event.keyId})")
                    val keyCode = com.inputleaf.android.inject.KeyMapUtils.keysymToAndroidKeyCode(event.keyId)
                    Log.d(TAG, "Mapped to Android keyCode: $keyCode")
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        metaState = com.inputleaf.android.inject.KeyMapUtils.updateMetaState(keyCode, true, metaState)
                        svc.injectKeyEvent(KeyEvent.ACTION_DOWN, keyCode, com.inputleaf.android.inject.KeyMapUtils.keycodeToScanCode(keyCode), metaState)
                    }
                }
                
                is InputLeapEvent.KeyUp -> {
                    Log.d(TAG, "KeyUp: keysym=0x${event.keyId.toString(16)} (${event.keyId})")
                    val keyCode = com.inputleaf.android.inject.KeyMapUtils.keysymToAndroidKeyCode(event.keyId)
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        svc.injectKeyEvent(KeyEvent.ACTION_UP, keyCode, com.inputleaf.android.inject.KeyMapUtils.keycodeToScanCode(keyCode), metaState)
                        metaState = com.inputleaf.android.inject.KeyMapUtils.updateMetaState(keyCode, false, metaState)
                    }
                }
                
                is InputLeapEvent.KeyRepeat -> {
                    // For repeat, just send another DOWN event
                    val keyCode = com.inputleaf.android.inject.KeyMapUtils.keysymToAndroidKeyCode(event.keyId)
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        val scanCode = com.inputleaf.android.inject.KeyMapUtils.keycodeToScanCode(keyCode)
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
    
}

