package com.inputleaf.android.inject

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.shizuku.ShizukuInputInjector
import kotlinx.coroutines.delay

private const val TAG = "AccessibilityInputInjector"

class AccessibilityInputInjector(
    private val context: Context,
    private var screenWidth: Int,
    private var screenHeight: Int
) : InputInjector {

    override val name: String = "Accessibility Service (no extra app)"

    override fun updateScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        hidKeyboard.updateScreenSize(width, height)
    }

    override fun updatePointerSpeed(speed: Int) {
        hidKeyboard.updatePointerSpeed(speed)
    }

    private var mouseX = 0f
    private var mouseY = 0f
    private var metaState = 0
    private val scanCodeDecoder = ProtocolScanCodeDecoder()
    private val hidKeyboard = ShizukuInputInjector(screenWidth, screenHeight)

    override fun isAvailable(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains("${context.packageName}/${AccessibilityInputService::class.java.name}")
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun connect(): Boolean {
        if (!isAvailable()) {
            Log.e(TAG, "Accessibility service not enabled in settings")
            return false
        }

        var attempts = 0
        while (!AccessibilityInputService.isServiceRunning() && attempts < 50) {
            delay(100)
            attempts++
        }

        val connected = AccessibilityInputService.isServiceRunning()
        if (connected) {
            Log.d(TAG, "Accessibility service connected successfully")
            if (hidKeyboard.isAvailable()) {
                hidKeyboard.connect()
            }
        } else {
            Log.e(TAG, "Accessibility service connection timeout")
        }
        return connected
    }

    override fun disconnect() {
        Log.d(TAG, "disconnect called")
        hidKeyboard.disconnect()
    }

    override fun setHidKeyboardAttached(attached: Boolean) {
        hidKeyboard.setHidKeyboardAttached(attached)
    }

    override fun setHidMouseAttached(attached: Boolean) {
        hidKeyboard.setHidMouseAttached(attached)
    }

    override fun onHidMouseEnter(x: Int, y: Int) {
        hidKeyboard.onHidMouseEnter(x, y)
    }

    override fun onHidMouseLeave() {
        hidKeyboard.onHidMouseLeave()
    }

    override fun usesNativePointer(): Boolean = hidKeyboard.usesNativePointer()

    override fun nativePointerState() = hidKeyboard.nativePointerState()

    override fun expectsNativePointer() = hidKeyboard.expectsNativePointer()

    override fun setOnNativePointerStateChanged(listener: ((NativePointerState) -> Unit)?) {
        hidKeyboard.setOnNativePointerStateChanged(listener)
    }

    override fun send(event: InputLeapEvent) {
        val svc = AccessibilityInputService.getInstance() ?: return

        try {
            when (event) {
                is InputLeapEvent.MouseMoveAbs -> {
                    mouseX = event.x.toFloat().coerceIn(0f, screenWidth.toFloat())
                    mouseY = event.y.toFloat().coerceIn(0f, screenHeight.toFloat())
                    if (!hidKeyboard.tryHidMouse(event)) {
                        svc.injectTouchMove(mouseX, mouseY)
                    }
                }

                is InputLeapEvent.MouseMoveRel -> {
                    mouseX = (mouseX + event.dx).coerceIn(0f, screenWidth.toFloat())
                    mouseY = (mouseY + event.dy).coerceIn(0f, screenHeight.toFloat())
                    if (!hidKeyboard.tryHidMouse(event)) {
                        svc.injectTouchMove(mouseX, mouseY)
                    }
                }

                is InputLeapEvent.MouseDown -> {
                    if (!hidKeyboard.tryHidMouse(event)) {
                        svc.injectTouchDown(mouseX, mouseY)
                    }
                }

                is InputLeapEvent.MouseUp -> {
                    if (!hidKeyboard.tryHidMouse(event)) {
                        svc.injectTouchUp(mouseX, mouseY)
                    }
                }

                is InputLeapEvent.MouseWheel -> {
                    if (!hidKeyboard.tryHidMouse(event)) {
                        val swipeLength = 300f
                        val startY = mouseY
                        // event.yDelta > 0 means scroll up (swipe down), event.yDelta < 0 means scroll down (swipe up)
                        val endY = (if (event.yDelta > 0) mouseY + swipeLength else mouseY - swipeLength)
                            .coerceIn(0f, screenHeight.toFloat())
                        svc.injectSwipe(mouseX, startY, mouseX, endY, 150)
                    }
                }

                is InputLeapEvent.KeyDown ->
                    handleKeyEvent(event.keyId, event.mask, event.scancode, isDown = true)

                is InputLeapEvent.KeyUp ->
                    handleKeyEvent(event.keyId, event.mask, event.scancode, isDown = false)

                is InputLeapEvent.KeyRepeat ->
                    handleKeyRepeat(event.keyId, event.mask, event.scancode, event.count)

                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send event", e)
        }
    }

    private fun handleKeyEvent(keysym: Int, mask: Int, button: Int, isDown: Boolean) {
        val scancode = scanCodeDecoder.toEvdev(button, keysym)
        if (hidKeyboard.tryHidKey(scancode, isDown)) {
            return
        }
        val ime = InputLeafIME.getInstance()
        if (ime == null) {
            Log.w(TAG, "InputLeafIME not running, dropping key event")
            return
        }
        val shortcutModifiers = KeyMapUtils.hasShortcutModifiers(metaState) ||
            KeyMapUtils.protocolMaskHasShortcuts(mask)
        when (val resolved = KeysymResolver.resolve(
            keysym,
            scancode,
            isDown,
            shortcutModifiers = shortcutModifiers,
        )) {
            is KeysymAction.KeyEventAction -> {
                KeysymInjection.applyKeyEventAction(
                    action = resolved,
                    isDown = isDown,
                    metaState = metaState,
                    onMetaStateChanged = { metaState = it },
                ) { keyEventAction, keyCode, updatedMetaState ->
                    ime.injectKeyEvent(
                        keyEventAction,
                        keyCode,
                        updatedMetaState or KeyMapUtils.androidMetaFromProtocolMask(mask),
                    )
                }
            }
            is KeysymAction.Text -> ime.commitText(resolved.char)
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

    private fun handleKeyRepeat(keysym: Int, mask: Int, button: Int, count: Int) {
        val scancode = scanCodeDecoder.toEvdev(button, keysym)
        if (hidKeyboard.tryHidKey(scancode, isDown = true)) {
            return
        }
        val ime = InputLeafIME.getInstance()
        if (ime == null) {
            Log.w(TAG, "InputLeafIME not running, dropping KeyRepeat")
            return
        }
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
                    ime.injectKeyEvent(
                        KeyEvent.ACTION_DOWN,
                        resolved.keyCode,
                        injectionMeta,
                    )
                }
            }
            is KeysymAction.Text -> repeat(count) { ime.commitText(resolved.char) }
            is KeysymAction.Ignore -> Unit
        }
    }
}
