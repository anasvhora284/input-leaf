package com.inputleaf.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    connectionState: ConnectionState,
    discoveredServers: List<ServerInfo>,
    isScanning: Boolean,
    screenName: String,
    onScan: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onAddManual: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Input-Leaf") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            // Status card
            item {
                StatusCard(connectionState, screenName)
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
                ServerListItem(server, isConnected) { onConnect(server) }
            }
            // Action buttons
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScan, enabled = !isScanning) {
                        if (isScanning) CircularProgressIndicator(Modifier.size(16.dp))
                        else Text("Scan Again")
                    }
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
    val (color, label, serverName) = when (state) {
        is ConnectionState.Active -> Triple(Color(0xFF4CAF50), "ACTIVE", state.serverName)
        is ConnectionState.Idle   -> Triple(Color(0xFF2196F3), "IDLE", state.serverName)
        is ConnectionState.Connecting, is ConnectionState.Handshaking ->
            Triple(Color(0xFFFF9800), "CONNECTING", "")
        is ConnectionState.Disconnected -> Triple(Color(0xFFF44336), "DISCONNECTED", "")
    }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(12.dp), color = color, shape = MaterialTheme.shapes.small) {}
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
            if (serverName.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Connected to: $serverName")
            }
            Spacer(Modifier.height(4.dp))
            Text("Screen name: $screenName", style = MaterialTheme.typography.bodySmall)
        }
    }
}
