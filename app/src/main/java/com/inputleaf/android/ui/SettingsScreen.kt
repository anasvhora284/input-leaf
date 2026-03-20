package com.inputleaf.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    screenName: String,
    autoConnect: Boolean,
    fingerprints: Map<String, String>, // ip → fingerprint
    onScreenNameChange: (String) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Auto-connect on launch")
                    Switch(checked = autoConnect, onCheckedChange = onAutoConnectChange)
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
