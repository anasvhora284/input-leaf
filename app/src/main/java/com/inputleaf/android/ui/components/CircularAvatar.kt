package com.inputleaf.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CircularAvatar(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 28.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    iconTint: Color = contentColorFor(backgroundColor),
    elevation: Dp = 0.dp,
    shape: Shape = CircleShape,
) {
    AvatarSurface(modifier, size, backgroundColor, iconTint, elevation, shape) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun CircularAvatar(
    painter: Painter,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 28.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    iconTint: Color = contentColorFor(backgroundColor),
    elevation: Dp = 0.dp,
    shape: Shape = CircleShape,
) {
    AvatarSurface(modifier, size, backgroundColor, iconTint, elevation, shape) {
        Icon(painter = painter, contentDescription = null, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun AvatarSurface(
    modifier: Modifier,
    size: Dp,
    backgroundColor: Color,
    iconTint: Color,
    elevation: Dp,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = backgroundColor,
        contentColor = iconTint,
        shadowElevation = elevation,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
