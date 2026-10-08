package com.inputleaf.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inputleaf.android.network.TlsFingerprintManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrustedServersSheet(
    fingerprints: Map<String, String>,
    serverNames: Map<String, String> = emptyMap(),
    onDeleteFingerprint: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var serverToDelete by remember { mutableStateOf<String?>(null) }
    var selectedServerFp by remember { mutableStateOf<Pair<String, String>?>(null) }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var copiedToClipboard by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ExpressiveSheetHeader(
                icon = Icons.Rounded.Security,
                title = "Trusted Servers",
                description = "Manage servers whose TLS certificates are trusted by this device.",
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (fingerprints.isNotEmpty()) {
                val sortedEntries = remember(fingerprints) {
                    fingerprints.entries.sortedBy { it.key }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    sortedEntries.forEachIndexed { index, (ip, fp) ->
                        val displayName = serverNames[ip]
                        val hasName = !displayName.isNullOrBlank() && displayName != ip
                        val formattedFp = remember(fp) {
                            TlsFingerprintManager.formatFingerprint(fp)
                        }

                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(index, sortedEntries.size),
                            onClick = { selectedServerFp = ip to fp },
                            leadingContent = {
                                CircularAvatar(
                                    icon = Icons.Rounded.VpnKey,
                                    size = 40.dp,
                                    iconSize = 20.dp,
                                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            },
                            supportingContent = {
                                if (hasName) {
                                    Text(
                                        text = "$ip • ${formattedFp.take(23)}…",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else {
                                    Text(
                                        text = "${formattedFp.take(23)}…",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { serverToDelete = ip }) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Revoke trust",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        ) {
                            Text(
                                text = displayName?.takeIf { hasName } ?: ip,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            } else {
                ExpressiveEmptyCard(
                    message = "No trusted servers yet. When you connect to a server over TLS and verify its certificate fingerprint, it will appear here.",
                )
            }
        }
    }

    // Confirmation dialog for revoking trust
    serverToDelete?.let { ip ->
        val serverName = serverNames[ip]
        val label = if (!serverName.isNullOrBlank() && serverName != ip) "$serverName ($ip)" else ip
        DeleteConfirmationDialog(
            title = "Revoke Server Trust",
            message = "Remove trust for $label? If you reconnect to this server, you will be prompted to verify and trust its certificate again.",
            confirmLabel = "Revoke",
            onConfirm = {
                onDeleteFingerprint(ip)
                serverToDelete = null
            },
            onDismiss = { serverToDelete = null },
        )
    }

    // Detail dialog when tapping a server to see full fingerprint and copy
    selectedServerFp?.let { (ip, fp) ->
        val serverName = serverNames[ip]
        val titleText = if (!serverName.isNullOrBlank() && serverName != ip) serverName else ip
        val fullFormatted = TlsFingerprintManager.formatFingerprint(fp)

        AlertDialog(
            onDismissRequest = {
                selectedServerFp = null
                copiedToClipboard = false
            },
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            title = { Text(titleText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!serverName.isNullOrBlank() && serverName != ip) {
                        Text(
                            text = "IP Address: $ip",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "SHA-256 Fingerprint:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = fullFormatted,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    if (copiedToClipboard) {
                        Text(
                            text = "Copied to clipboard",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(fullFormatted))
                        copiedToClipboard = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Copy Fingerprint")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedServerFp = null
                    copiedToClipboard = false
                }) {
                    Text("Close")
                }
            },
        )
    }
}
