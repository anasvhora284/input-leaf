package com.inputleaf.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
    favoriteServers: Set<String>,
    onScan: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onAddManual: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    val favorites = discoveredServers.filter { favoriteServers.contains(it.ip) }
    val others = discoveredServers.filter { !favoriteServers.contains(it.ip) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Servers") })
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            // Favorites section
            if (favorites.isNotEmpty()) {
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                "Favorites",
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    HorizontalDivider()
                }

                items(favorites) { server ->
                    val isConnected = isServerConnected(connectionState, server)
                    ServerListItem(
                        server = server,
                        isConnected = isConnected,
                        onServerClick = onConnect,
                        isFavorite = true,
                        onToggleFavorite = { onToggleFavorite(server.ip) }
                    )
                }
            }

            // Discovered servers
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

            items(others) { server ->
                val isConnected = isServerConnected(connectionState, server)
                ServerListItem(
                    server = server,
                    isConnected = isConnected,
                    onServerClick = onConnect,
                    isFavorite = false,
                    onToggleFavorite = { onToggleFavorite(server.ip) }
                )
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
                    manualIp = ""
                    showAddDialog = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    manualIp = ""
                    showAddDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun isServerConnected(connectionState: ConnectionState, server: ServerInfo): Boolean {
    return when (connectionState) {
        is ConnectionState.Idle -> connectionState.serverIp == server.ip
        is ConnectionState.Active -> connectionState.serverIp == server.ip
        else -> false
    }
}
