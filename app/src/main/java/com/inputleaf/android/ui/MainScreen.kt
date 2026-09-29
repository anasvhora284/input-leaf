package com.inputleaf.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import com.inputleaf.android.R
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.ui.components.CircularAvatar
import com.inputleaf.android.ui.components.FeatureToggleCard
import com.inputleaf.android.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    connectionState: ConnectionState,
    discoveredServers: List<ServerInfo>,
    isScanning: Boolean,
    screenName: String,
    shizukuStatus: ShizukuStatus,
    rootStatus: RootStatus,
    accessibilityAvailable: Boolean,
    mouseEnabled: Boolean,
    keyboardEnabled: Boolean,
    favoriteServers: Set<String>,
    onScan: () -> Unit,
    onConnect: (ServerInfo) -> Unit,
    onDisconnect: () -> Unit,
    onAddManual: (String) -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onRequestRootAccess: () -> Unit,
    onRequestAccessibilityService: () -> Unit,
    onScreenNameChange: (String) -> Unit,
    onToggleMouse: (Boolean) -> Unit,
    onToggleKeyboard: (Boolean) -> Unit,
    pendingConnectIp: String? = null,
) {
    var showEditNameDialog by remember { mutableStateOf(false) }
    var tempName by remember(screenName) { mutableStateOf(screenName) }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            title = { Text("Rename Device") },
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
                    Image(
                        painter = painterResource(id = R.drawable.ic_splash_text),
                        contentDescription = "Input Leaf",
                        modifier = Modifier.height(32.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
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
                    SectionHeader("Quick Connect")
                    val connectingIp = connectingServerIp(connectionState, pendingConnectIp)
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        favorites.forEachIndexed { index, server ->
                            val isConnected = when (connectionState) {
                                is ConnectionState.Idle -> connectionState.serverIp == server.ip
                                is ConnectionState.Active -> connectionState.serverIp == server.ip
                                else -> false
                            }
                            ServerListItem(
                                server = server,
                                isConnected = isConnected,
                                onServerClick = onConnect,
                                shapes = ListItemDefaults.segmentedShapes(index, favorites.size),
                                isConnecting = connectingIp == server.ip,
                                enabled = connectingIp == null,
                            )
                        }
                    }
                }
            }

            // Setup options section if neither Shizuku nor Accessibility is enabled/ready
            // AVAILABLE only means an su binary exists; nothing can inject until it is
            // GRANTED, so treating it as ready hides the card the user needs to tap.
            val rootUsable = rootStatus == RootStatus.GRANTED
            val isInputInjectionReady =
                shizukuStatus == ShizukuStatus.READY || rootUsable || accessibilityAvailable
            val isSessionActive = connectionState is ConnectionState.Active

            if (!isInputInjectionReady && !isSessionActive) {
                item { SectionHeader("Setup Required") }

                item {
                    SetupActionCard(
                        icon = Icons.Rounded.FlashOn,
                        title = "Enable Shizuku or grant root",
                        body = "Recommended for the system cursor and HID keyboard. Shizuku or su — you do not need both.",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        onClick = {
                            if (rootStatus == RootStatus.DENIED || rootStatus == RootStatus.AVAILABLE) {
                                onRequestRootAccess()
                            } else {
                                onRequestShizukuPermission()
                            }
                        },
                    )
                }

                item {
                    SetupActionCard(
                        icon = Icons.Rounded.Accessibility,
                        title = "Enable Accessibility Mode",
                        body = "Easy rootless touch & keyboard simulation. Works out of the box on any device.",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onRequestAccessibilityService,
                    )
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

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
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
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Icon(
                                            painter = painterResource(id = brandLogoRes),
                                            contentDescription = manufacturer,
                                            modifier = Modifier.size(16.dp),
                                            tint = brandColor
                                        )
                                    }

                                    if (internalCode != marketingName && !marketingName.contains(internalCode, ignoreCase = true)) {
                                        Text(
                                            text = internalCode,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Screen: ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = screenName,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }

                                FilledTonalIconButton(onClick = { showEditNameDialog = true }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit Name")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InfoPill(
                                    text = androidVersion,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                )
                                InfoPill(
                                    text = manufacturer,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectionStatusCard(
    state: ConnectionState,
    screenName: String,
    onDisconnect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val (statusLabel, statusIcon, roles, serverInfo) = when (state) {
        is ConnectionState.Active -> StatusInfo(
            "CONNECTED", Icons.Rounded.CheckCircle,
            StatusRoles(colors.primaryContainer, colors.onPrimaryContainer, colors.primary, colors.onPrimary),
            "${state.serverName} • ${state.serverIp}"
        )
        is ConnectionState.Idle -> StatusInfo(
            "IDLE", Icons.Rounded.Pause,
            StatusRoles(colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary, colors.onSecondary),
            "${state.serverName} • ${state.serverIp}"
        )
        is ConnectionState.Connecting -> StatusInfo(
            "CONNECTING...", Icons.Rounded.Sync,
            StatusRoles(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary, colors.onTertiary),
            state.serverIp
        )
        is ConnectionState.Handshaking -> StatusInfo(
            "HANDSHAKING...", Icons.Rounded.Sync,
            StatusRoles(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary, colors.onTertiary),
            state.serverIp
        )
        is ConnectionState.Disconnected -> StatusInfo(
            "DISCONNECTED", Icons.Rounded.LinkOff,
            StatusRoles(colors.errorContainer, colors.onErrorContainer, colors.error, colors.onError),
            "No server connected"
        )
    }

    val colorSpec = MaterialTheme.motionScheme.slowEffectsSpec<Color>()
    val containerColor by animateColorAsState(roles.container, colorSpec, label = "status_container")
    val contentColor by animateColorAsState(roles.onContainer, colorSpec, label = "status_content")
    val accentColor by animateColorAsState(roles.accent, colorSpec, label = "status_accent")

    val isConnected = state is ConnectionState.Active || state is ConnectionState.Idle
    val isWorking = state is ConnectionState.Connecting || state is ConnectionState.Handshaking

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isWorking) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    containerColor = accentColor,
                    indicatorColor = roles.onAccent,
                )
            } else {
                CircularAvatar(
                    icon = statusIcon,
                    size = 48.dp,
                    iconSize = 26.dp,
                    backgroundColor = accentColor,
                    iconTint = roles.onAccent,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = statusLabel, style = MaterialTheme.typography.labelLargeEmphasized)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = serverInfo, style = MaterialTheme.typography.bodySmall)
            }

            if (isConnected) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = roles.onAccent),
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}

private data class StatusRoles(val container: Color, val onContainer: Color, val accent: Color, val onAccent: Color)

private data class StatusInfo(
    val label: String,
    val icon: ImageVector,
    val roles: StatusRoles,
    val serverInfo: String
)

@Composable
private fun SetupActionCard(
    icon: ImageVector,
    title: String,
    body: String,
    containerColor: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColorFor(containerColor),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(text = body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, containerColor: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = containerColor) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

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
                            Box(modifier = Modifier.size(4.dp).background(accentColor, androidx.compose.foundation.shape.CircleShape))
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
