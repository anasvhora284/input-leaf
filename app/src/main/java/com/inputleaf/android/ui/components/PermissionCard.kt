package com.inputleaf.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    buttonLabel: String = "Grant",
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "bg_color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "content_color",
    )
    val avatarBgColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(300),
        label = "avatar_bg_color",
    )
    val iconTintColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.onTertiary
            else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(300),
        label = "icon_tint_color",
    )

    Card(
        onClick = onRequestPermission,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = bgColor, contentColor = contentColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularAvatar(
                icon = if (isGranted) Icons.Rounded.CheckCircle else icon,
                size = 48.dp,
                iconSize = 24.dp,
                backgroundColor = avatarBgColor,
                iconTint = iconTintColor,
                shape = if (isGranted) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Circle.toShape(),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
                AnimatedVisibility(
                    visible = !isGranted,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(buttonLabel)
                    }
                }
            }
        }
    }
}
