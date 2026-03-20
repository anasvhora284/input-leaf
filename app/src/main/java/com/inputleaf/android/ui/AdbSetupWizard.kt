package com.inputleaf.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun AdbSetupWizard(
    adbPushCommand: String,
    adbStartCommand: String,
    isVerifying: Boolean,
    verifyResult: Boolean?,
    onVerify: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Up ADB") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("To inject keyboard and mouse events, run these commands on your PC:")

                listOf(
                    "1. Enable USB Debugging (Settings → Developer Options)",
                    "2. Connect USB cable",
                    "3. Run on PC:"
                ).forEach { Text(it) }

                CommandBlock(adbPushCommand) {
                    clipboard.setText(AnnotatedString(adbPushCommand))
                }
                CommandBlock(adbStartCommand) {
                    clipboard.setText(AnnotatedString(adbStartCommand))
                }

                when {
                    isVerifying -> Row {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Verifying…")
                    }
                    verifyResult == true  -> Text("✅ UHID server is running!")
                    verifyResult == false -> Text("❌ UHID server not detected. Check commands above.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onVerify, enabled = !isVerifying) { Text("Verify") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Skip") } }
    )
}

@Composable
private fun CommandBlock(command: String, onCopy: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(command, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            TextButton(onClick = onCopy) { Text("Copy") }
        }
    }
}
