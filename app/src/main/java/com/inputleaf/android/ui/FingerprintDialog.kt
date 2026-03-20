package com.inputleaf.android.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

@Composable
fun FingerprintDialog(
    fingerprint: String,
    oldFingerprint: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (oldFingerprint != null) "Certificate Changed!" else "Trust This Server?") },
        text = {
            if (oldFingerprint != null) {
                Text("The server certificate has changed. This may indicate a security risk.\n\n" +
                    "Old: ${oldFingerprint.chunked(8).joinToString(" ")}\n\n" +
                    "New: ${fingerprint.chunked(8).joinToString(" ")}")
            } else {
                Text("Verify this fingerprint matches what Input-Leap shows on your PC:\n\n" +
                    fingerprint.chunked(8).joinToString(" "), fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Trust") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
