package com.inputleaf.android.ui.root

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inputleaf.android.root.ClipboardSyncService
import com.inputleaf.android.root.ControlChannelClient
import com.inputleaf.android.root.RootCompanionState
import com.inputleaf.android.ui.components.GradientCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
@Composable
fun RootCompanionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Disconnected") }
    var selectedName by remember { mutableStateOf<String?>(null) }

    val adapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    val bonded = remember(adapter) { adapter?.bondedDevices?.toList().orEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Input Root", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pair as HID with the PC device “Input Root”, then connect the control channel for clipboard sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GradientCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Status: $status", style = MaterialTheme.typography.titleMedium)
                selectedName?.let { Text("Device: $it") }
            }
        }

        Text("Paired PCs", style = MaterialTheme.typography.titleSmall)
        if (bonded.isEmpty()) {
            Text(
                "No bonded Bluetooth devices yet. Pair “Input Root” in system Bluetooth settings first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        bonded.forEach { device ->
            OutlinedButton(
                onClick = {
                    selectedName = device.name ?: device.address
                    scope.launch {
                        status = "Connecting…"
                        try {
                            val client = ControlChannelClient(adapter!!)
                            RootCompanionState.client = client
                            context.startForegroundService(Intent(context, ClipboardSyncService::class.java))
                            status = "Connected — syncing clipboard"
                            withContext(Dispatchers.IO) {
                                client.connect(device)
                            }
                            status = "Disconnected"
                        } catch (e: Exception) {
                            status = "Failed: ${e.message}"
                            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(device.name ?: device.address)
            }
        }

        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Bluetooth / audio settings")
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back")
        }
    }
}
