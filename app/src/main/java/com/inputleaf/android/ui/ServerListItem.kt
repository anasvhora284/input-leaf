package com.inputleaf.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.ui.components.CircularAvatar
import com.inputleaf.android.ui.components.GradientCard

@Composable
fun ServerListItem(
    server: ServerInfo,
    isConnected: Boolean,
    onServerClick: (ServerInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    GradientCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onServerClick(server) },
        backgroundColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        cornerRadius = 24.dp,
        elevation = if (isConnected) 0.dp else 1.dp,
        padding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularAvatar(
                icon = Icons.Rounded.Build,
                size = 48.dp,
                iconSize = 24.dp,
                backgroundColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                iconTint = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                elevation = if (isConnected) 2.dp else 0.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name.ifBlank { server.ip },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isConnected) "${server.ip} • Connected" else server.ip,
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isConnected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .shadow(4.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = "Navigate",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
