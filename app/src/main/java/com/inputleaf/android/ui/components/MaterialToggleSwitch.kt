package com.inputleaf.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MaterialToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 20f else 2f,
        animationSpec = tween(durationMillis = 300),
        label = "thumb_offset"
    )

    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .width(52.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (checked) Brush.linearGradient(
                    listOf(scheme.primary, scheme.primary.copy(alpha = 0.85f)),
                ) else Brush.linearGradient(
                    listOf(scheme.surfaceVariant, scheme.outlineVariant.copy(alpha = 0.5f)),
                ),
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.dp)
                .size(28.dp)
                .shadow(
                    elevation = if (checked) 6.dp else 3.dp,
                    shape = CircleShape,
                    ambientColor = if (checked) scheme.primary.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)
                )
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
