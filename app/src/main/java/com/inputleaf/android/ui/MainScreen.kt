package com.inputleaf.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.ui.components.CircularAvatar
import com.inputleaf.android.ui.components.GradientCard
import com.inputleaf.android.ui.theme.Gradients
import com.inputleaf.android.ui.theme.Purple600
import com.inputleaf.android.ui.theme.TextPrimary
import com.inputleaf.android.ui.theme.TextSecondary
import com.inputleaf.android.ui.theme.TextTertiary

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
    onRequestShizukuPermission: () -> Unit,
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
                        gradient = Gradients.Accent,
                        cornerRadius = 24.dp,
                        elevation = 0.dp,
                        padding = 16.dp
                    ) {
                        Column {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = "Screen",
                                modifier = Modifier.size(28.dp),
                                tint = Purple600
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SCREEN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Purple600
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = screenName,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    GradientCard(
                        modifier = Modifier.weight(1f),
                        gradient = Gradients.Accent,
                        cornerRadius = 24.dp,
                        elevation = 0.dp,
                        padding = 16.dp
                    ) {
                        Column {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Cursor",
                                modifier = Modifier.size(28.dp),
                                tint = Purple600
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "CURSOR",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Purple600
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enabled",
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            // Shizuku status card
            item {
                ShizukuStatusCard(shizukuStatus, onRequestShizukuPermission)
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
private fun ShizukuStatusCard(
    status: MainViewModel.ShizukuStatus,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    
    when (status) {
        MainViewModel.ShizukuStatus.READY -> {
            GradientCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                gradient = Gradients.Success,
                cornerRadius = 24.dp,
                elevation = 0.dp,
                padding = 20.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularAvatar(
                        icon = Icons.Rounded.CheckCircle,
                        size = 48.dp,
                        iconSize = 28.dp,
                        backgroundColor = Color.White,
                        iconTint = Color(0xFF10B981)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shizuku Ready",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF065F46),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Input injection enabled",
                            color = Color(0xFF047857),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        else -> {
            val icon: ImageVector?
            val color: Color
            val title: String
            val description: String
            val actionLabel: String?
            val action: (() -> Unit)?

            when (status) {
                MainViewModel.ShizukuStatus.CHECKING -> {
                    icon = null; color = Color.Gray; title = "Checking Shizuku..."
                    description = ""; actionLabel = null; action = null
                }
                MainViewModel.ShizukuStatus.NOT_INSTALLED -> {
                    icon = Icons.Default.Warning; color = Color(0xFFF44336)
                    title = "Shizuku Not Installed"
                    description = "Install Shizuku from Play Store to enable mouse/keyboard input."
                    actionLabel = "Install Shizuku"
                    action = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, 
                            Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                    }
                }
                MainViewModel.ShizukuStatus.NOT_RUNNING -> {
                    icon = Icons.Default.Warning; color = Color(0xFFFF9800)
                    title = "Shizuku Not Running"
                    description = "Open Shizuku app and start it via Wireless Debugging (Android 11+) or ADB."
                    actionLabel = "Open Shizuku"
                    action = {
                        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                            context.startActivity(it)
                        }
                    }
                }
                MainViewModel.ShizukuStatus.PERMISSION_REQUIRED -> {
                    icon = Icons.Default.Warning; color = Color(0xFFFF9800)
                    title = "Permission Required"
                    description = "Grant Input-Leaf permission to use Shizuku for input injection."
                    actionLabel = "Grant Permission"; action = onRequestPermission
                }
                else -> {
                    icon = null; color = Color.Gray; title = "Unknown"
                    description = ""; actionLabel = null; action = null
                }
            }
            
            GradientCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                backgroundColor = Color.White,
                cornerRadius = 24.dp,
                elevation = 1.dp,
                padding = 20.dp
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            CircularAvatar(
                                icon = icon,
                                size = 40.dp,
                                iconSize = 24.dp,
                                backgroundColor = color.copy(alpha = 0.1f),
                                iconTint = color
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = description,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (actionLabel != null && action != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = action) {
                            Text(actionLabel, color = Purple600)
                        }
                    }
                }
            }
        }
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
        backgroundColor = Color.White,
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
                    background = if (isConnected) Gradients.Primary else null,
                    backgroundColor = Color(0xFFF5F5F5),
                    iconTint = if (isConnected) Color.White else TextTertiary,
                    elevation = if (isConnected) 4.dp else 0.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusLabel,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnected) Purple600 else TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (serverName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = serverName,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Screen: $screenName",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
