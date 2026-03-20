package com.inputleaf.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    screenName: String,
    autoConnect: Boolean,
    showCursor: Boolean,
    canDrawOverlays: Boolean,
    fingerprints: Map<String, String>, // ip → fingerprint
    onScreenNameChange: (String) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onShowCursorChange: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onDeleteFingerprint: (String) -> Unit,
    onBack: () -> Unit
) {
    var editingName by remember(screenName) { mutableStateOf(screenName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.padding(horizontal = 16.dp)) {
            item {
                OutlinedTextField(
                    value = editingName,
                    onValueChange = { editingName = it },
                    label = { Text("Screen name") },
                    supportingText = { Text("Must match Input-Leap server layout") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    singleLine = true
                )
                Button(onClick = { onScreenNameChange(editingName) },
                    modifier = Modifier.padding(bottom = 16.dp)) { Text("Save") }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-connect on launch")
                    Switch(checked = autoConnect, onCheckedChange = onAutoConnectChange)
                }
                
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Cursor Overlay", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                
                // Show cursor toggle with permission check
                if (!canDrawOverlays) {
                    // Permission not granted - show warning and button
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Overlay permission required",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Required to show cursor on screen",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Button(
                            onClick = onRequestOverlayPermission,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            Text("Grant Permission")
                        }
                    }
                } else {
                    // Permission granted - show toggle
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Show cursor overlay")
                            Text(
                                "Display a visual cursor when controlling",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = showCursor, onCheckedChange = onShowCursorChange)
                    }
                }
                
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Trusted Servers", style = MaterialTheme.typography.titleSmall)
            }
            items(fingerprints.entries.toList()) { (ip, fp) ->
                ListItem(
                    headlineContent = { Text(ip) },
                    supportingContent = { Text(fp.take(16) + "…", style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        IconButton(onClick = { onDeleteFingerprint(ip) }) {
                            Icon(Icons.Default.Delete, "Remove")
                        }
                    }
                )
            }
        }
    }
}
