package com.inputleaf.android.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.inputleaf.android.network.TlsFingerprintManager

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
                    "Old: ${formatFingerprintForDisplay(oldFingerprint)}\n\n" +
                    "New: ${formatFingerprintForDisplay(fingerprint)}")
            } else {
                Text("Verify this fingerprint matches what Deskflow shows on your PC:\n\n" +
                    formatFingerprintForDisplay(fingerprint), fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Trust") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LocalFingerprintDialog(
    fingerprint: String,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit,
    onImport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This device's fingerprint") },
        text = {
            Text(
                "Compare this with Deskflow when it asks to trust a new client.\n\n" +
                    formatFingerprintForDisplay(fingerprint),
                fontFamily = FontFamily.Monospace,
            )
        },
        confirmButton = {
            TextButton(onClick = onImport) { Text("Import PKCS12") }
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onRegenerate) { Text("Regenerate") }
        },
    )
}

private fun formatFingerprintForDisplay(fingerprint: String): String =
    TlsFingerprintManager.formatFingerprint(fingerprint)
