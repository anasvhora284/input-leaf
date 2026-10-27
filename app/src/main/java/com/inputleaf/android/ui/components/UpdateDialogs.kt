package com.inputleaf.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inputleaf.android.update.ApkInstaller
import com.inputleaf.android.update.VersionChangelog
import java.io.File

/** Where an in-app update download is, for the update dialog. */
sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    data class Downloading(val done: Long, val total: Long) : UpdateDownload
    data class Ready(val file: File) : UpdateDownload
    data class Failed(val message: String) : UpdateDownload
}

@Composable
fun UpdateAvailableDialog(
    latestVersion: String,
    changelog: String,
    isFdroid: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    /** False when there is no APK for this device, so only the GitHub page is offered. */
    canInstallInApp: Boolean = false,
    download: UpdateDownload = UpdateDownload.Idle,
    installStatus: ApkInstaller.Status = ApkInstaller.Status.Idle,
    onDownload: () -> Unit = {},
    onInstall: () -> Unit = {},
) {
    val inApp = canInstallInApp && !isFdroid
    val error = (download as? UpdateDownload.Failed)?.message
        ?: (installStatus as? ApkInstaller.Status.Failed)?.message
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
                    text = when {
                        isFdroid -> "You can update Input Leaf directly from the F-Droid app repository."
                        inApp -> "Input Leaf downloads the signed release for this device, then Android asks you to confirm the install."
                        else -> "You can download the latest signed release APK from GitHub."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (inApp && download is UpdateDownload.Downloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (download.total > 0) {
                        LinearProgressIndicator(
                            progress = { (download.done.toFloat() / download.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                if (inApp && error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onUpdate, contentPadding = PaddingValues(0.dp)) {
                        Text("Download from GitHub instead")
                    }
                }
            }
        },
        confirmButton = {
            if (!inApp) {
                Button(onClick = onUpdate) {
                    ButtonIcon(if (isFdroid) Icons.Rounded.Shop else Icons.Rounded.Download)
                    Text(text = if (isFdroid) "Open in F-Droid" else "Download from GitHub")
                }
            } else when (download) {
                is UpdateDownload.Downloading -> Button(onClick = {}, enabled = false) {
                    Text(if (download.total > 0) "Downloading ${download.done * 100 / download.total}%" else "Downloading…")
                }
                is UpdateDownload.Ready -> {
                    val installing = installStatus is ApkInstaller.Status.Installing
                    Button(onClick = onInstall, enabled = !installing) {
                        ButtonIcon(Icons.Rounded.InstallMobile)
                        Text(if (installing) "Installing…" else "Install")
                    }
                }
                is UpdateDownload.Failed -> Button(onClick = onDownload) { Text("Try again") }
                UpdateDownload.Idle -> Button(onClick = onDownload) {
                    ButtonIcon(Icons.Rounded.Download)
                    Text("Download")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (inApp && download is UpdateDownload.Downloading) "Cancel" else "Later")
            }
        }
    )
}

@Composable
private fun ButtonIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
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
