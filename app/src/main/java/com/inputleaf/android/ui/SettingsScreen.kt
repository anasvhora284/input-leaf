package com.inputleaf.android.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.inputleaf.android.update.InstallSource
import com.inputleaf.android.update.UpdateService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputleaf.android.ui.components.CircularAvatar
import com.inputleaf.android.ui.components.GradientCard
import com.inputleaf.android.ui.components.SectionHeader
import com.inputleaf.android.ui.components.SettingsRow
import com.inputleaf.android.ui.components.ThemeModeOption
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import com.inputleaf.android.network.ClientCertificateSummary
import com.inputleaf.android.network.ConnectionTransportPolicy
import com.inputleaf.android.network.TlsFingerprintManager

@OptIn(ExperimentalMaterial3Api::class)
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
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.4.2"
        } catch (_: Exception) {
            "1.4.2"
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

    var editingName by remember(screenName) { mutableStateOf(screenName) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showInputMethodDialog by remember { mutableStateOf(false) }
    var showTransportPolicyDialog by remember { mutableStateOf(false) }
    var showCursorStyleDialog by remember { mutableStateOf(false) }
    var showLocalFingerprint by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var showAuthorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 0.5.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Connection Section
            SectionHeader("CONNECTION")
            
            GradientCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface,
                cornerRadius = 24.dp,
                padding = 0.dp
            ) {
                Column {
                    // Screen Name
                    SettingsRow(
                        icon = Icons.Rounded.Phone,
                        title = "Screen name",
                        subtitle = screenName,
                        onClick = { showEditNameDialog = true }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                    // Auto-connect
                    SettingsRow(
                        icon = Icons.Rounded.Build,
                        title = "Auto-connect on launch",
                        trailingContent = {
                            Switch(
                                checked = autoConnect,
                                onCheckedChange = onAutoConnectChange,
                            )
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Lock,
                        title = "Connection security",
                        subtitle = when (connectionTransportPolicy) {
                            ConnectionTransportPolicy.AUTO -> "Auto (recommended)"
                            ConnectionTransportPolicy.TLS_ONLY -> "TLS only"
                            ConnectionTransportPolicy.PLAIN_ONLY -> "Plain only"
                        },
                        onClick = { showTransportPolicyDialog = true }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                    // Input Method
                    SettingsRow(
                        icon = Icons.Rounded.Keyboard,
                        title = "Input method",
                        subtitle = when (inputMethod) {
                            "shizuku" -> "Shizuku (ADB-level injection)"
                            "accessibility" -> "Accessibility Service (no extra app)"
                            else -> "Auto (Recommended)"
                        },
                        onClick = { showInputMethodDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display Section
            SectionHeader("DISPLAY")
            
            GradientCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface,
                cornerRadius = 24.dp,
                padding = 0.dp
            ) {
                Column {
                    if (!cursorAvailable) {
                        // Permission warning
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularAvatar(
                                icon = Icons.Rounded.Warning,
                                size = 40.dp,
                                iconSize = 24.dp,
                                backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                iconTint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Overlay or Accessibility required",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Grant overlay permission or enable Accessibility Service to show the cursor",
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        TextButton(
                            onClick = onRequestOverlayPermission,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Grant Permission", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    // Show Cursor toggle
                    SettingsRow(
                        icon = Icons.Rounded.Info,
                        title = "Show cursor overlay",
                        subtitle = when {
                            canDrawOverlays || accessibilityAvailable -> "Display cursor when active"
                            else -> "Needs overlay permission or Accessibility"
                        },
                        trailingContent = {
                            Switch(
                                checked = showCursor,
                                onCheckedChange = onShowCursorChange,
                                enabled = cursorAvailable,
                            )
                        }
                    )
                    if (showCursor && cursorAvailable) {
                        SettingsRow(
                            icon = Icons.Rounded.Edit,
                            title = "Cursor style",
                            subtitle = if (cursorStyle == "leaf") "Input Leaf custom" else "Android default",
                            onClick = { showCursorStyleDialog = true }
                        )
                        if (shizukuAvailable && (inputMethod == "auto" || inputMethod == "shizuku")) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Cursor in notification panel",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Enable Accessibility Service for cursor visibility over the notification shade.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else if (!accessibilityAvailable) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        try {
                                            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Unable to open Accessibility Settings", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Cursor in notification panel",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Enable Accessibility Service for cursor visibility over the notification shade.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    // Theme setting
                    SettingsRow(
                        icon = Icons.Rounded.Settings,
                        title = "Theme",
                        subtitle = when (themeMode) {
                            "LIGHT" -> "Light"
                            "DARK" -> "Dark"
                            else -> "System default"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("SECURITY")

            GradientCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface,
                cornerRadius = 24.dp,
                padding = 0.dp
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Rounded.Badge,
                        title = "This device's fingerprint",
                        subtitle = clientCertificateSummary?.let { summary ->
                            TlsFingerprintManager.formatFingerprint(summary.fingerprint.take(16)) +
                                "…"
                        } ?: "Creating a certificate for this device…",
                        onClick = { if (clientCertificateSummary != null) showLocalFingerprint = true },
                        trailingContent = {
                            IconButton(
                                onClick = { showRegenerateConfirm = true },
                                enabled = clientCertificateSummary != null,
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Regenerate certificate",
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                    // Trusted servers header
                    SettingsRow(
                        icon = Icons.Rounded.Lock,
                        title = "Trusted servers",
                        subtitle = "${fingerprints.size} server${if (fingerprints.size != 1) "s" else ""}",
                    )
                    // Server entries
                    fingerprints.entries.forEachIndexed { index, (ip, fp) ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 72.dp)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ip,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = fp.take(16) + "...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { onDeleteFingerprint(ip) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // About & Community Section
            SectionHeader("ABOUT & COMMUNITY")

            GradientCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface,
                cornerRadius = 24.dp,
                padding = 0.dp
            ) {
                Column {
                    // App Info Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Image(
                            painter = painterResource(id = com.inputleaf.android.R.drawable.ic_splash_logo),
                            contentDescription = "Input Leaf Logo",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Input Leaf",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "v$versionName",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Open-source Android client for Input Leap",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )

                    // Check for updates
                    SettingsRow(
                        icon = Icons.Rounded.Sync,
                        title = "Check for updates",
                        subtitle = if (isCheckingUpdate) "Checking latest release…" else "v$versionName · $installSourceLabel",
                        onClick = onCheckForUpdates,
                        trailingContent = if (isCheckingUpdate) {
                            {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )

                    // GitHub Repository
                    SettingsRow(
                        painter = painterResource(id = com.inputleaf.android.R.drawable.ic_brand_github),
                        title = "GitHub Repository",
                        subtitle = "anasvhora284/input-leaf",
                        onClick = { openUrl(context, "https://github.com/anasvhora284/input-leaf") }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )

                    // Contributors
                    SettingsRow(
                        icon = Icons.Rounded.Group,
                        title = "Contributors",
                        subtitle = "View contributors on GitHub",
                        onClick = { openUrl(context, "https://github.com/anasvhora284/input-leaf/graphs/contributors") }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 72.dp)
                    )

                    // Report an Issue
                    SettingsRow(
                        icon = Icons.Rounded.BugReport,
                        title = "Report an Issue",
                        subtitle = "GitHub issues & feature requests",
                        onClick = { openUrl(context, "https://github.com/anasvhora284/input-leaf/issues") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Author Info Section
            SectionHeader("DEVELOPER")

            GradientCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface,
                cornerRadius = 24.dp,
                padding = 0.dp
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Rounded.Person,
                        title = "Author Info",
                        subtitle = "Anas Vhora · Connect & Socials",
                        onClick = { showAuthorDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Made with love footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Made with ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = "Love",
                        tint = Color(0xFFE11D48),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = " by",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Anas Vhora",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { openUrl(context, "https://github.com/anasvhora284") }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = " & ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "input-leaf contributors",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { openUrl(context, "https://github.com/anasvhora284/input-leaf/graphs/contributors") }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
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

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    ThemeModeOption(
                        text = "System default",
                        selected = themeMode == "SYSTEM",
                        onClick = {
                            onThemeModeChange("SYSTEM")
                            showThemeDialog = false
                        }
                    )
                    ThemeModeOption(
                        text = "Light",
                        selected = themeMode == "LIGHT",
                        onClick = {
                            onThemeModeChange("LIGHT")
                            showThemeDialog = false
                        }
                    )
                    ThemeModeOption(
                        text = "Dark",
                        selected = themeMode == "DARK",
                        onClick = {
                            onThemeModeChange("DARK")
                            showThemeDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTransportPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showTransportPolicyDialog = false },
            title = { Text("Connection Security") },
            text = {
                Column {
                    SettingsChoiceOption(
                        text = "Auto (Recommended)",
                        selected = connectionTransportPolicy == ConnectionTransportPolicy.AUTO,
                        status = "Use the last working mode, with fallback",
                        statusColor = Color.Gray,
                        onClick = {
                            onConnectionTransportPolicyChange(ConnectionTransportPolicy.AUTO)
                            showTransportPolicyDialog = false
                        }
                    )
                    SettingsChoiceOption(
                        text = "TLS only",
                        selected = connectionTransportPolicy == ConnectionTransportPolicy.TLS_ONLY,
                        status = "Require an encrypted Deskflow connection",
                        statusColor = Color.Gray,
                        onClick = {
                            onConnectionTransportPolicyChange(ConnectionTransportPolicy.TLS_ONLY)
                            showTransportPolicyDialog = false
                        }
                    )
                    SettingsChoiceOption(
                        text = "Plain only",
                        selected = connectionTransportPolicy == ConnectionTransportPolicy.PLAIN_ONLY,
                        status = "Never attempt TLS",
                        statusColor = Color.Gray,
                        onClick = {
                            onConnectionTransportPolicyChange(ConnectionTransportPolicy.PLAIN_ONLY)
                            showTransportPolicyDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTransportPolicyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showInputMethodDialog) {
        AlertDialog(
            onDismissRequest = { showInputMethodDialog = false },
            title = { Text("Select Input Method") },
            text = {
                Column {
                    SettingsChoiceOption(
                        text = "Auto (Recommended)",
                        selected = inputMethod == "auto",
                        status = "Available",
                        statusColor = Color.Gray,
                        onClick = {
                            onInputMethodChange("auto")
                            showInputMethodDialog = false
                        }
                    )
                    SettingsChoiceOption(
                        text = "Shizuku",
                        selected = inputMethod == "shizuku",
                        status = if (shizukuAvailable) "Available" else "Not running",
                        statusColor = if (shizukuAvailable) Color(0xFF4CAF50) else Color.Red,
                        onClick = {
                            onInputMethodChange("shizuku")
                            showInputMethodDialog = false
                        }
                    )
                    SettingsChoiceOption(
                        text = "Accessibility Service",
                        selected = inputMethod == "accessibility",
                        status = if (accessibilityAvailable) "Enabled" else "Disabled",
                        statusColor = if (accessibilityAvailable) Color(0xFF4CAF50) else Color.Red,
                        onClick = {
                            onInputMethodChange("accessibility")
                            showInputMethodDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInputMethodDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditNameDialog) {
        var newName by remember { mutableStateOf(screenName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
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
            title = {
                Text(
                    text = "Select Cursor Style",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Option 1: Android Default
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                            .clickable {
                                onCursorStyleChange("default")
                                showCursorStyleDialog = false
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (cursorStyle == "default")
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = if (cursorStyle == "default")
                            BorderStroke(2.2.dp, MaterialTheme.colorScheme.primary)
                        else
                            null
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        color = Color.LightGray.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.inputleaf.android.R.drawable.ic_cursor_aosp),
                                    contentDescription = "Default Cursor",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .graphicsLayer(scaleX = 1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Default",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (cursorStyle == "default")
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option 2: Input Leaf Custom
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                            .clickable {
                                onCursorStyleChange("leaf")
                                showCursorStyleDialog = false
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (cursorStyle == "leaf")
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = if (cursorStyle == "leaf")
                            BorderStroke(2.2.dp, MaterialTheme.colorScheme.primary)
                        else
                            null
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF10B981).copy(alpha = 0.15f),
                                                Color(0xFF34D399).copy(alpha = 0.15f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.inputleaf.android.R.drawable.cursor),
                                    contentDescription = "Leaf Cursor",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .graphicsLayer(scaleX = -1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Input Leaf",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (cursorStyle == "leaf")
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCursorStyleDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showAuthorDialog) {
        AlertDialog(
            onDismissRequest = { showAuthorDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = "Author",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Anas Vhora",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Developer & Maintainer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Personal Website
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { openUrl(context, "https://anasvhora.tech") },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = "Website",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Personal Website",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "anasvhora.tech",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // LinkedIn Profile
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { openUrl(context, "https://www.linkedin.com/in/anas-vhora-28455a1a1/") },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = com.inputleaf.android.R.drawable.ic_brand_linkedin),
                                contentDescription = "LinkedIn",
                                tint = Color(0xFF0A66C2),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LinkedIn",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Anas Vhora",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // GitHub Profile
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { openUrl(context, "https://github.com/anasvhora284") },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = com.inputleaf.android.R.drawable.ic_brand_github),
                                contentDescription = "GitHub",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GitHub",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "anasvhora284",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuthorDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

@Composable
private fun SettingsChoiceOption(
    text: String,
    selected: Boolean,
    status: String,
    statusColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = status,
                color = statusColor,
                style = MaterialTheme.typography.bodySmall
            )
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
