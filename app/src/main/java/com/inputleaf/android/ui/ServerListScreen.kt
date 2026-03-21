package com.inputleaf.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    connectionState: ConnectionState,
    discoveredServers: List<ServerInfo>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onAddManual: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servers") }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            // Server list header
            item {
                ListItem(
                    headlineContent = { 
                        Text(
                            "Discovered Servers", 
                            style = MaterialTheme.typography.titleMedium
                        ) 
                    }
                )
                HorizontalDivider()
            }

            // Server list
            items(discoveredServers) { server ->
                val isConnected = when (connectionState) {
                    is ConnectionState.Idle   -> connectionState.serverIp == server.ip
                    is ConnectionState.Active -> connectionState.serverIp == server.ip
                    else -> false
                }
                ServerListItem(server, isConnected, onConnect)
            }

            // Empty state
            if (discoveredServers.isEmpty() && !isScanning) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            text = "No servers found. Try scanning again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action buttons
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onScan, 
                        enabled = !isScanning,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Scan Again")
                    }
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.weight(1f)
                    ) { 
                        Text("Add Manually") 
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Server") },
            text = {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text("IP Address") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    onAddManual(manualIp)
                    showAddDialog = false 
                }) { 
                    Text("Add") 
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { 
                    Text("Cancel") 
                }
            }
        )
    }
}
