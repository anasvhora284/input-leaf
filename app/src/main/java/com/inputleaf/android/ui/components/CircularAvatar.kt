package com.inputleaf.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CircularAvatar(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 28.dp,
    background: Brush? = null,
    backgroundColor: Color? = null,
    iconTint: Color? = null,
    elevation: Dp = 0.dp
) {
    val bgColor = backgroundColor ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val fgColor = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation, CircleShape)
            .clip(CircleShape)
            .then(
                if (background != null) {
                    Modifier.background(background)
                } else {
                    Modifier.background(bgColor)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = fgColor
        )
    }
}
