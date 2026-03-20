package com.inputleaf.android.shizuku

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Shizuku UserService that runs with shell (ADB) privileges.
 * This service can call InputManager.injectInputEvent() because the shell user
 * has the INJECT_EVENTS permission.
 * 
 * This class is instantiated by Shizuku in a separate process with elevated privileges.
 */
class InputInjectorService : IInputInjector.Stub() {
    
    private val inputManager: Any?
    private val injectInputEventMethod: java.lang.reflect.Method?
    
    init {
        // Get InputManager instance via reflection
        // InputManager.getInstance() is a hidden API
        val inputManagerClass = Class.forName("android.hardware.input.InputManager")
        val getInstanceMethod = inputManagerClass.getMethod("getInstance")
        inputManager = getInstanceMethod.invoke(null)
        
        // Get the injectInputEvent method
        // Signature: boolean injectInputEvent(InputEvent event, int mode)
        injectInputEventMethod = inputManagerClass.getMethod(
            "injectInputEvent",
            android.view.InputEvent::class.java,
            Int::class.javaPrimitiveType
        )
    }
    
    companion object {
        // Injection mode: async (don't wait for injection to complete)
        private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
        
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
            
            val result = injectInputEventMethod?.invoke(
                inputManager, 
                event, 
                INJECT_INPUT_EVENT_MODE_ASYNC
            ) as? Boolean ?: false
            
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
            
            val result = injectInputEventMethod?.invoke(
                inputManager, 
                event, 
                INJECT_INPUT_EVENT_MODE_ASYNC
            ) as? Boolean ?: false
            
            event.recycle()
            result
        } catch (e: Exception) {
            android.util.Log.e("InputInjectorService", "Failed to inject scroll event", e)
            false
        }
    }
    
    override fun injectKeyEvent(action: Int, keyCode: Int, metaState: Int): Boolean {
        return try {
            val now = SystemClock.uptimeMillis()
            
            val event = KeyEvent(
                now,        // downTime
                now,        // eventTime
                action,     // action (ACTION_DOWN or ACTION_UP)
                keyCode,    // keyCode
                0,          // repeat
                metaState,  // metaState (modifiers)
                KeyEvent.KEYCODE_UNKNOWN, // deviceId
                0,          // scanCode
                KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD
            )
            
            val result = injectInputEventMethod?.invoke(
                inputManager, 
                event, 
                INJECT_INPUT_EVENT_MODE_ASYNC
            ) as? Boolean ?: false
            
            result
        } catch (e: Exception) {
            android.util.Log.e("InputInjectorService", "Failed to inject key event", e)
            false
        }
    }
    
    override fun destroy() {
        // Nothing to clean up
    }
}
