package com.inputleaf.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.inputleaf.android.ui.components.SectionHeader
import com.inputleaf.android.ui.components.SettingsRow
import com.inputleaf.android.ui.components.ThemeModeOption
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    screenName: String,
    autoConnect: Boolean,
    showCursor: Boolean,
    themeMode: String,
    inputMethod: String,
    cursorStyle: String,
    shizukuAvailable: Boolean,
    accessibilityAvailable: Boolean,
    canDrawOverlays: Boolean,
    fingerprints: Map<String, String>,
    onScreenNameChange: (String) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onShowCursorChange: (Boolean) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onInputMethodChange: (String) -> Unit,
    onCursorStyleChange: (String) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onDeleteFingerprint: (String) -> Unit,
    onBack: () -> Unit,
) {
    var editingName by remember(screenName) { mutableStateOf(screenName) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showInputMethodDialog by remember { mutableStateOf(false) }
    var showCursorStyleDialog by remember { mutableStateOf(false) }

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
                            MaterialToggleSwitch(
                                checked = autoConnect,
                                onCheckedChange = onAutoConnectChange
                            )
                        }
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
                        trailingContent = {
                            MaterialToggleSwitch(
                                checked = showCursor,
                                onCheckedChange = onShowCursorChange,
                                enabled = canDrawOverlays
                            )
                        }
                    )
                    if (showCursor && canDrawOverlays) {
                        SettingsRow(
                            icon = Icons.Rounded.Edit,
                            title = "Cursor style",
                            subtitle = if (cursorStyle == "leaf") "Input Leaf custom" else "Android default",
                            onClick = { showCursorStyleDialog = true }
                        )
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

    if (showInputMethodDialog) {
        AlertDialog(
            onDismissRequest = { showInputMethodDialog = false },
            title = { Text("Select Input Method") },
            text = {
                Column {
                    InputMethodOption(
                        text = "Auto (Recommended)",
                        selected = inputMethod == "auto",
                        status = "Available",
                        statusColor = Color.Gray,
                        onClick = {
                            onInputMethodChange("auto")
                            showInputMethodDialog = false
                        }
                    )
                    InputMethodOption(
                        text = "Shizuku",
                        selected = inputMethod == "shizuku",
                        status = if (shizukuAvailable) "Available" else "Not running",
                        statusColor = if (shizukuAvailable) Color(0xFF4CAF50) else Color.Red,
                        onClick = {
                            onInputMethodChange("shizuku")
                            showInputMethodDialog = false
                        }
                    )
                    InputMethodOption(
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
                                                Color(0xFF8B5CF6).copy(alpha = 0.15f),
                                                Color(0xFFD8B4FE).copy(alpha = 0.15f)
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
}


@Composable
private fun InputMethodOption(
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
