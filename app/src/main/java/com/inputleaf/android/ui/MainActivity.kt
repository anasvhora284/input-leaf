package com.inputleaf.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        viewModel.scan()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppNavigation(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    var screen by remember { mutableStateOf("main") }
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val discoveredServers by viewModel.discoveredServers.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val screenName by viewModel.screenName.collectAsState(initial = "android-phone")
    val autoConnect by viewModel.autoConnect.collectAsState(initial = true)
    val fingerprints by viewModel.fingerprints.collectAsState(initial = emptyMap())

    // TOFU fingerprint dialog
    var pendingFpRequest by remember { mutableStateOf<MainViewModel.FingerprintRequest?>(null) }
    LaunchedEffect(Unit) {
        viewModel.fingerprintRequest.collect { request -> pendingFpRequest = request }
    }
    pendingFpRequest?.let { req ->
        FingerprintDialog(
            fingerprint = req.newFp,
            oldFingerprint = req.oldFp,
            onConfirm = { viewModel.respondToFingerprint(req, true); pendingFpRequest = null },
            onDismiss = { viewModel.respondToFingerprint(req, false); pendingFpRequest = null }
        )
    }

    when (screen) {
        "main" -> MainScreen(
            connectionState = connectionState,
            discoveredServers = discoveredServers,
            isScanning = isScanning,
            screenName = screenName,
            onScan = { viewModel.scan() },
            onConnect = { viewModel.connect(it) },
            onAddManual = { viewModel.addManualServer(it) },
            onSettingsClick = { screen = "settings" }
        )
        "settings" -> SettingsScreen(
            screenName = screenName,
            autoConnect = autoConnect,
            fingerprints = fingerprints,
            onScreenNameChange = { viewModel.saveScreenName(it) },
            onAutoConnectChange = { viewModel.saveAutoConnect(it) },
            onDeleteFingerprint = { viewModel.deleteFingerprint(it) },
            onBack = { screen = "main" }
        )
    }
}
