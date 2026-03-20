package com.inputleaf.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(this@MainActivity)
            } else {
                lightColorScheme(
                    primary = com.inputleaf.android.ui.theme.Purple500,
                    primaryContainer = com.inputleaf.android.ui.theme.Purple100,
                    onPrimary = androidx.compose.ui.graphics.Color.White,
                    secondary = com.inputleaf.android.ui.theme.Purple400,
                    tertiary = com.inputleaf.android.ui.theme.Success500,
                    background = com.inputleaf.android.ui.theme.Background,
                    surface = com.inputleaf.android.ui.theme.Surface
                )
            }

            MaterialTheme(
                colorScheme = colorScheme,
                shapes = com.inputleaf.android.ui.theme.InputLeafShapes
            ) {
                Surface(Modifier.fillMaxSize()) {
                    AppNavigation(viewModel)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Re-check Shizuku status when returning to app
        viewModel.checkShizukuStatus()
        // Re-check overlay permission (user may have granted it in settings)
        viewModel.checkOverlayPermission()
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    var screen by remember { mutableStateOf("main") }
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val discoveredServers by viewModel.discoveredServers.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val screenName by viewModel.screenName.collectAsState(initial = "android-phone")
    val autoConnect by viewModel.autoConnect.collectAsState(initial = true)
    val showCursor by viewModel.showCursor.collectAsState(initial = true)
    val canDrawOverlays by viewModel.canDrawOverlays.collectAsStateWithLifecycle()
    val fingerprints by viewModel.fingerprints.collectAsState(initial = emptyMap())
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()

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
            shizukuStatus = shizukuStatus,
            onScan = { viewModel.scan() },
            onConnect = { viewModel.connect(it) },
            onAddManual = { viewModel.addManualServer(it) },
            onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
            onSettingsClick = { screen = "settings" }
        )
        "settings" -> SettingsScreen(
            screenName = screenName,
            autoConnect = autoConnect,
            showCursor = showCursor,
            canDrawOverlays = canDrawOverlays,
            fingerprints = fingerprints,
            onScreenNameChange = { viewModel.saveScreenName(it) },
            onAutoConnectChange = { viewModel.saveAutoConnect(it) },
            onShowCursorChange = { viewModel.saveShowCursor(it) },
            onRequestOverlayPermission = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            },
            onDeleteFingerprint = { viewModel.deleteFingerprint(it) },
            onBack = { screen = "main" }
        )
    }
}
