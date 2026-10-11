package com.inputleaf.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerListScreen(
    connectionState: ConnectionState,
    discoveredServers: List<ServerInfo>,
    isScanning: Boolean,
    favoriteServers: Set<String>,
    onScan: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onAddManual: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    pendingConnectIp: String? = null,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    val connectingIp = connectingServerIp(connectionState, pendingConnectIp)
    val favorites = discoveredServers.filter { favoriteServers.contains(it.ip) }
    val others = discoveredServers.filter { !favoriteServers.contains(it.ip) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Servers") },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (favorites.isNotEmpty()) {
                item { SectionHeader("Favorites") }
                itemsIndexed(favorites) { index, server ->
                    ServerListItem(
                        server = server,
                        isConnected = isServerConnected(connectionState, server),
                        onServerClick = onConnect,
                        shapes = ListItemDefaults.segmentedShapes(index, favorites.size),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        isFavorite = true,
                        onToggleFavorite = { onToggleFavorite(server.ip) },
                        isConnecting = connectingIp == server.ip,
                        enabled = connectingIp == null,
                    )
                }
            }

            item { SectionHeader("Discovered Servers") }
            itemsIndexed(others) { index, server ->
                ServerListItem(
                    server = server,
                    isConnected = isServerConnected(connectionState, server),
                    onServerClick = onConnect,
                    shapes = ListItemDefaults.segmentedShapes(index, others.size),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    isFavorite = false,
                    onToggleFavorite = { onToggleFavorite(server.ip) },
                    isConnecting = connectingIp == server.ip,
                    enabled = connectingIp == null,
                )
            }

            if (discoveredServers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScanning) {
                            LoadingIndicator()
                        } else {
                            Text(
                                text = "No servers found. Try scanning again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onScan,
                        enabled = !isScanning && connectingIp == null,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isScanning) {
                            LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        }
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Scan Again")
                    }
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        enabled = connectingIp == null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Add Manually")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
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
