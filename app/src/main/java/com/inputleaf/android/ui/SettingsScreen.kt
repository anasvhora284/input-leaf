package com.inputleaf.android.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.inputleaf.android.service.CursorColor
import com.inputleaf.android.service.CursorHotspot
import com.inputleaf.android.service.CursorLook
import com.inputleaf.android.service.CursorSize
import androidx.compose.ui.unit.dp
import com.inputleaf.android.R
import com.inputleaf.android.inject.PointerDevice
import com.inputleaf.android.inject.ScrollSpeed
import com.inputleaf.android.network.ClientCertificateSummary
import com.inputleaf.android.network.ConnectionTransportPolicy
import com.inputleaf.android.network.TlsFingerprintManager
import com.inputleaf.android.ui.components.CircularAvatar
import com.inputleaf.android.ui.components.ConnectedChoiceRow
import com.inputleaf.android.ui.components.SectionHeader
import com.inputleaf.android.ui.components.SettingsBadgeRow
import com.inputleaf.android.ui.components.SettingsGroup
import com.inputleaf.android.ui.components.SettingsRow
import com.inputleaf.android.ui.components.SettingsSwitchRow
import com.inputleaf.android.ui.components.TrustedServersSheet
import com.inputleaf.android.update.InstallSource
import com.inputleaf.android.update.UpdateService

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    screenName: String,
    autoConnect: Boolean,
    showCursor: Boolean,
    themeMode: String,
    inputMethod: String,
    connectionTransportPolicy: ConnectionTransportPolicy,
    cursorLook: CursorLook,
    pointerDevice: PointerDevice,
    scrollSpeed: Int = ScrollSpeed.DEFAULT,
    shizukuAvailable: Boolean,
    rootGranted: Boolean,
    rootUsable: Boolean,
    accessibilityAvailable: Boolean,
    canDrawOverlays: Boolean,
    fingerprints: Map<String, String>,
    serverNames: Map<String, String> = emptyMap(),
    clientCertificateSummary: ClientCertificateSummary?,
    onScreenNameChange: (String) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onShowCursorChange: (Boolean) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onInputMethodChange: (String) -> Unit,
    onConnectionTransportPolicyChange: (ConnectionTransportPolicy) -> Unit,
    onCursorLookChange: (CursorLook) -> Unit,
    onPointerDeviceChange: (PointerDevice) -> Unit,
    onScrollSpeedChange: (Int) -> Unit = {},
    onRequestOverlayPermission: () -> Unit,
    onDeleteFingerprint: (String) -> Unit,
    onImportClientCertificate: () -> Unit,
    onRegenerateClientCertificate: () -> Unit,
    isCheckingUpdate: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    receivePrereleases: Boolean = false,
    onReceivePrereleasesChange: (Boolean) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cursorAvailable = canDrawOverlays || accessibilityAvailable
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.4.5"
        } catch (_: Exception) {
            "1.4.5"
        }
    }
    val installSource = remember(context) { UpdateService.getInstallSource(context) }
    val installSourceLabel = remember(installSource) {
        when (installSource) {
            InstallSource.FDROID -> "Installed via F-Droid"
            InstallSource.PLAY_STORE -> "Installed via Play Store"
            InstallSource.GITHUB -> "Installed via GitHub / Direct APK"
        }
    }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showInputMethodDialog by remember { mutableStateOf(false) }
    var showTransportPolicyDialog by remember { mutableStateOf(false) }
    var showCursorStyleDialog by remember { mutableStateOf(false) }
    var showScrollSpeedDialog by remember { mutableStateOf(false) }
    var showLocalFingerprint by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var showTrustedServersSheet by remember { mutableStateOf(false) }
    var showAuthorDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("Connection")
            SettingsGroup {
                row { shapes ->
                    SettingsRow(
                        icon = Icons.Rounded.Phone,
                        title = "Screen name",
                        subtitle = screenName,
                        shapes = shapes,
                        onClick = { showEditNameDialog = true }
                    )
                }
                row { shapes ->
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Build,
                        title = "Auto-connect on launch",
                        checked = autoConnect,
                        onCheckedChange = onAutoConnectChange,
                        shapes = shapes,
                    )
                }
                row { shapes ->
                    SettingsRow(
                        icon = Icons.Rounded.Lock,
                        title = "Connection security",
                        subtitle = when (connectionTransportPolicy) {
                            ConnectionTransportPolicy.AUTO -> "Auto (recommended)"
                            ConnectionTransportPolicy.TLS_ONLY -> "TLS only"
                            ConnectionTransportPolicy.PLAIN_ONLY -> "Plain only"
                        },
                        shapes = shapes,
                        onClick = { showTransportPolicyDialog = true }
                    )
                }
                row { shapes ->
                    SettingsRow(
                        icon = Icons.Rounded.Keyboard,
                        title = "Input method",
                        subtitle = when (inputMethod) {
                            "shizuku" -> "Shizuku (ADB-level injection)"
                            "root" -> "Root (su HID injection)"
                            "accessibility" -> "Accessibility Service (overlay fallback)"
                            else -> "Auto (Shizuku, then root, then Accessibility)"
                        },
                        shapes = shapes,
                        onClick = { showInputMethodDialog = true }
                    )
                }
            }

            SectionHeader("Display")
            if (!cursorAvailable) {
                NoticeCard(
                    icon = Icons.Rounded.Warning,
                    title = "Overlay or Accessibility required",
                    body = "Grant overlay permission or enable Accessibility Service to show the cursor",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    action = {
                        TextButton(onClick = onRequestOverlayPermission) { Text("Grant Permission") }
                    },
                )
            }
            SettingsGroup {
                row { shapes ->
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Info,
                        title = "Show cursor overlay",
                        subtitle = if (cursorAvailable) "Display cursor when active" else "Needs overlay permission or Accessibility",
                        checked = showCursor,
                        onCheckedChange = onShowCursorChange,
                        enabled = cursorAvailable,
                        shapes = shapes,
                    )
                }
                if (showCursor && cursorAvailable) {
                    row { shapes ->
                        SettingsRow(
                            icon = Icons.Rounded.Edit,
                            title = "Cursor style",
                            subtitle = if (cursorLook.style == "leaf") {
                                "Input Leaf · ${cursorLook.size.label}"
                            } else {
                                "Android · ${cursorLook.fill.label} · ${cursorLook.size.label}"
                            },
                            shapes = shapes,
                            onClick = { showCursorStyleDialog = true }
                        )
                    }
                }
                row { shapes ->
                    SegmentedListItem(
                        shapes = shapes,
                        leadingContent = {
                            CircularAvatar(
                                icon = Icons.Rounded.Mouse,
                                size = 40.dp,
                                iconSize = 22.dp,
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            )
                        },
                        supportingContent = {
                            ConnectedChoiceRow(
                                options = listOf(
                                    PointerDevice.MOUSE to "Mouse",
                                    PointerDevice.STYLUS to "Stylus",
                                ),
                                selected = pointerDevice,
                                onSelect = onPointerDeviceChange,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        },
                    ) {
                        Text("Pointer device")
                    }
                }
                row { shapes ->
                    SettingsRow(
                        icon = Icons.Rounded.Mouse,
                        title = "Scroll speed",
                        subtitle = ScrollSpeed.label(scrollSpeed),
                        shapes = shapes,
                        onClick = { showScrollSpeedDialog = true },
                    )
                }
                row { shapes ->
                    SegmentedListItem(
                        shapes = shapes,
                        leadingContent = {
                            CircularAvatar(
                                icon = Icons.Rounded.Settings,
                                size = 40.dp,
                                iconSize = 22.dp,
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            )
                        },
                        supportingContent = {
                            ConnectedChoiceRow(
                                options = listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark"),
                                selected = themeMode,
                                onSelect = onThemeModeChange,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        },
                    ) {
                        Text("Theme")
                    }
                }
            }
            val usesPrivilegedInput = (shizukuAvailable || rootGranted) &&
                (inputMethod == "auto" || inputMethod == "shizuku" || inputMethod == "root")
            if (showCursor && cursorAvailable && (usesPrivilegedInput || !accessibilityAvailable)) {
                val onAccessibilityShortcut: () -> Unit = {
                    try {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "Unable to open Accessibility Settings", Toast.LENGTH_SHORT).show()
                    }
                }
                NoticeCard(
                    icon = Icons.Rounded.Info,
                    title = "Cursor in notification panel",
                    body = "Enable Accessibility Service for cursor visibility over the notification shade.",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = if (usesPrivilegedInput) null else onAccessibilityShortcut,
                )
            }

            SectionHeader("Security")
            SettingsGroup {
                row { shapes ->
                    SettingsRow(
                        icon = Icons.Rounded.Badge,
                        title = "This device's fingerprint",
                        subtitle = clientCertificateSummary?.let { summary ->
                            TlsFingerprintManager.formatFingerprint(summary.fingerprint.take(16)) + "…"
                        } ?: "Creating a certificate for this device…",
                        shapes = shapes,
                        onClick = { if (clientCertificateSummary != null) showLocalFingerprint = true },
                        trailingContent = {
                            IconButton(
                                onClick = { showRegenerateConfirm = true },
                                enabled = clientCertificateSummary != null,
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate certificate")
                            }
                        }
                    )
                }
                row { shapes ->
                    SettingsBadgeRow(
                        icon = Icons.Rounded.Lock,
                        title = "Trusted servers",
                        subtitle = if (fingerprints.isEmpty()) {
                            "No trusted servers"
                        } else {
                            "${fingerprints.size} trusted server${if (fingerprints.size != 1) "s" else ""}"
                        },
                        count = fingerprints.size.takeIf { it > 0 },
                        shapes = shapes,
                        onClick = { showTrustedServersSheet = true },
                    )
                }
            }

            SectionHeader("About & Community")
            SettingsGroup {
                row { shapes ->
                    SegmentedListItem(
                        shapes = shapes,
                        leadingContent = {
                            Image(
                                painter = painterResource(id = R.drawable.ic_splash_logo),
                                contentDescription = "Input Leaf Logo",
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )
                        },
                        supportingContent = { Text("Open-source Android client for Input Leap") },
                        trailingContent = {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = "v$versionName",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        },
                    ) {
                        Text("Input Leaf", style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
                row { shapes ->
                    SettingsRow(
                        icon = Icons.Rounded.Sync,
                        title = "Check for updates",
                        subtitle = if (isCheckingUpdate) "Checking latest release…" else "v$versionName · $installSourceLabel",
                        shapes = shapes,
                        onClick = onCheckForUpdates,
                        trailingContent = if (isCheckingUpdate) {
                            { LoadingIndicator(Modifier.size(32.dp)) }
                        } else null
                    )
                }
                // F-Droid builds are stable-only, so the choice would do nothing there.
                if (installSource != InstallSource.FDROID) {
                    row { shapes ->
                        SettingsSwitchRow(
                            icon = Icons.Rounded.Sync,
                            title = "Get pre-release updates",
                            subtitle = "Try new builds early. You'll still get the stable release when it ships.",
                            checked = receivePrereleases,
                            onCheckedChange = onReceivePrereleasesChange,
                            shapes = shapes,
                        )
                    }
                }
                row { shapes ->
                    SettingsRow(
                        painter = painterResource(id = R.drawable.ic_brand_github),
                        title = "GitHub Repository",
                        subtitle = "anasvhora284/input-leaf",
                        shapes = shapes,
                        onClick = { openUrl(context, "https://github.com/anasvhora284/input-leaf") }
                    )
                }
                row { shapes ->
                    SettingsRow(
                        icon = Icons.Rounded.Group,
                        title = "Contributors",
                        subtitle = "View contributors on GitHub",
                        shapes = shapes,
                        onClick = { openUrl(context, "https://github.com/anasvhora284/input-leaf/graphs/contributors") }
                    )
                }
                row { shapes ->
                    SettingsRow(
                        icon = Icons.Rounded.BugReport,
                        title = "Report an Issue",
                        subtitle = "GitHub issues & feature requests",
                        shapes = shapes,
                        onClick = { openUrl(context, "https://github.com/anasvhora284/input-leaf/issues") }
                    )
                }
            }

            SectionHeader("Developer")
            SettingsGroup {
                row { shapes ->
                    SettingsRow(
                        icon = Icons.Rounded.Person,
                        title = "Author Info",
                        subtitle = "Anas Vhora · Connect & Socials",
                        shapes = shapes,
                        onClick = { showAuthorDialog = true }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Made with ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = "Love",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = " by",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { openUrl(context, "https://github.com/anasvhora284") }) {
                        Text("Anas Vhora")
                    }
                    Text(
                        text = "&",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { openUrl(context, "https://github.com/anasvhora284/input-leaf/graphs/contributors") }) {
                        Text("input-leaf contributors")
                    }
                }
            }
        }
    }

    if (showLocalFingerprint) {
        clientCertificateSummary?.let { summary ->
            LocalFingerprintDialog(
                fingerprint = summary.fingerprint,
                onDismiss = { showLocalFingerprint = false },
                onRegenerate = {
                    showLocalFingerprint = false
                    showRegenerateConfirm = true
                },
                onImport = {
                    showLocalFingerprint = false
                    onImportClientCertificate()
                },
            )
        }
    }

    if (showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
            title = { Text("Regenerate certificate?") },
            text = {
                Text(
                    "Your server will ask you to trust this phone again. Only regenerate if you " +
                        "want a new identity."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegenerateConfirm = false
                        onRegenerateClientCertificate()
                    }
                ) {
                    Text("Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showTransportPolicyDialog) {
        ChoiceDialog(
            title = "Connection Security",
            icon = Icons.Rounded.Lock,
            options = listOf(
                Choice(ConnectionTransportPolicy.AUTO, "Auto (Recommended)", "Use the last working mode, with fallback"),
                Choice(ConnectionTransportPolicy.TLS_ONLY, "TLS only", "Require an encrypted connection"),
                Choice(ConnectionTransportPolicy.PLAIN_ONLY, "Plain only", "Never attempt TLS"),
            ),
            selected = connectionTransportPolicy,
            onSelect = {
                onConnectionTransportPolicyChange(it)
                showTransportPolicyDialog = false
            },
            onDismiss = { showTransportPolicyDialog = false },
        )
    }

    if (showInputMethodDialog) {
        ChoiceDialog(
            title = "Select Input Method",
            icon = Icons.Rounded.Keyboard,
            options = listOf(
                Choice("auto", "Auto (Recommended)", "Shizuku, then root, then Accessibility"),
                Choice("shizuku", "Shizuku", if (shizukuAvailable) "Available" else "Not running", available = shizukuAvailable),
                Choice(
                    "root",
                    "Root",
                    when {
                        rootGranted -> "Granted"
                        rootUsable -> "su available"
                        else -> "Not available"
                    },
                    available = rootUsable,
                ),
                Choice(
                    "accessibility",
                    "Accessibility Service",
                    if (accessibilityAvailable) "Enabled" else "Disabled",
                    available = accessibilityAvailable,
                ),
            ),
            selected = inputMethod,
            onSelect = {
                onInputMethodChange(it)
                showInputMethodDialog = false
            },
            onDismiss = { showInputMethodDialog = false },
        )
    }

    if (showEditNameDialog) {
        var newName by remember { mutableStateOf(screenName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            icon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
            title = { Text("Screen Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onScreenNameChange(newName)
                            showEditNameDialog = false
                        }
                    },
                    enabled = newName.isNotBlank()
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

    if (showScrollSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showScrollSpeedDialog = false },
            title = { Text("Scroll speed") },
            text = {
                Column {
                    Text(
                        "How far the mouse wheel scrolls on this device, for both directions.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    ScrollSpeed.OPTIONS.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onScrollSpeedChange(option)
                                    showScrollSpeedDialog = false
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = option == ScrollSpeed.fromStorage(scrollSpeed),
                                onClick = {
                                    onScrollSpeedChange(option)
                                    showScrollSpeedDialog = false
                                },
                            )
                            Text(ScrollSpeed.label(option))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScrollSpeedDialog = false }) { Text("Close") }
            },
        )
    }

    if (showCursorStyleDialog) {
        AlertDialog(
            onDismissRequest = { showCursorStyleDialog = false },
            title = { Text("Cursor") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CursorStyleCard(
                            label = "Android",
                            look = cursorLook.copy(style = "default"),
                            selected = cursorLook.style == "default",
                            onClick = { onCursorLookChange(cursorLook.copy(style = "default")) },
                            modifier = Modifier.weight(1f),
                        )
                        CursorStyleCard(
                            label = "Input Leaf",
                            look = cursorLook.copy(style = "leaf"),
                            selected = cursorLook.style == "leaf",
                            onClick = { onCursorLookChange(cursorLook.copy(style = "leaf")) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text("Size", style = MaterialTheme.typography.labelLarge)
                    ChipRow {
                        CursorSize.entries.forEach { size ->
                            FilterChip(
                                selected = cursorLook.size == size,
                                onClick = { onCursorLookChange(cursorLook.copy(size = size)) },
                                label = { Text(size.label) },
                            )
                        }
                    }
                    if (cursorLook.style == "default") {
                        Text("Colour", style = MaterialTheme.typography.labelLarge)
                        ChipRow {
                            CursorColor.entries.forEach { color ->
                                ColorSwatch(
                                    color = color,
                                    selected = cursorLook.fill == color,
                                    onClick = { onCursorLookChange(cursorLook.copy(fill = color)) },
                                )
                            }
                        }
                        Text("Outline", style = MaterialTheme.typography.labelLarge)
                        ChipRow {
                            CursorColor.OUTLINES.forEach { color ->
                                FilterChip(
                                    selected = cursorLook.outline == color,
                                    onClick = { onCursorLookChange(cursorLook.copy(outline = color)) },
                                    label = { Text(color.label) },
                                )
                            }
                        }
                    }
                    Text(
                        text = "Used in Accessibility mode. Shizuku and root use Android's own pointer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCursorStyleDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAuthorDialog) {
        AlertDialog(
            onDismissRequest = { showAuthorDialog = false },
            icon = {
                CircularAvatar(
                    icon = Icons.Rounded.Person,
                    size = 56.dp,
                    iconSize = 32.dp,
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Anas Vhora")
                    Text(
                        text = "Developer & Maintainer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                val links = listOf(
                    AuthorLink("Personal Website", "anasvhora.tech", "https://anasvhora.tech", icon = Icons.Rounded.Language),
                    AuthorLink(
                        "LinkedIn",
                        "Anas Vhora",
                        "https://www.linkedin.com/in/anas-vhora-28455a1a1/",
                        painter = painterResource(id = R.drawable.ic_brand_linkedin),
                    ),
                    AuthorLink(
                        "GitHub",
                        "anasvhora284",
                        "https://github.com/anasvhora284",
                        painter = painterResource(id = R.drawable.ic_brand_github),
                    ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    links.forEachIndexed { index, link ->
                        SegmentedListItem(
                            onClick = { openUrl(context, link.url) },
                            shapes = ListItemDefaults.segmentedShapes(index, links.size),
                            leadingContent = {
                                if (link.icon != null) {
                                    Icon(link.icon, contentDescription = link.title)
                                } else if (link.painter != null) {
                                    Icon(link.painter, contentDescription = link.title)
                                }
                            },
                            supportingContent = { Text(link.subtitle) },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                            },
                        ) {
                            Text(link.title)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuthorDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showTrustedServersSheet) {
        TrustedServersSheet(
            fingerprints = fingerprints,
            serverNames = serverNames,
            onDeleteFingerprint = onDeleteFingerprint,
            onDismiss = { showTrustedServersSheet = false },
        )
    }
}

private class AuthorLink(
    val title: String,
    val subtitle: String,
    val url: String,
    val icon: ImageVector? = null,
    val painter: Painter? = null,
)

private class Choice<T>(val value: T, val label: String, val status: String, val available: Boolean? = null)

@Composable
private fun <T> ChoiceDialog(
    title: String,
    icon: ImageVector,
    options: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                options.forEachIndexed { index, option ->
                    SegmentedListItem(
                        selected = option.value == selected,
                        onClick = { onSelect(option.value) },
                        shapes = ListItemDefaults.segmentedShapes(index, options.size),
                        leadingContent = {
                            RadioButton(selected = option.value == selected, onClick = null)
                        },
                        supportingContent = {
                            Text(
                                text = option.status,
                                color = when (option.available) {
                                    true -> MaterialTheme.colorScheme.primary
                                    false -> MaterialTheme.colorScheme.error
                                    null -> Color.Unspecified
                                },
                            )
                        },
                    ) {
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NoticeCard(
    icon: ImageVector,
    title: String,
    body: String,
    containerColor: Color,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = contentColorFor(containerColor),
    )
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
        }
        if (action != null) {
            Box(Modifier.padding(start = 8.dp, bottom = 8.dp)) { action() }
        }
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = colors,
            content = content,
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = colors,
            content = content,
        )
    }
}

@Composable
private fun CursorStyleCard(
    label: String,
    look: CursorLook,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CursorPreview(look = look, contentDescription = "$label cursor")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** The overlay cursor as drawn on screen, at its chosen size up to the preview box. */
@Composable
private fun CursorPreview(look: CursorLook, contentDescription: String) {
    val modifier = Modifier
        .size((36 * look.size.scale).coerceAtMost(56f).dp)
        .graphicsLayer(scaleX = if (look.hotspot.mirrored) -1f else 1f)
    if (look.hotspot == CursorHotspot.LEAF) {
        Image(painterResource(R.drawable.cursor), contentDescription, modifier)
    } else {
        Box(modifier) {
            Image(
                painterResource(R.drawable.ic_cursor_aosp_fill),
                contentDescription,
                Modifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(Color(look.fill.argb)),
            )
            Image(
                painterResource(R.drawable.ic_cursor_aosp_outline),
                null,
                Modifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(Color(look.outline.argb)),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun ColorSwatch(color: CursorColor, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = color.label; this.selected = selected },
        shape = CircleShape,
        color = Color(color.argb),
        border = BorderStroke(
            if (selected) 3.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {}
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
    }
}
