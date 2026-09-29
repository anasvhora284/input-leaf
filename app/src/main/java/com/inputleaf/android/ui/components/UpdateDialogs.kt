package com.inputleaf.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inputleaf.android.update.VersionChangelog

@Composable
fun UpdateAvailableDialog(
    latestVersion: String,
    changelog: String,
    isFdroid: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Download, contentDescription = "Update") },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Update Available")
                Text(
                    text = "v$latestVersion is now ready",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (changelog.isNotBlank()) {
                    Text(
                        text = "What's changed:",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            MarkdownContent(markdown = changelog)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = if (isFdroid) {
                        "You can update Input Leaf directly from the F-Droid app repository."
                    } else {
                        "You can download the latest signed release APK from GitHub."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) {
                Icon(
                    imageVector = if (isFdroid) Icons.Rounded.Shop else Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = if (isFdroid) "Open in F-Droid" else "Download from GitHub")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}

@Composable
fun WhatsNewDialog(
    changelog: VersionChangelog,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.NewReleases, contentDescription = "New") },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("What's New")
                Text(
                    text = "Version ${changelog.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                changelog.highlights.forEach { highlight ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(text = highlight, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it!")
            }
        }
    )
}
