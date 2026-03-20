package com.inputleaf.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inputleaf.android.model.ServerInfo

@Composable
fun ServerListItem(server: ServerInfo, isConnected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(server.name.ifBlank { server.ip }) },
        supportingContent = { Text(server.ip) },
        leadingContent = { Text("🖥") },
        trailingContent = {
            if (isConnected) Badge { Text("Connected") }
        }
    )
}
