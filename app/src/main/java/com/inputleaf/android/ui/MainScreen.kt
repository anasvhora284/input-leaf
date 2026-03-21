package com.inputleaf.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.ui.components.CircularAvatar
import com.inputleaf.android.ui.components.GradientCard
import com.inputleaf.android.ui.components.ShizukuStatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    connectionState: ConnectionState,
    discoveredServers: List<ServerInfo>,
    isScanning: Boolean,
    screenName: String,
    shizukuStatus: MainViewModel.ShizukuStatus,
    onScan: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onAddManual: (String) -> Unit,
    onRequestShizukuPermission: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Input-Leaf") }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            // Status card
            item {
                StatusCard(connectionState, screenName)
            }
            // Quick info cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GradientCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        cornerRadius = 24.dp,
                        elevation = 0.dp,
                        padding = 16.dp
                    ) {
                        Column {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = "Screen",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SCREEN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = screenName,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    GradientCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        cornerRadius = 24.dp,
                        elevation = 0.dp,
                        padding = 16.dp
                    ) {
                        Column {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Cursor",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "CURSOR",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enabled",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            // Shizuku status card
            item {
                ShizukuStatusCard(
                    status = shizukuStatus,
                    onRequestPermission = onRequestShizukuPermission,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            // Search bar
            item {
                ServerSearchBar(onScanClick = onScan)
            }
            // Server list header
            item {
                ListItem(headlineContent = { Text("Discovered Servers", style = MaterialTheme.typography.titleSmall) })
                HorizontalDivider()
            }
            items(discoveredServers) { server ->
                val isConnected = when (connectionState) {
                    is ConnectionState.Idle   -> connectionState.serverIp == server.ip
                    is ConnectionState.Active -> connectionState.serverIp == server.ip
                    else -> false
                }
                ServerListItem(server, isConnected, onConnect)
            }
            // Action buttons
            item {
                Row(Modifier.padding(16.dp)) {
                    OutlinedButton(onClick = { showAddDialog = true }) { Text("Add Manually") }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Server") },
            text = {
                OutlinedTextField(manualIp, { manualIp = it },
                    label = { Text("IP Address") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { onAddManual(manualIp); showAddDialog = false }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StatusCard(state: ConnectionState, screenName: String) {
    val isConnected = state is ConnectionState.Active || state is ConnectionState.Idle
    val statusLabel = when (state) {
        is ConnectionState.Active -> "CONNECTED"
        is ConnectionState.Idle -> "IDLE"
        is ConnectionState.Connecting, is ConnectionState.Handshaking -> "CONNECTING"
        is ConnectionState.Disconnected -> "DISCONNECTED"
    }
    val serverName = when (state) {
        is ConnectionState.Active -> state.serverName ?: ""
        is ConnectionState.Idle -> state.serverName ?: ""
        else -> ""
    }
    
    GradientCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        backgroundColor = MaterialTheme.colorScheme.surface,
        cornerRadius = 28.dp,
        elevation = 1.dp,
        padding = 20.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularAvatar(
                    icon = Icons.Rounded.Build,
                    size = 56.dp,
                    iconSize = 28.dp,
                    backgroundColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    iconTint = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    elevation = if (isConnected) 4.dp else 0.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusLabel,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (serverName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = serverName,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Screen: $screenName",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerSearchBar(
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GradientCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        backgroundColor = MaterialTheme.colorScheme.surface,
        cornerRadius = 28.dp,
        elevation = 1.dp,
        padding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Search servers...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onScanClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Scan",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
