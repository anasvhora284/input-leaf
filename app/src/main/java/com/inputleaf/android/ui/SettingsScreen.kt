package com.inputleaf.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import com.inputleaf.android.ui.components.MaterialToggleSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    screenName: String,
    autoConnect: Boolean,
    showCursor: Boolean,
    themeMode: String,
    canDrawOverlays: Boolean,
    fingerprints: Map<String, String>,
    onScreenNameChange: (String) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onShowCursorChange: (Boolean) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onDeleteFingerprint: (String) -> Unit,
    onBack: () -> Unit
) {
    var editingName by remember(screenName) { mutableStateOf(screenName) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
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
                        showDivider = false,
                        trailingContent = {
                            MaterialToggleSwitch(
                                checked = autoConnect,
                                onCheckedChange = onAutoConnectChange
                            )
                        }
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
                    if (!canDrawOverlays) {
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
                                    text = "Overlay permission required",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Required to show cursor on screen",
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
                        subtitle = if (canDrawOverlays) "Display cursor when active" else "Grant permission first",
                        showDivider = true,
                        trailingContent = {
                            MaterialToggleSwitch(
                                checked = showCursor,
                                onCheckedChange = onShowCursorChange,
                                enabled = canDrawOverlays
                            )
                        }
                    )
                    // Theme setting
                    SettingsRow(
                        icon = Icons.Rounded.Settings,
                        title = "Theme",
                        subtitle = when (themeMode) {
                            "LIGHT" -> "Light"
                            "DARK" -> "Dark"
                            else -> "System default"
                        },
                        showDivider = false,
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Section
            SectionHeader("SECURITY")
            
            GradientCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface,
                cornerRadius = 24.dp,
                padding = 0.dp
            ) {
                Column {
                    // Trusted servers header
                    SettingsRow(
                        icon = Icons.Rounded.Lock,
                        title = "Trusted servers",
                        subtitle = "${fingerprints.size} server${if (fingerprints.size != 1) "s" else ""}",
                        showDivider = fingerprints.isNotEmpty()
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
            
            Spacer(modifier = Modifier.height(16.dp))
        }
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
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp, top = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else Modifier
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularAvatar(
                icon = icon,
                size = 40.dp,
                iconSize = 22.dp,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (trailingContent != null) {
                trailingContent()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = "Navigate",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun ThemeModeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}
