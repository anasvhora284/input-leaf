package com.inputleaf.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
    backgroundColor: Color = Color(0xFFF5F5F5),
    iconTint: Color = Color.Black,
    elevation: Dp = 0.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation, CircleShape)
            .clip(CircleShape)
            .then(
                if (background != null) {
                    Modifier.background(background)
                } else {
                    Modifier.background(backgroundColor)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = iconTint
        )
    }
}
