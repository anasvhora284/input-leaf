package com.inputleaf.android.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.inputleaf.android.inject.InputInjector
import com.inputleaf.android.inject.NativePointerState
import com.inputleaf.android.inject.InputLeafIME
import com.inputleaf.android.inject.KeyMapUtils
import com.inputleaf.android.inject.KeysymAction
import com.inputleaf.android.inject.KeysymInjection
import com.inputleaf.android.inject.KeysymResolver
import com.inputleaf.android.inject.ProtocolScanCodeDecoder
import com.inputleaf.android.model.InputLeapEvent
import com.inputleaf.android.privilege.PrivilegeKind
import com.inputleaf.android.privilege.PrivilegedUserServiceHost
import com.inputleaf.android.privilege.ShizukuUserServiceHost
import com.inputleaf.android.shizuku.uhid.HidMouseState
import com.inputleaf.android.shizuku.uhid.MouseEdgeAnchor
import com.inputleaf.android.shizuku.uhid.WheelNotchAccumulator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

private const val TAG = "ShizukuInputInjector"

class ShizukuInputInjector internal constructor(
    screenWidth: Int,
    screenHeight: Int,
    private val host: PrivilegedUserServiceHost,
) : InputInjector {
    constructor(screenWidth: Int, screenHeight: Int) : this(
        screenWidth,
        screenHeight,
        ShizukuUserServiceHost(),
    )

    override val name: String get() = host.displayName

    override fun privilegeKind(): PrivilegeKind = host.kind

    private var screenWidth = screenWidth
    private var screenHeight = screenHeight
    private val hidMouse = HidMouseState(pointerMaxX(), pointerMaxY())

    var onServiceDisconnectedCallback: (() -> Unit)? = null

    /** Owned by this process, so the injector's death watch fires exactly when we die. */
    private val clientToken = android.os.Binder()

    private var service: IInputInjector? = null
    private var isBound = false
    private var connectDeferred: CompletableDeferred<Boolean>? = null

    private var mouseX = 0f
    private var mouseY = 0f
    private var buttonState = 0

    private val wheelNotches = WheelNotchAccumulator()
    @Volatile private var pointerSpeed = 0
    /** True until the first successful attach after [setHidMouseAttached(false)] or disconnect. */
    private var clientClosedMouse = true
    @Volatile private var currentNativePointerState: NativePointerState = NativePointerState.NONE
    @Volatile private var nativePointerListener: ((NativePointerState) -> Unit)? = null

    private var metaState = 0
    private val scanCodeDecoder = ProtocolScanCodeDecoder()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "${host.kind} service connected")
            val injector = IInputInjector.Stub.asInterface(binder)
            // Best-effort: an injector that cannot watch us still works, it just falls
            // back to process reaping for UHID cleanup.
            runCatching { injector.attachClient(clientToken) }
                .onFailure { Log.w(TAG, "Could not register client death watch", it) }
            service = injector
            isBound = true
            connectDeferred?.complete(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "${host.kind} service disconnected")
            notifyDisconnected()
            connectDeferred?.complete(false)
        }
    }

    private fun pointerMaxX(): Int = (screenWidth - 1).coerceAtLeast(0)
    private fun pointerMaxY(): Int = (screenHeight - 1).coerceAtLeast(0)

    override fun updateScreenSize(width: Int, height: Int) {
        if (width == screenWidth && height == screenHeight) return
        Log.i(TAG, "Screen size updated ${screenWidth}x$screenHeight -> ${width}x$height")
        screenWidth = width
        screenHeight = height
        hidMouse.resizeDisplay(pointerMaxX(), pointerMaxY())
    }

    override fun updatePointerSpeed(speed: Int) {
        val clamped = speed.coerceIn(-7, 7)
        if (clamped == pointerSpeed) return
        pointerSpeed = clamped
    }

    private fun notifyDisconnected() {
        val wasActive = isBound || service != null
        service = null
        isBound = false
        hidMouse.resetOnDisconnect()
        wheelNotches.reset()
        clientClosedMouse = true
        publishNativePointerState(NativePointerState.NONE)
        if (wasActive) {
            onServiceDisconnectedCallback?.invoke()
        }
    }

    private fun publishNativePointerState(state: NativePointerState) {
        currentNativePointerState = state
        nativePointerListener?.invoke(state)
    }

    override fun isAvailable(): Boolean = host.isAvailable()

    override suspend fun connect(): Boolean {
        if (!isAvailable()) {
            Log.e(TAG, "${host.kind} not available or permission not granted")
            return false
        }
        if (isBound && service != null) {
            return true
        }

        val attempts = host.bindAttempts.coerceAtLeast(1)
        repeat(attempts) { attempt ->
            if (bindOnce()) return true
            Log.w(TAG, "${host.kind} bind attempt ${attempt + 1}/$attempts failed")
            runCatching { host.unbind(serviceConnection, destroy = false) }
            isBound = false
            service = null
            delay(400)
            if (!isAvailable()) {
                runCatching { host.unbind(serviceConnection, destroy = true) }
                return false
            }
        }
        runCatching { host.unbind(serviceConnection, destroy = true) }
        return false
    }

    private suspend fun bindOnce(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        connectDeferred = deferred
        return try {
            if (!host.bind(serviceConnection)) {
                Log.e(TAG, "${host.kind} bind could not be started")
                return false
            }
            withTimeout(host.bindTimeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "${host.kind} service bind timeout")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind ${host.kind} service", e)
            false
        } finally {
            connectDeferred = null
        }
    }

    override fun disconnect() {
        if (isBound || service != null) {
            try {
                runCatching { service?.releaseHidKeys() }
                runCatching { service?.closeVirtualKeyboard() }
                runCatching { service?.closeVirtualMouse() }
                hidMouse.detach()
                service?.destroy()
                host.unbind(serviceConnection, destroy = true)
            } catch (e: Exception) {
                Log.w(TAG, "Error unbinding ${host.kind} service", e)
            }
            service = null
            isBound = false
            hidMouse.resetOnDisconnect()
        wheelNotches.reset()
            clientClosedMouse = true
            publishNativePointerState(NativePointerState.NONE)
        }
    }

    override fun setHidKeyboardAttached(attached: Boolean) {
        if (service == null) {
            Log.w(TAG, "HID keyboard attach=$attached skipped; ${host.kind} service not bound")
            return
        }
        remote("Failed to update HID keyboard attachment", Unit) { svc ->
            if (attached) {
                if (svc.openVirtualKeyboard()) {
                    Log.i(TAG, "HID keyboard attached")
                } else {
                    Log.w(TAG, "HID keyboard unavailable; keys use injectKeyEvent")
                }
            } else {
                svc.releaseHidKeys()
                svc.closeVirtualKeyboard()
            }
        }
    }

    override fun setHidMouseAttached(attached: Boolean) {
        val svc = service
        if (svc == null) {
            if (attached) {
                publishNativePointerState(NativePointerState.FALLBACK)
            }
            Log.w(TAG, "HID mouse attach=$attached skipped; ${host.kind} service not bound")
            return
        }
        if (!attached) {
            remote("Failed to detach HID mouse", Unit) { s ->
                runCatching { s.onHidMouseLeave() }
                s.closeVirtualMouse()
                hidMouse.detach()
                wheelNotches.reset()
                clientClosedMouse = true
                publishNativePointerState(NativePointerState.NONE)
                Log.i(TAG, "HID mouse detached")
            }
            return
        }

        if (hidMouse.attached) {
            publishNativePointerState(NativePointerState.ACTIVE)
            remote("Failed to snap HID mouse", Unit) { s ->
                finishPendingSnap(s, sendHid = true)
            }
            return
        }
        if (hidMouse.phase == HidMouseState.AttachPhase.ATTACHING) {
            return
        }

        val newDevice = clientClosedMouse
        hidMouse.beginAttach()
        publishNativePointerState(NativePointerState.PENDING)
        try {
            if (svc.openVirtualMouse()) {
                hidMouse.completeAttach(newDevice = newDevice)
                clientClosedMouse = false
                publishNativePointerState(NativePointerState.ACTIVE)
                // Only the privileged process knows whether this call created the device
                // and emitted the Enter warp. A warm root daemon returns through the
                // idempotent branch without warping, and guessing from app state strands
                // the cursor.
                val daemonWarped = runCatching { svc.consumeEnterWarpApplied() }.getOrDefault(false)
                Log.i(TAG, "HID mouse attached (newDevice=$newDevice daemonWarped=$daemonWarped)")
                finishPendingSnap(svc, sendHid = !daemonWarped)
            } else {
                hidMouse.markUnusable()
                hidMouse.detach()
                publishNativePointerState(NativePointerState.FALLBACK)
                Log.w(TAG, "HID mouse unavailable; pointer uses injectMotionEvent")
            }
        } catch (e: Exception) {
            hidMouse.detach()
            if (service != null) publishNativePointerState(NativePointerState.FALLBACK)
            handleRemoteException(e, "Failed to attach HID mouse")
        }
    }

    override fun usesNativePointer(): Boolean =
        isBound && hidMouse.attached && hidMouse.usable

    override fun nativePointerState(): NativePointerState = currentNativePointerState

    override fun expectsNativePointer(): Boolean = isBound

    override fun setOnNativePointerStateChanged(listener: ((NativePointerState) -> Unit)?) {
        nativePointerListener = listener
        listener?.invoke(currentNativePointerState)
    }

    override fun onHidMouseEnter(x: Int, y: Int) {
        hidMouse.onEnter(x, y)
        Log.i(
            TAG,
            "HID mouse enter $x,$y phase=${hidMouse.phase} cooked=${hidMouse.cookedX},${hidMouse.cookedY}",
        )
        // Hand the coords to the injector so a CREATE2 can warp there itself. Warping
        // from here loses the race against AOSP seeding the new pointer at centre.
        remote("Failed to store HID mouse enter on injector", Unit) {
            it.onHidMouseEnter(x, y, pointerMaxX(), pointerMaxY(), pointerSpeed)
        }
        if (hidMouse.attached) {
            remote("Failed to snap HID mouse", Unit) { s ->
                finishPendingSnap(s, sendHid = true)
            }
        }
    }

    override fun onHidMouseLeave() {
        hidMouse.markLeave()
        remote("Failed to clear HID mouse enter on injector", Unit) { it.onHidMouseLeave() }
    }

    /**
     * @param sendHid false when the injector already emitted this warp itself, so the
     * cooked model advances without replaying the reports and doubling the movement.
     */
    private fun finishPendingSnap(svc: IInputInjector, sendHid: Boolean = true) {
        val target = hidMouse.peekPendingSnap() ?: return
        hidMouse.updatePointerTarget(target.first, target.second)
        Log.i(
            TAG,
            "HID mouse snap to ${target.first},${target.second} from ${hidMouse.cookedX},${hidMouse.cookedY} " +
                "centerSeed=${hidMouse.needsCenterSeed()} sendHid=$sendHid",
        )
        val now = SystemClock.uptimeMillis()
        val plans = MouseEdgeAnchor.planSnap(hidMouse.plannerInput(now), pointerSpeed)
        for (plan in plans) {
            if (plan.isNoOp) continue
            if (sendHid) {
                val sent = svc.injectHidMouse(plan.hidX, plan.hidY, hidMouse.buttons(), 0)
                if (!sent) return
            }
            hidMouse.applySuccessfulPlan(plan, now)
        }
        val (errX, errY) = hidMouse.errorToTarget(target.first, target.second)
        val stillEdge = MouseEdgeAnchor.needsEdgePulse(
            hidMouse.edgeFlags,
            target.first,
            target.second,
            hidMouse.maxXExclusive(),
            hidMouse.maxYExclusive(),
        )
        if (errX == 0 && errY == 0 && !stillEdge) {
            hidMouse.consumePendingSnap()
        }
    }

    fun tryHidKey(evdevCode: Int, isDown: Boolean): Boolean {
        if (evdevCode == 0) return false
        return remote(null, false) { it.injectHidKey(evdevCode, isDown) }
    }

    fun tryHidMouse(event: InputLeapEvent): Boolean {
        if (hidMouse.phase == HidMouseState.AttachPhase.ATTACHING) {
            return when (event) {
                is InputLeapEvent.MouseMoveAbs ->
                    hidMouse.queueTargetWhileAttaching(event.x, event.y)
                is InputLeapEvent.MouseMoveRel -> {
                    val tx = hidMouse.clampX(hidMouse.protocolX + event.dx)
                    val ty = hidMouse.clampY(hidMouse.protocolY + event.dy)
                    hidMouse.queueTargetWhileAttaching(tx, ty)
                }
                else -> true
            }
        }
        if (!hidMouse.attached) return false
        val svc = service ?: return false
        return try {
            when (event) {
                is InputLeapEvent.MouseMoveAbs ->
                    moveHidToProtocolTarget(
                        hidMouse.clampX(event.x),
                        hidMouse.clampY(event.y),
                        svc,
                    )
                is InputLeapEvent.MouseMoveRel -> {
                    val tx = hidMouse.clampX(hidMouse.protocolX + event.dx)
                    val ty = hidMouse.clampY(hidMouse.protocolY + event.dy)
                    moveHidToProtocolTarget(tx, ty, svc)
                }
                is InputLeapEvent.MouseDown -> {
                    val buttons = hidMouse.buttons() or inputLeapButtonToHid(event.buttonId)
                    hidMouse.setButtons(buttons)
                    svc.injectHidMouse(0, 0, buttons, 0)
                }
                is InputLeapEvent.MouseUp -> {
                    val buttons = hidMouse.buttons() and inputLeapButtonToHid(event.buttonId).inv()
                    hidMouse.setButtons(buttons)
                    svc.injectHidMouse(0, 0, buttons, 0)
                }
                is InputLeapEvent.MouseWheel -> {
                    val notches = wheelNotches.accept(event.yDelta)
                    // Handled either way: a partial notch is banked, not passed to the
                    // fallback path, which would scroll it a second time.
                    if (notches == 0) true else svc.injectHidMouse(0, 0, hidMouse.buttons(), notches)
                }
                else -> false
            }
        } catch (e: Exception) {
            handleRemoteException(e, null)
            false
        }
    }

    private fun moveHidToProtocolTarget(targetX: Int, targetY: Int, svc: IInputInjector): Boolean {
        hidMouse.updatePointerTarget(targetX, targetY)
        val now = SystemClock.uptimeMillis()
        val plan = MouseEdgeAnchor.plan(hidMouse.plannerInput(now), pointerSpeed)
        if (plan.isNoOp) return true
        val sent = svc.injectHidMouse(plan.hidX, plan.hidY, hidMouse.buttons(), 0)
        if (sent) {
            hidMouse.applySuccessfulPlan(plan, now)
        }
        return sent
    }

    override fun send(event: InputLeapEvent) {
        val svc = service ?: return

        try {
            when (event) {
                is InputLeapEvent.MouseMoveAbs -> {
                    mouseX = event.x.toFloat().coerceIn(0f, screenWidth.toFloat())
                    mouseY = event.y.toFloat().coerceIn(0f, screenHeight.toFloat())
                    if (!tryHidMouse(event)) {
                        val action = if (buttonState != 0) {
                            MotionEvent.ACTION_MOVE
                        } else {
                            MotionEvent.ACTION_HOVER_MOVE
                        }
                        svc.injectMotionEvent(action, mouseX, mouseY, buttonState)
                    }
                }

                is InputLeapEvent.MouseMoveRel -> {
                    mouseX = (mouseX + event.dx).coerceIn(0f, screenWidth.toFloat())
                    mouseY = (mouseY + event.dy).coerceIn(0f, screenHeight.toFloat())
                    if (!tryHidMouse(event)) {
                        val action = if (buttonState != 0) {
                            MotionEvent.ACTION_MOVE
                        } else {
                            MotionEvent.ACTION_HOVER_MOVE
                        }
                        svc.injectMotionEvent(action, mouseX, mouseY, buttonState)
                    }
                }

                is InputLeapEvent.MouseDown -> {
                    if (!tryHidMouse(event)) {
                        val button = inputLeapButtonToAndroid(event.buttonId)
                        buttonState = buttonState or button
                        svc.injectMotionEvent(MotionEvent.ACTION_DOWN, mouseX, mouseY, buttonState)
                    }
                }

                is InputLeapEvent.MouseUp -> {
                    if (!tryHidMouse(event)) {
                        val button = inputLeapButtonToAndroid(event.buttonId)
                        buttonState = buttonState and button.inv()
                        svc.injectMotionEvent(MotionEvent.ACTION_UP, mouseX, mouseY, buttonState)
                    }
                }

                is InputLeapEvent.MouseWheel -> {
                    if (!tryHidMouse(event)) {
                        val vScroll = event.yDelta / 120f
                        val hScroll = event.xDelta / 120f
                        svc.injectScrollEvent(mouseX, mouseY, hScroll, vScroll)
                    }
                }

                is InputLeapEvent.KeyDown -> {
                    handleKeyEvent(svc, event.keyId, event.mask, event.scancode, isDown = true)
                }

                is InputLeapEvent.KeyUp -> {
                    handleKeyEvent(svc, event.keyId, event.mask, event.scancode, isDown = false)
                }

                is InputLeapEvent.KeyRepeat -> {
                    handleKeyRepeat(svc, event.keyId, event.mask, event.scancode, event.count)
                }

                else -> Unit
            }
        } catch (e: Exception) {
            handleRemoteException(e, "Failed to inject event")
        }
    }

    /**
     * Runs one AIDL call against the bound privileged service.
     *
     * A dead binder means the privileged process is gone, so it must drop the session
     * rather than be logged and ignored; anything else is a per-call failure. Every
     * remote call routes through here so the two are never confused, and so the log
     * names the actual host instead of always saying Shizuku.
     */
    private inline fun <T> remote(what: String?, fallback: T, call: (IInputInjector) -> T): T {
        val svc = service ?: return fallback
        return try {
            call(svc)
        } catch (e: Exception) {
            handleRemoteException(e, what)
            fallback
        }
    }

    private fun handleRemoteException(e: Exception, message: String?) {
        if (e is DeadObjectException || e is RemoteException ||
            e.cause is DeadObjectException || e.cause is RemoteException
        ) {
            Log.w(TAG, "${host.kind} service binder is dead (${message ?: "remote call"})", e)
            notifyDisconnected()
        } else if (message != null) {
            Log.w(TAG, message, e)
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
            is KeysymAction.Ignore -> Unit
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
        if (scancode != 0 && svc.injectHidKey(scancode, true)) {
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
        val action = if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        svc.injectKeyEvent(action, keyCode, scancode, metaState)
    }

    private fun inputLeapButtonToAndroid(buttonId: Int): Int {
        return when (buttonId) {
            1 -> MotionEvent.BUTTON_PRIMARY
            2 -> MotionEvent.BUTTON_TERTIARY
            3 -> MotionEvent.BUTTON_SECONDARY
            else -> 0
        }
    }

    private fun inputLeapButtonToHid(buttonId: Int): Int {
        return when (buttonId) {
            1 -> 1
            3 -> 2
            2 -> 4
            else -> 0
        }
    }
}
