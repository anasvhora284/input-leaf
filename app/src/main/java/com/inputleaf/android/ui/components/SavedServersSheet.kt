package com.inputleaf.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.ui.ServerListItem
import com.inputleaf.android.ui.isServerConnected

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SavedServersSheet(
    savedServers: List<ServerInfo>,
    connectionState: ConnectionState,
    favoriteServers: Set<String>,
    connectingIp: String?,
    onConnect: (ServerInfo) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onAddManualClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    var serverToDelete by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ExpressiveSheetHeader(
                icon = Icons.Rounded.Bookmark,
                title = "Saved Servers",
                description = "Manage saved host addresses, favorites, and quick connection targets.",
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (savedServers.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    savedServers.forEachIndexed { index, server ->
                        ServerListItem(
                            server = server,
                            isConnected = isServerConnected(connectionState, server),
                            onServerClick = {
                                onConnect(server)
                                onDismiss()
                            },
                            shapes = ListItemDefaults.segmentedShapes(index, savedServers.size),
                            isFavorite = favoriteServers.contains(server.ip),
                            onToggleFavorite = { onToggleFavorite(server.ip) },
                            onDelete = { serverToDelete = server.ip },
                            isConnecting = connectingIp == server.ip,
                            enabled = connectingIp == null,
                        )
                    }
                }
            } else {
                ExpressiveEmptyCard(
                    message = "No saved servers yet. Add one manually or star a discovered server.",
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onAddManualClick,
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

    serverToDelete?.let { ip ->
        val matchingServer = savedServers.find { it.ip == ip }
        val label = matchingServer?.name?.takeIf { it.isNotBlank() && it != ip }?.let { "$it ($ip)" } ?: ip
        DeleteConfirmationDialog(
            title = "Remove Saved Server",
            message = "Remove $label from your saved servers list?",
            confirmLabel = "Remove",
            onConfirm = {
                onDeleteServer(ip)
                serverToDelete = null
            },
            onDismiss = { serverToDelete = null },
        )
    }
}
