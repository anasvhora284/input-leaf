package com.inputleaf.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.ui.components.FeatureToggleCard
import com.inputleaf.android.ui.components.GradientCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    connectionState: ConnectionState,
    discoveredServers: List<ServerInfo>,
    isScanning: Boolean,
    screenName: String,
    shizukuStatus: ShizukuStatus,
    accessibilityAvailable: Boolean,
    mouseEnabled: Boolean,
    keyboardEnabled: Boolean,
    favoriteServers: Set<String>,
    onScan: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onDisconnect: () -> Unit,
    onAddManual: (String) -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onRequestAccessibilityService: () -> Unit,
    onScreenNameChange: (String) -> Unit,
    onToggleMouse: (Boolean) -> Unit,
    onToggleKeyboard: (Boolean) -> Unit,
) {
    var showEditNameDialog by remember { mutableStateOf(false) }
    var tempName by remember(screenName) { mutableStateOf(screenName) }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = {
                Text(
                    text = "Rename Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Device Screen Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            onScreenNameChange(tempName)
                            showEditNameDialog = false
                        }
                    },
                    enabled = tempName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Input Leaf",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
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
                    FeatureToggleCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Mouse,
                        label = "Mouse",
                        enabled = mouseEnabled,
                        onToggle = { onToggleMouse(!mouseEnabled) },
                    )
                    FeatureToggleCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Keyboard,
                        label = "Keyboard",
                        enabled = keyboardEnabled,
                        onToggle = { onToggleKeyboard(!keyboardEnabled) },
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

            // Setup options section if neither Shizuku nor Accessibility is enabled/ready
            val isInputInjectionReady = shizukuStatus == ShizukuStatus.READY || accessibilityAvailable
            val isSessionActive = connectionState is ConnectionState.Active

            if (!isInputInjectionReady && !isSessionActive) {
                item {
                    Text(
                        text = "Setup Required",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Shizuku setup card
                item {
                    GradientCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onRequestShizukuPermission() },
                        backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        cornerRadius = 20.dp,
                        padding = 16.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FlashOn,
                                contentDescription = "Shizuku",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Shizuku Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Recommended for precise mouse injection. Requires Shizuku background service.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Accessibility setup card
                item {
                    GradientCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onRequestAccessibilityService() },
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        cornerRadius = 20.dp,
                        padding = 16.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Accessibility,
                                contentDescription = "Accessibility",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Accessibility Mode",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Easy rootless touch & keyboard simulation. Works out of the box on any device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Styled device identity section
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                var marketingName by remember { androidx.compose.runtime.mutableStateOf(com.inputleaf.android.util.DeviceIdentity.getMarketingName()) }
                
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    com.inputleaf.android.util.DeviceIdentity.requestMarketingName(context) { name ->
                        marketingName = name
                    }
                }
                val manufacturer = remember { com.inputleaf.android.util.DeviceIdentity.getManufacturerName() }
                val internalCode = remember { com.inputleaf.android.util.DeviceIdentity.getInternalModelCode() }
                val androidVersion = remember { com.inputleaf.android.util.DeviceIdentity.getAndroidVersion() }
                val brandLogoRes = remember { com.inputleaf.android.util.DeviceIdentity.getBrandLogoRes() }
                val brandColor = remember { com.inputleaf.android.util.DeviceIdentity.getBrandColor() }

                GradientCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    backgroundColor = brandColor.copy(alpha = 0.08f),
                    cornerRadius = 24.dp,
                    elevation = 0.dp,
                    padding = 0.dp
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Brand watermark — large, subtle background logo
                        Icon(
                            painter = painterResource(id = brandLogoRes),
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .align(Alignment.CenterEnd)
                                .offset(x = 16.dp, y = 0.dp),
                            tint = brandColor.copy(alpha = 0.06f)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Stylized device mockup
                                DeviceVisualRepresentation(
                                    manufacturer = manufacturer,
                                    brandColor = brandColor,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    // Marketing name + brand logo inline
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = marketingName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Icon(
                                            painter = painterResource(id = brandLogoRes),
                                            contentDescription = manufacturer,
                                            modifier = Modifier.size(16.dp),
                                            tint = brandColor.copy(alpha = 0.8f)
                                        )
                                    }

                                    // Internal model code (subtle)
                                    if (internalCode != marketingName && !marketingName.contains(internalCode, ignoreCase = true)) {
                                        Text(
                                            text = internalCode,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            letterSpacing = 0.5.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Screen name row
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Screen: ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = screenName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showEditNameDialog = true },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit Name",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Info pills row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Android version pill
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = brandColor.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = androidVersion,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = brandColor.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                // Manufacturer pill
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = manufacturer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
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
private fun DeviceVisualRepresentation(
    manufacturer: String,
    brandColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val accentColor = brandColor.copy(alpha = 0.8f)
    
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 80.dp)
            .background(
                color = brandColor.copy(alpha = 0.08f),
                shape = shape
            )
            .border(
                width = 1.5.dp,
                color = brandColor.copy(alpha = 0.35f),
                shape = shape
            )
    ) {
        when (manufacturer.lowercase()) {
            "google" -> {
                // Pixel visor camera bar
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(9.dp)
                            .background(accentColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(4.dp).background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(4.dp).background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
            }
            "samsung" -> {
                // Samsung vertical triple lenses top-left
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp, start = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    repeat(3) {
                        Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                    }
                }
            }
            "oneplus" -> {
                // OnePlus circular center camera module
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 14.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(accentColor, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape))
                    }
                }
            }
            "xiaomi" -> {
                // Xiaomi large square camera module top-left
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 6.dp, start = 5.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(modifier = Modifier.size(4.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                                Box(modifier = Modifier.size(4.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(modifier = Modifier.size(4.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                                Box(modifier = Modifier.size(4.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            }
                        }
                    }
                }
            }
            "redmi", "poco" -> {
                // Redmi/Poco vertical camera strip left side
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp, start = 6.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 10.dp, height = 28.dp)
                            .background(accentColor.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(vertical = 3.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
            }
            "realme" -> {
                // Realme vertical dual lens left
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 10.dp, start = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(modifier = Modifier.size(6.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                    Box(modifier = Modifier.size(6.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                    Box(modifier = Modifier.size(3.dp).background(accentColor.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape))
                }
            }
            "vivo", "iqoo" -> {
                // Vivo/iQOO horizontal top camera bar
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 30.dp, height = 10.dp)
                            .background(accentColor.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
            }
            "oppo" -> {
                // Oppo rectangle camera module top-left
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 6.dp, start = 5.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 14.dp, height = 20.dp)
                            .background(accentColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(vertical = 3.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
            }
            "motorola" -> {
                // Motorola centered circle module with M notch
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(accentColor.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(7.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                    }
                }
            }
            "nokia" -> {
                // Nokia circular Zeiss style centered
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                        Box(modifier = Modifier.size(8.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                        Box(modifier = Modifier.size(4.dp).background(accentColor.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape))
                    }
                }
            }
            "nothing" -> {
                // Nothing Phone transparent-inspired dot grid
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(4.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(4.dp).background(accentColor.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(4.dp).background(accentColor.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(4.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
            }
            "tecno", "infinix" -> {
                // Tecno/Infinix vertical camera island
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 6.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 26.dp)
                            .background(accentColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(vertical = 3.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
            }
            "asus" -> {
                // Asus ROG style angular camera module
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 12.dp)
                            .background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
            }
            "honor" -> {
                // Honor circular camera module
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(accentColor.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                            Box(modifier = Modifier.size(5.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
            }
            else -> {
                // Generic: simple top-left camera bump
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 7.dp, start = 6.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 10.dp, height = 14.dp)
                            .background(accentColor, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
        
        // Inner screen bezel frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.5.dp)
                .border(
                    width = 0.8.dp,
                    color = brandColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                )
        )
    }
}
