package com.inputleaf.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.ui.components.GradientCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    connectionState: ConnectionState,
    discoveredServers: List<ServerInfo>,
    isScanning: Boolean,
    screenName: String,
    shizukuStatus: MainViewModel.ShizukuStatus,
    mouseEnabled: Boolean,
    keyboardEnabled: Boolean,
    favoriteServers: Set<String>,
    onScan: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onDisconnect: () -> Unit,
    onAddManual: (String) -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onToggleMouse: (Boolean) -> Unit,
    onToggleKeyboard: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Input Leaf") })
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Connection status card
            item {
                ConnectionStatusCard(
                    state = connectionState,
                    screenName = screenName,
                    onDisconnect = onDisconnect
                )
            }

            // Mouse & Keyboard toggle cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToggleCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Mouse,
                        label = "Mouse",
                        enabled = mouseEnabled,
                        onToggle = { onToggleMouse(!mouseEnabled) }
                    )
                    ToggleCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Keyboard,
                        label = "Keyboard",
                        enabled = keyboardEnabled,
                        onToggle = { onToggleKeyboard(!keyboardEnabled) }
                    )
                }
            }

            // Favorite servers quick connect
            val favorites = discoveredServers.filter { favoriteServers.contains(it.ip) }
            if (favorites.isNotEmpty()) {
                item {
                    Text(
                        text = "Quick Connect",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(favorites) { server ->
                    val isConnected = when (connectionState) {
                        is ConnectionState.Idle -> connectionState.serverIp == server.ip
                        is ConnectionState.Active -> connectionState.serverIp == server.ip
                        else -> false
                    }
                    ServerListItem(server, isConnected, onConnect)
                }
            }

            // Shizuku warning if not ready
            if (shizukuStatus != MainViewModel.ShizukuStatus.READY) {
                item {
                    com.inputleaf.android.ui.components.ShizukuStatusCard(
                        status = shizukuStatus,
                        onRequestPermission = onRequestShizukuPermission,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Device info
            item {
                GradientCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    cornerRadius = 20.dp,
                    elevation = 0.dp,
                    padding = 16.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = "Device",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                text = "Device Name",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = screenName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    state: ConnectionState,
    screenName: String,
    onDisconnect: () -> Unit
) {
    val (statusLabel, statusIcon, containerColor, contentColor, serverInfo) = when (state) {
        is ConnectionState.Active -> StatusInfo(
            "CONNECTED", Icons.Rounded.CheckCircle,
            Color(0xFF1B5E20).copy(alpha = 0.15f), Color(0xFF4CAF50),
            "${state.serverName} • ${state.serverIp}"
        )
        is ConnectionState.Idle -> StatusInfo(
            "IDLE", Icons.Rounded.Pause,
            Color(0xFF0D47A1).copy(alpha = 0.15f), Color(0xFF42A5F5),
            "${state.serverName} • ${state.serverIp}"
        )
        is ConnectionState.Connecting -> StatusInfo(
            "CONNECTING...", Icons.Rounded.Sync,
            Color(0xFFF57F17).copy(alpha = 0.15f), Color(0xFFFFB300),
            state.serverIp
        )
        is ConnectionState.Handshaking -> StatusInfo(
            "HANDSHAKING...", Icons.Rounded.Sync,
            Color(0xFFF57F17).copy(alpha = 0.15f), Color(0xFFFFB300),
            state.serverIp
        )
        is ConnectionState.Disconnected -> StatusInfo(
            "DISCONNECTED", Icons.Rounded.LinkOff,
            Color(0xFFB71C1C).copy(alpha = 0.12f), Color(0xFFEF5350),
            "No server connected"
        )
    }

    val animatedColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(500),
        label = "status_color"
    )

    val isConnected = state is ConnectionState.Active || state is ConnectionState.Idle

    GradientCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        backgroundColor = animatedColor,
        cornerRadius = 24.dp,
        elevation = 0.dp,
        padding = 20.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = contentColor.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusLabel,
                        modifier = Modifier.size(26.dp),
                        tint = contentColor
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = contentColor,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = serverInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (isConnected) {
                FilledTonalButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = contentColor.copy(alpha = 0.2f),
                        contentColor = contentColor
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Disconnect", fontSize = 12.sp)
                }
            }
        }
    }
}

private data class StatusInfo(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val serverInfo: String
)

@Composable
private fun ToggleCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "toggle_bg"
    )
    val contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant

    GradientCard(
        modifier = modifier.clickable { onToggle() },
        backgroundColor = bgColor,
        cornerRadius = 20.dp,
        elevation = 0.dp,
        padding = 16.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(28.dp),
                    tint = contentColor
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .padding(0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = if (enabled) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                    ) {}
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            Text(
                text = if (enabled) "Enabled" else "Disabled",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
    }
}
