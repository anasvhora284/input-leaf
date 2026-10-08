package com.inputleaf.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
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
import com.inputleaf.android.ui.components.SectionHeader
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
    var serverToDelete by remember { mutableStateOf<String?>(null) }

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
                        SettingsRow(
                            icon = Icons.Rounded.Bookmark,
                            title = "Saved & Favorite Servers",
                            subtitle = when (savedServerList.size) {
                                0 -> "No saved servers • Tap to add or view"
                                1 -> "1 saved server • Tap to manage"
                                else -> "${savedServerList.size} saved servers • Tap to manage"
                            },
                            shapes = shapes,
                            onClick = { showSavedServersSheet = true },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (savedServerList.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                            Text(
                                                text = "${savedServerList.size}",
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "View Saved Servers",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Saved Servers
    if (showSavedServersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSavedServersSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularAvatar(
                        icon = Icons.Rounded.Bookmark,
                        size = 44.dp,
                        iconSize = 22.dp,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Saved Servers",
                            style = MaterialTheme.typography.titleLargeEmphasized,
                        )
                        Text(
                            text = "Manage saved host addresses, favorites, and quick connection targets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (savedServerList.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        savedServerList.forEachIndexed { index, server ->
                            ServerListItem(
                                server = server,
                                isConnected = isServerConnected(connectionState, server),
                                onServerClick = {
                                    onConnect(server)
                                    showSavedServersSheet = false
                                },
                                shapes = ListItemDefaults.segmentedShapes(index, savedServerList.size),
                                isFavorite = favoriteServers.contains(server.ip),
                                onToggleFavorite = { onToggleFavorite(server.ip) },
                                onDelete = { serverToDelete = server.ip },
                                isConnecting = connectingIp == server.ip,
                                enabled = connectingIp == null,
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No saved servers yet. Add one manually or star a discovered server.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Add Server Manually")
                }
            }
        }
    }

    // Delete Saved Server Confirmation Dialog
    serverToDelete?.let { ip ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("Remove Saved Server") },
            text = { Text("Remove $ip from your saved servers list?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveSavedServer(ip)
                    serverToDelete = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text("Cancel")
                }
            },
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

private fun isServerConnected(connectionState: ConnectionState, server: ServerInfo): Boolean {
    return when (connectionState) {
        is ConnectionState.Idle -> connectionState.serverIp == server.ip
        is ConnectionState.Active -> connectionState.serverIp == server.ip
        else -> false
    }
}
