package com.inputleaf.android.inject

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.inputleaf.android.model.InputLeapEvent
import kotlinx.coroutines.delay

private const val TAG = "AccessibilityInputInjector"
private const val RECLAIM_POLLS = 30
private const val RECLAIM_POLL_MS = 50L

/**
 * Accessibility mode is entirely virtual: gestures and the overlay cursor for the mouse,
 * the Input Leaf keyboard (IME) for keys. It never starts a Shizuku or root process, so
 * switching to it cannot leave HID devices or injector processes behind.
 */
class AccessibilityInputInjector(
    private val context: Context,
    private var screenWidth: Int,
    private var screenHeight: Int,
) : InputInjector {

    override val name: String = "Accessibility Service (no extra app)"

    override fun updateScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    override fun setScrollSpeed(percent: Int) {
        scrollSpeed = ScrollSpeed.multiplier(percent)
        wheelNotches.speed = scrollSpeed
        hWheelNotches.speed = scrollSpeed
    }

    private var mouseX = 0f
    private var mouseY = 0f
    private var metaState = 0
    private val scanCodeDecoder = ProtocolScanCodeDecoder()
    private val wheelNotches = com.inputleaf.android.shizuku.uhid.WheelNotchAccumulator()
    private val hWheelNotches = com.inputleaf.android.shizuku.uhid.WheelNotchAccumulator()
    @Volatile private var scrollSpeed = 1.0
    private val supportsTouchGestures = runCatching {
        context.packageManager.run {
            hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) ||
                hasSystemFeature(PackageManager.FEATURE_FAKETOUCH)
        }
    }.getOrDefault(true)

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
        } else {
            Log.e(TAG, "Accessibility service connection timeout")
        }
        return connected
    }

    override fun disconnect() {
        scanCodeDecoder.clearPressedKeys()
        Log.d(TAG, "disconnect called")
        ImeSwitcher.restore(context)
    }

    /** The "keyboard" here is the Input Leaf IME: selected on Enter, handed back on Leave. */
    override fun setHidKeyboardAttached(attached: Boolean) {
        if (attached) {
            ImeSwitcher.useOurs(context)
        } else {
            scanCodeDecoder.clearPressedKeys()
            ImeSwitcher.restore(context, leaving = true)
        }
    }

    override fun send(event: InputLeapEvent) {
        val svc = AccessibilityInputService.getInstance() ?: return

        try {
            when (event) {
                is InputLeapEvent.MouseMoveAbs -> {
                    mouseX = event.x.toFloat().coerceIn(0f, screenWidth.toFloat())
                    mouseY = event.y.toFloat().coerceIn(0f, screenHeight.toFloat())
                    svc.injectTouchMove(mouseX, mouseY)
                }

                is InputLeapEvent.MouseMoveRel -> {
                    mouseX = (mouseX + event.dx).coerceIn(0f, screenWidth.toFloat())
                    mouseY = (mouseY + event.dy).coerceIn(0f, screenHeight.toFloat())
                    svc.injectTouchMove(mouseX, mouseY)
                }

                is InputLeapEvent.MouseDown ->
                    when (AccessibilityMousePolicy.onButtonDown(event.buttonId, supportsTouchGestures)) {
                        AccessibilityMouseAction.TOUCH_DOWN -> svc.injectTouchDown(mouseX, mouseY)
                        else -> Unit
                    }

                is InputLeapEvent.MouseUp ->
                    when (AccessibilityMousePolicy.onButtonUp(event.buttonId, supportsTouchGestures)) {
                        AccessibilityMouseAction.TOUCH_UP -> svc.injectTouchUp(mouseX, mouseY)
                        AccessibilityMouseAction.CLICK_NODE -> svc.clickNodeAt(mouseX, mouseY)
                        AccessibilityMouseAction.GLOBAL_BACK -> svc.performGlobalBack()
                        AccessibilityMouseAction.GLOBAL_HOME -> svc.performGlobalHome()
                        else -> Unit
                    }

                is InputLeapEvent.MouseWheel -> {
                    if (!supportsTouchGestures) {
                        // Swipes are touch gestures, which non-touch devices reject (#65).
                        // Input Leap's positive y scrolls up; scrollNodeAt's positive is down.
                        val down = -wheelNotches.accept(event.yDelta)
                        val right = hWheelNotches.accept(event.xDelta)
                        if (down != 0) svc.scrollNodeAt(mouseX, mouseY, down, horizontal = false)
                        if (right != 0) svc.scrollNodeAt(mouseX, mouseY, right, horizontal = true)
                    } else if (event.yDelta != 0) {
                        val swipeLength = (300.0 * scrollSpeed).toFloat()
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

    /**
     * The Input Leaf keyboard, taking it back first if the pill lent the user theirs. The
     * key that triggers that waits for the switch (a few hundred ms) instead of being lost.
     */
    private fun keyboard(): InputLeafIME? {
        InputLeafIME.getInstance()?.let { return it }
        if (!ImeSwitcher.reclaim(context)) return null
        repeat(RECLAIM_POLLS) {
            Thread.sleep(RECLAIM_POLL_MS)
            InputLeafIME.getInstance()?.takeIf { it.currentInputConnection != null }?.let { return it }
        }
        return null
    }

    private fun handleKeyEvent(keysym: Int, mask: Int, button: Int, isDown: Boolean) {
        val scancode = scanCodeDecoder.decodeKeyEvent(button, keysym, isDown)
        if (AccessibilityKeyPolicy.isBackKey(keysym, mask)) {
            if (isDown) AccessibilityInputService.getInstance()?.performGlobalBack()
            return
        }
        val ime = keyboard()
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
        val scancode = scanCodeDecoder.decodeKeyEvent(button, keysym, isDown = true)
        // A held Esc must not fire Back again for every repeat.
        if (AccessibilityKeyPolicy.isBackKey(keysym, mask)) return
        val ime = keyboard()
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
