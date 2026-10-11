package com.inputleaf.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.ui.components.CircularAvatar

internal fun connectingServerIp(
    connectionState: ConnectionState,
    pendingConnectIp: String?,
): String? = pendingConnectIp ?: when (connectionState) {
    is ConnectionState.Connecting -> connectionState.serverIp
    is ConnectionState.Handshaking -> connectionState.serverIp
    else -> null
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerListItem(
    server: ServerInfo,
    isConnected: Boolean,
    onServerClick: (ServerInfo) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    isConnecting: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = { onServerClick(server) },
        shapes = shapes,
        modifier = modifier,
        enabled = enabled && !isConnecting && !isConnected,
        colors = if (isConnected) {
            ListItemDefaults.segmentedColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                supportingContentColor = colors.onPrimaryContainer,
                disabledContainerColor = colors.primaryContainer,
                disabledContentColor = colors.onPrimaryContainer,
                disabledSupportingContentColor = colors.onPrimaryContainer,
                disabledLeadingContentColor = colors.onPrimaryContainer,
                disabledTrailingContentColor = colors.onPrimaryContainer,
            )
        } else {
            ListItemDefaults.segmentedColors()
        },
        leadingContent = {
            CircularAvatar(
                icon = Icons.Rounded.Computer,
                size = 48.dp,
                iconSize = 24.dp,
                backgroundColor = if (isConnected) colors.primary else colors.secondaryContainer,
                shape = if (isConnected) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Circle.toShape(),
            )
        },
        supportingContent = {
            Text(
                when {
                    isConnecting -> "Connecting…"
                    isConnected -> "${server.ip} • Connected"
                    else -> server.ip
                },
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToggleFavorite != null) {
                    IconToggleButton(
                        checked = isFavorite,
                        onCheckedChange = { onToggleFavorite() },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        )
                    }
                }
                when {
                    isConnecting -> LoadingIndicator(Modifier.size(32.dp))
                    isConnected -> Badge(containerColor = colors.primary)
                    onToggleFavorite == null ->
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Navigate")
                }
            }
        },
    ) {
        Text(server.name.ifBlank { server.ip })
    }
}
