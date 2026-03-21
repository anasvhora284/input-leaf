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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputleaf.android.ui.components.AnimatedBottomNavigation
import com.inputleaf.android.ui.components.NavItem

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
            val themeMode by viewModel.themeMode.collectAsState(initial = "SYSTEM")

            val isDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ Material You dynamic colors
                if (isDarkTheme) {
                    androidx.compose.material3.dynamicDarkColorScheme(this@MainActivity)
                } else {
                    dynamicLightColorScheme(this@MainActivity)
                }
            } else {
                // Fallback for older Android versions
                if (isDarkTheme) {
                    androidx.compose.material3.darkColorScheme(
                        primary = com.inputleaf.android.ui.theme.Purple400,
                        primaryContainer = com.inputleaf.android.ui.theme.Purple700,
                        onPrimary = androidx.compose.ui.graphics.Color.White,
                        secondary = com.inputleaf.android.ui.theme.Purple300,
                        tertiary = com.inputleaf.android.ui.theme.Success400,
                        background = androidx.compose.ui.graphics.Color(0xFF121212),
                        surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
                        onBackground = androidx.compose.ui.graphics.Color(0xFFE1E1E1),
                        onSurface = androidx.compose.ui.graphics.Color(0xFFE1E1E1)
                    )
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
        // Re-check battery optimization
        viewModel.checkBatteryOptimization()
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
    val batteryOptimizationExempt by viewModel.batteryOptimizationExempt.collectAsStateWithLifecycle()
    val fingerprints by viewModel.fingerprints.collectAsState(initial = emptyMap())
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsState(initial = "SYSTEM")
    val onboardingComplete by viewModel.onboardingComplete.collectAsState(initial = true)
    val mouseEnabled by viewModel.mouseEnabled.collectAsState(initial = true)
    val keyboardEnabled by viewModel.keyboardEnabled.collectAsState(initial = true)
    val favoriteServers by viewModel.favoriteServers.collectAsState(initial = emptySet())

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

    // Show onboarding on first launch
    if (!onboardingComplete) {
        OnboardingScreen(
            shizukuStatus = shizukuStatus,
            canDrawOverlays = canDrawOverlays,
            batteryOptimizationExempt = batteryOptimizationExempt,
            onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
            onRequestOverlayPermission = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            },
            onRequestBatteryOptimization = {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            },
            onComplete = { viewModel.completeOnboarding() }
        )
        return
    }

    val navItems = listOf(
        NavItem("Home", Icons.Rounded.Home, "main"),
        NavItem("Servers", Icons.Rounded.Dns, "servers"),
        NavItem("Permissions", Icons.Rounded.Shield, "setup"),
        NavItem("Settings", Icons.Rounded.Settings, "settings")
    )
    val selectedIndex = when (screen) {
        "main" -> 0
        "servers" -> 1
        "setup" -> 2
        "settings" -> 3
        else -> 0
    }

    Scaffold(
        bottomBar = {
            AnimatedBottomNavigation(
                items = navItems,
                selectedIndex = selectedIndex,
                onItemSelected = { index ->
                    screen = navItems[index].route
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (screen) {
                "main" -> MainScreen(
                    connectionState = connectionState,
                    discoveredServers = discoveredServers,
                    isScanning = isScanning,
                    screenName = screenName,
                    shizukuStatus = shizukuStatus,
                    mouseEnabled = mouseEnabled,
                    keyboardEnabled = keyboardEnabled,
                    favoriteServers = favoriteServers,
                    onScan = { viewModel.scan() },
                    onConnect = { viewModel.connect(it) },
                    onDisconnect = { viewModel.disconnect() },
                    onAddManual = { viewModel.addManualServer(it) },
                    onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
                    onToggleMouse = { viewModel.toggleMouseEnabled(it) },
                    onToggleKeyboard = { viewModel.toggleKeyboardEnabled(it) }
                )
                "servers" -> ServerListScreen(
                    connectionState = connectionState,
                    discoveredServers = discoveredServers,
                    isScanning = isScanning,
                    favoriteServers = favoriteServers,
                    onScan = { viewModel.scan() },
                    onConnect = { viewModel.connect(it) },
                    onAddManual = { viewModel.addManualServer(it) },
                    onToggleFavorite = { viewModel.toggleFavoriteServer(it) }
                )
                "setup" -> SetupScreen(
                    shizukuStatus = shizukuStatus,
                    canDrawOverlays = canDrawOverlays,
                    batteryOptimizationExempt = batteryOptimizationExempt,
                    onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
                    onRequestOverlayPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    onRequestBatteryOptimization = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
                "settings" -> SettingsScreen(
                    screenName = screenName,
                    autoConnect = autoConnect,
                    showCursor = showCursor,
                    themeMode = themeMode,
                    canDrawOverlays = canDrawOverlays,
                    fingerprints = fingerprints,
                    onScreenNameChange = { viewModel.saveScreenName(it) },
                    onAutoConnectChange = { viewModel.saveAutoConnect(it) },
                    onShowCursorChange = { viewModel.saveShowCursor(it) },
                    onThemeModeChange = { viewModel.saveThemeMode(it) },
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
    }
}
