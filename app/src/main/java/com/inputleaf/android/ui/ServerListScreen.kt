package com.inputleaf.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.ui.components.CircularAvatar
import com.inputleaf.android.ui.components.SavedServersSheet
import com.inputleaf.android.ui.components.SectionHeader
import com.inputleaf.android.ui.components.SettingsBadgeRow
import com.inputleaf.android.ui.components.SettingsGroup
import com.inputleaf.android.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerListScreen(
    connectionState: ConnectionState,
    scannedServers: List<ServerInfo>,
    isScanning: Boolean,
    favoriteServers: Set<String>,
    onScan: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onAddManual: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    savedServerIps: Set<String> = emptySet(),
    serverNames: Map<String, String> = emptyMap(),
    onRemoveSavedServer: (String) -> Unit = {},
    pendingConnectIp: String? = null,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var showSavedServersSheet by remember { mutableStateOf(false) }

    val connectingIp = connectingServerIp(connectionState, pendingConnectIp)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val savedServerList = remember(savedServerIps, serverNames, scannedServers, favoriteServers) {
        savedServerIps.sortedWith(
            compareByDescending<String> { favoriteServers.contains(it) }
                .thenBy { it }
        ).map { ip ->
            val scannedMatch = scannedServers.find { it.ip == ip }
            val name = serverNames[ip]
                ?: scannedMatch?.name?.takeIf { it.isNotBlank() && it != ip }
                ?: ""
            val port = scannedMatch?.port ?: 24800
            ServerInfo(ip = ip, port = port, name = name)
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Section 1: Scanned / Discovered Servers on the local network
            item { SectionHeader("Scanned Servers") }

            if (scannedServers.isNotEmpty()) {
                itemsIndexed(scannedServers) { index, server ->
                    ServerListItem(
                        server = server,
                        isConnected = isServerConnected(connectionState, server),
                        onServerClick = onConnect,
                        shapes = ListItemDefaults.segmentedShapes(index, scannedServers.size),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        isFavorite = favoriteServers.contains(server.ip),
                        onToggleFavorite = { onToggleFavorite(server.ip) },
                        isConnecting = connectingIp == server.ip,
                        enabled = connectingIp == null,
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (isScanning) {
                                Box(
                                    modifier = Modifier.size(48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Scanning network…",
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Looking for Synergy, Barrier, and Deskflow servers on your Wi-Fi subnet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                CircularAvatar(
                                    icon = Icons.Rounded.Dns,
                                    size = 48.dp,
                                    iconSize = 24.dp,
                                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "No servers discovered",
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Ensure your desktop server is running on the same network, then scan again or add its IP below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Action Buttons below the scanned server list
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = onScan,
                        enabled = !isScanning && connectingIp == null,
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        if (isScanning) {
                            LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                        }
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(if (isScanning) "Scanning…" else "Scan Again")
                    }

                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        enabled = connectingIp == null,
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Add Manually")
                    }
                }
            }

            // Section 3: Saved & Favorite Servers entry option below the buttons
            item { SectionHeader("Saved Servers") }

            item {
                SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    row { shapes ->
                        SettingsBadgeRow(
                            icon = Icons.Rounded.Bookmark,
                            title = "Saved & Favorite Servers",
                            subtitle = when (savedServerList.size) {
                                0 -> "No saved servers • Tap to add or view"
                                1 -> "1 saved server • Tap to manage"
                                else -> "${savedServerList.size} saved servers • Tap to manage"
                            },
                            count = savedServerList.size.takeIf { it > 0 },
                            shapes = shapes,
                            onClick = { showSavedServersSheet = true },
                        )
                    }
                }
            }
        }
    }

    if (showSavedServersSheet) {
        SavedServersSheet(
            savedServers = savedServerList,
            connectionState = connectionState,
            favoriteServers = favoriteServers,
            connectingIp = connectingIp,
            onConnect = { server ->
                onConnect(server)
                showSavedServersSheet = false
            },
            onToggleFavorite = onToggleFavorite,
            onDeleteServer = onRemoveSavedServer,
            onAddManualClick = { showAddDialog = true },
            onDismiss = { showSavedServersSheet = false },
        )
    }

    // Add Server Manually Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            title = { Text("Add Server") },
            text = {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text("IP Address or Hostname") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = manualIp.trim()
                        if (trimmed.isNotBlank()) {
                            onAddManual(trimmed)
                            manualIp = ""
                            showAddDialog = false
                        }
                    },
                    enabled = manualIp.isNotBlank(),
                ) {
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
            },
        )
    }
}

internal fun isServerConnected(connectionState: ConnectionState, server: ServerInfo): Boolean {
    return when (connectionState) {
        is ConnectionState.Idle -> connectionState.serverIp == server.ip
        is ConnectionState.Active -> connectionState.serverIp == server.ip
        else -> false
    }
}
