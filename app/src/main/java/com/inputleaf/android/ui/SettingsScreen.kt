package com.inputleaf.android.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.inputleaf.android.R
import com.inputleaf.android.network.ClientCertificateSummary
import com.inputleaf.android.network.ConnectionTransportPolicy
import com.inputleaf.android.network.TlsFingerprintManager
import com.inputleaf.android.ui.components.CircularAvatar
import com.inputleaf.android.ui.components.ConnectedChoiceRow
import com.inputleaf.android.ui.components.SectionHeader
import com.inputleaf.android.ui.components.SettingsGroup
import com.inputleaf.android.ui.components.SettingsRow
import com.inputleaf.android.ui.components.SettingsSwitchRow
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
    cursorStyle: String,
    shizukuAvailable: Boolean,
    rootGranted: Boolean,
    rootUsable: Boolean,
    accessibilityAvailable: Boolean,
    canDrawOverlays: Boolean,
    fingerprints: Map<String, String>,
    clientCertificateSummary: ClientCertificateSummary?,
    onScreenNameChange: (String) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onShowCursorChange: (Boolean) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onInputMethodChange: (String) -> Unit,
    onConnectionTransportPolicyChange: (ConnectionTransportPolicy) -> Unit,
    onCursorStyleChange: (String) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onDeleteFingerprint: (String) -> Unit,
    onImportClientCertificate: () -> Unit,
    onRegenerateClientCertificate: () -> Unit,
    isCheckingUpdate: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cursorAvailable = canDrawOverlays || accessibilityAvailable
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.4.3"
        } catch (_: Exception) {
            "1.4.3"
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
    var showLocalFingerprint by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
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
                            subtitle = if (cursorStyle == "leaf") "Input Leaf custom" else "Android default",
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
                    SettingsRow(
                        icon = Icons.Rounded.Lock,
                        title = "Trusted servers",
                        subtitle = "${fingerprints.size} server${if (fingerprints.size != 1) "s" else ""}",
                        shapes = shapes,
                    )
                }
                fingerprints.forEach { (ip, fp) ->
                    row { shapes ->
                        SegmentedListItem(
                            shapes = shapes,
                            supportingContent = { Text(fp.take(16) + "...") },
                            trailingContent = {
                                IconButton(onClick = { onDeleteFingerprint(ip) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove")
                                }
                            },
                        ) {
                            Text(ip)
                        }
                    }
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
                    "Deskflow will ask you to trust this phone again. Only regenerate if you " +
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
                Choice(ConnectionTransportPolicy.TLS_ONLY, "TLS only", "Require an encrypted Deskflow connection"),
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

    if (showCursorStyleDialog) {
        AlertDialog(
            onDismissRequest = { showCursorStyleDialog = false },
            title = { Text("Select Cursor Style") },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CursorStyleCard(
                        label = "Default",
                        painter = painterResource(id = R.drawable.ic_cursor_aosp),
                        mirrored = false,
                        selected = cursorStyle == "default",
                        onClick = {
                            onCursorStyleChange("default")
                            showCursorStyleDialog = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    CursorStyleCard(
                        label = "Input Leaf",
                        painter = painterResource(id = R.drawable.cursor),
                        mirrored = true,
                        selected = cursorStyle == "leaf",
                        onClick = {
                            onCursorStyleChange("leaf")
                            showCursorStyleDialog = false
                        },
                        modifier = Modifier.weight(1f),
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
    painter: Painter,
    mirrored: Boolean,
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
                    Image(
                        painter = painter,
                        contentDescription = "$label Cursor",
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer(scaleX = if (mirrored) -1f else 1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
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
