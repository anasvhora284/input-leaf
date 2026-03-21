package com.inputleaf.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    gradient: Brush? = null,
    backgroundColor: Color? = null,
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 1.dp,
    padding: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val surfaceColor = backgroundColor ?: MaterialTheme.colorScheme.surface
    
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (gradient != null) {
                    Modifier.background(gradient)
                } else {
                    Modifier.background(surfaceColor)
                }
            )
            .padding(padding)
    ) {
        content()
    }
}
