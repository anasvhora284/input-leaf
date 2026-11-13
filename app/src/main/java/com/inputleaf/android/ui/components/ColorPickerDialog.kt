package com.inputleaf.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.inputleaf.android.service.formatCursorHex

/** Picks a colour with hue, saturation and brightness bars and a live preview (#81). */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val hsv = remember {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(initial, out)
        // Black (the default cursor) has no hue to show; start bright so the bars mean something.
        if (out[2] == 0f) { out[1] = 1f; out[2] = 1f }
        mutableStateOf(out.toList())
    }
    val (h, s, v) = hsv.value
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    // Hue and saturation do nothing at zero brightness, so moving them brings it back up.
    fun set(h2: Float = h, s2: Float = s, v2: Float = v) {
        hsv.value = listOf(h2, s2, if (v2 == 0f && (h2 != h || s2 != s)) 1f else v2)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(argb),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {}
                Text(formatCursorHex(argb), style = MaterialTheme.typography.labelLarge)
                GradientSlider(
                    value = h / 360f,
                    brush = Brush.horizontalGradient(
                        List(7) { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) },
                    ),
                    description = "Hue",
                    onChange = { set(h2 = it * 360f) },
                )
                GradientSlider(
                    value = s,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 0f, v))),
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, v))),
                        ),
                    ),
                    description = "Saturation",
                    onChange = { set(s2 = it) },
                )
                GradientSlider(
                    value = v,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Black,
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, 1f))),
                        ),
                    ),
                    description = "Brightness",
                    onChange = { set(v2 = it) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(argb) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A bar painted with [brush] whose thumb sits at [value] (0..1); tap or drag to move it. */
@Composable
private fun GradientSlider(
    value: Float,
    brush: Brush,
    description: String,
    onChange: (Float) -> Unit,
) {
    var widthPx by remember { mutableStateOf(1) }
    val thumb = 28.dp
    val thumbPx = with(LocalDensity.current) { thumb.toPx() }
    // The thumb centre travels between half a thumb in from each end.
    fun fraction(x: Float) = ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumb)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { onChange(fraction(it.x)) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> onChange(fraction(change.position.x)) } },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .align(androidx.compose.ui.Alignment.Center)
                .clip(RoundedCornerShape(6.dp))
                .background(brush),
        )
        Surface(
            modifier = Modifier
                .offset(x = with(LocalDensity.current) { (value * (widthPx - thumbPx)).toDp() })
                .size(thumb),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, Color(0x44000000)),
        ) {}
    }
}
