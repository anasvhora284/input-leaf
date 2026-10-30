package com.inputleaf.android.inject

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.inputleaf.android.ui.theme.InputLeafTheme
import kotlin.math.abs

/**
 * A small floating button on the screen edge while the mouse is on the phone in
 * Accessibility mode. Clicking it switches between Input Leaf's keyboard and the user's own
 * (for emoji, clipboard, voice and so on). It can be dragged along either side edge and
 * stays where it was left.
 *
 * Drawn like the app: Material 3 Expressive with the device's dynamic colours. While keys
 * come from the PC it is a cookie in the primary container colour with a computer icon;
 * with the phone's own keyboard out it morphs into a soft burst in the tertiary colour with
 * a phone icon.
 */
internal class KeyboardBubble(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val size = dp(WINDOW_DP)
    private val lent = mutableStateOf(false)

    private var root: ComposeView? = null
    private var owner: OverlayOwner? = null

    /** Show, hide or update the bubble for ImeSwitcher's current state. Any thread. */
    fun refresh() {
        main.post {
            lent.value = ImeSwitcher.lent
            if (ImeSwitcher.wanted && ImeSwitcher.canLend) show() else root?.visibility = View.GONE
        }
    }

    fun remove() {
        main.post {
            root?.let { runCatching { windowManager.removeView(it) } }
            owner?.destroy()
            root = null
            owner = null
        }
    }

    private fun show() {
        val view = root ?: create() ?: return
        if (view.visibility != View.VISIBLE) {
            view.visibility = View.VISIBLE
            // Windows of one type stack in the order they were added; keep the cursor above.
            (service as? AccessibilityInputService)?.bringCursorToFront()
        }
    }

    private fun create(): ComposeView? {
        val lifecycleOwner = OverlayOwner()
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            contentDescription = "Switch keyboard"
            setContent { InputLeafTheme(darkTheme = isSystemInDarkTheme()) { Bubble(lent.value) } }
        }
        val screen = service.resources.displayMetrics
        val layout = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // LEFT, not START: START mirrors on RTL locales.
            gravity = Gravity.TOP or Gravity.LEFT
            x = if (prefs.getBoolean(KEY_LEFT, false)) 0 else screen.widthPixels - size
            y = BubblePlacement.clampY(
                prefs.getInt(KEY_Y, BubblePlacement.defaultY(size, screen.heightPixels)),
                size,
                screen.heightPixels,
            )
        }
        view.setOnTouchListener(DragOrClick(layout))
        lifecycleOwner.start()
        return runCatching { windowManager.addView(view, layout) }
            .onFailure {
                android.util.Log.w(TAG, "Could not show the keyboard bubble", it)
                lifecycleOwner.destroy()
            }
            .map {
                root = view
                owner = lifecycleOwner
                (service as? AccessibilityInputService)?.bringCursorToFront()
                view
            }
            .getOrNull()
    }

    /** A drag moves the bubble and snaps it to the nearer side edge; a tap toggles keyboards. */
    private inner class DragOrClick(private val layout: WindowManager.LayoutParams) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(service).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layout.x
                    startY = layout.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        layout.x = startX + dx.toInt()
                        layout.y = startY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(view, layout) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        ImeSwitcher.toggle(service)
                        return true
                    }
                    val screen = service.resources.displayMetrics
                    layout.x = BubblePlacement.snapX(layout.x, size, screen.widthPixels)
                    layout.y = BubblePlacement.clampY(layout.y, size, screen.heightPixels)
                    runCatching { windowManager.updateViewLayout(view, layout) }
                    prefs.edit().putBoolean(KEY_LEFT, layout.x == 0).putInt(KEY_Y, layout.y).apply()
                }
            }
            return true
        }
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "KeyboardBubble"
        const val PREFS = "keyboard_bubble"
        const val KEY_LEFT = "left"
        const val KEY_Y = "y"
        /** A little room around the 52 dp shape so its rotation is never clipped. */
        const val WINDOW_DP = 60
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Bubble(lent: Boolean) {
    val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    val progress by animateFloatAsState(if (lent) 1f else 0f, bouncy, label = "morph")
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (lent) colors.tertiaryContainer else colors.primaryContainer, label = "container")
    val content by animateColorAsState(if (lent) colors.onTertiaryContainer else colors.onPrimaryContainer, label = "content")
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val shape = remember(progress) { MorphShape(morph, progress) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer { rotationZ = progress * 40f }
                // No shadow: the cookie is concave, and Android's concave shadow tessellation
                // ran every animation frame and froze the app's render thread (ANR).
                .clip(shape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = lent,
                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "icon",
            ) { userKeyboardOut ->
                Icon(
                    // Where the keys come from right now: the PC, or the phone's own keyboard.
                    imageVector = if (userKeyboardOut) Icons.Rounded.Smartphone else Icons.Rounded.Computer,
                    contentDescription = null,
                    tint = content,
                    // Counter-rotate so the icon stays upright while the shape turns.
                    modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = -progress * 40f },
                )
            }
        }
    }
}

/** A shape partway through [morph]; MaterialShapes are drawn in a unit square. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, android.graphics.Path())
        path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}

/** Lifecycle for a ComposeView living in a service-owned window rather than an Activity. */
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
