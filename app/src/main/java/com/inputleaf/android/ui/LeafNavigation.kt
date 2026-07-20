package com.inputleaf.android.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputleaf.android.ui.components.AnimatedBottomNavigation
import com.inputleaf.android.ui.components.NavItem
import com.inputleaf.android.util.BatteryOptimizationHelper

private sealed class LeafRoute(val key: String) {
    data object Home : LeafRoute("main")
    data object Servers : LeafRoute("servers")
    data object Setup : LeafRoute("setup")
    data object Settings : LeafRoute("settings")
}

@Composable
fun LeafNavigation(viewModel: MainViewModel) {
    var screen by remember { mutableStateOf(LeafRoute.Home.key) }
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
    val inputMethod by viewModel.inputMethod.collectAsState(initial = "auto")
    val cursorStyle by viewModel.cursorStyle.collectAsState(initial = "default")
    val shizukuAvailable by viewModel.shizukuAvailable.collectAsState(initial = false)
    val accessibilityAvailable by viewModel.accessibilityAvailable.collectAsState(initial = false)
    val imeEnabledAndSelected by viewModel.imeEnabledAndSelected.collectAsStateWithLifecycle(initialValue = false)

    var pendingFpRequest by remember { mutableStateOf<MainViewModel.FingerprintRequest?>(null) }
    LaunchedEffect(Unit) {
        viewModel.fingerprintRequest.collect { request -> pendingFpRequest = request }
    }
    pendingFpRequest?.let { req ->
        FingerprintDialog(
            fingerprint = req.newFp,
            oldFingerprint = req.oldFp,
            onConfirm = { viewModel.respondToFingerprint(req, true); pendingFpRequest = null },
            onDismiss = { viewModel.respondToFingerprint(req, false); pendingFpRequest = null },
        )
    }

    if (!onboardingComplete) {
        OnboardingScreen(
            shizukuStatus = shizukuStatus,
            accessibilityAvailable = accessibilityAvailable,
            canDrawOverlays = canDrawOverlays,
            batteryOptimizationExempt = batteryOptimizationExempt,
            imeEnabledAndSelected = imeEnabledAndSelected,
            onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
            onRequestOverlayPermission = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
            onRequestBatteryOptimization = { BatteryOptimizationHelper.requestExemption(context) },
            onRequestImeSetup = { openImeSetup(context) },
            onComplete = { viewModel.completeOnboarding() },
        )
        return
    }

    val navItems = listOf(
        NavItem("Home", Icons.Rounded.Home, LeafRoute.Home.key),
        NavItem("Servers", Icons.Rounded.Dns, LeafRoute.Servers.key),
        NavItem("Permissions", Icons.Rounded.Shield, LeafRoute.Setup.key),
        NavItem("Settings", Icons.Rounded.Settings, LeafRoute.Settings.key),
    )
    val selectedIndex = navItems.indexOfFirst { it.route == screen }.coerceAtLeast(0)

    Scaffold(
        bottomBar = {
            AnimatedBottomNavigation(
                items = navItems,
                selectedIndex = selectedIndex,
                onItemSelected = { index -> screen = navItems[index].route },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (screen) {
                LeafRoute.Home.key -> MainScreen(
                    connectionState = connectionState,
                    discoveredServers = discoveredServers,
                    isScanning = isScanning,
                    screenName = screenName,
                    shizukuStatus = shizukuStatus,
                    accessibilityAvailable = accessibilityAvailable,
                    mouseEnabled = mouseEnabled,
                    keyboardEnabled = keyboardEnabled,
                    favoriteServers = favoriteServers,
                    onScan = { viewModel.scan() },
                    onConnect = { viewModel.connect(it) },
                    onDisconnect = { viewModel.disconnect() },
                    onAddManual = { viewModel.addManualServer(it) },
                    onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
                    onRequestAccessibilityService = { openAccessibilitySettings(context) },
                    onScreenNameChange = { viewModel.saveScreenName(it) },
                    onToggleMouse = { viewModel.toggleMouseEnabled(it) },
                    onToggleKeyboard = { viewModel.toggleKeyboardEnabled(it) },
                )
                LeafRoute.Servers.key -> ServerListScreen(
                    connectionState = connectionState,
                    discoveredServers = discoveredServers,
                    isScanning = isScanning,
                    favoriteServers = favoriteServers,
                    onScan = { viewModel.scan() },
                    onConnect = { viewModel.connect(it) },
                    onAddManual = { viewModel.addManualServer(it) },
                    onToggleFavorite = { viewModel.toggleFavoriteServer(it) },
                )
                LeafRoute.Setup.key -> SetupScreen(
                    shizukuStatus = shizukuStatus,
                    accessibilityAvailable = accessibilityAvailable,
                    canDrawOverlays = canDrawOverlays,
                    batteryOptimizationExempt = batteryOptimizationExempt,
                    imeEnabledAndSelected = imeEnabledAndSelected,
                    onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
                    onRequestOverlayPermission = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    onRequestBatteryOptimization = { BatteryOptimizationHelper.requestExemption(context) },
                    onRequestAccessibilityService = { openAccessibilitySettings(context) },
                    onRequestImeSetup = { openImeSetup(context) },
                )
                LeafRoute.Settings.key -> SettingsScreen(
                    screenName = screenName,
                    autoConnect = autoConnect,
                    showCursor = showCursor,
                    themeMode = themeMode,
                    inputMethod = inputMethod,
                    cursorStyle = cursorStyle,
                    shizukuAvailable = shizukuAvailable,
                    accessibilityAvailable = accessibilityAvailable,
                    canDrawOverlays = canDrawOverlays,
                    fingerprints = fingerprints,
                    onScreenNameChange = { viewModel.saveScreenName(it) },
                    onAutoConnectChange = { viewModel.saveAutoConnect(it) },
                    onShowCursorChange = { viewModel.saveShowCursor(it) },
                    onThemeModeChange = { viewModel.saveThemeMode(it) },
                    onInputMethodChange = { viewModel.saveInputMethod(it) },
                    onCursorStyleChange = { viewModel.saveCursorStyle(it) },
                    onRequestOverlayPermission = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    onDeleteFingerprint = { viewModel.deleteFingerprint(it) },
                    onBack = { screen = LeafRoute.Home.key },
                )
            }
        }
    }
}

private fun openImeSetup(context: android.content.Context) {
    val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
    val enabledIds = imm.enabledInputMethodList.map { it.id }
    val ourId = ComponentName(
        context.packageName,
        com.inputleaf.android.inject.InputLeafIME::class.java.name,
    ).flattenToShortString()
    if (enabledIds.contains(ourId)) {
        imm.showInputMethodPicker()
    } else {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        Toast.makeText(
            context,
            "Enable Input Leaf Keyboard, then click again to select it",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun openAccessibilitySettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        val comp = ComponentName(
            context.packageName,
            com.inputleaf.android.inject.AccessibilityInputService::class.java.name,
        )
        putExtra(
            ":settings:show_fragment_args",
            android.os.Bundle().apply {
                putString(":settings:fragment_args_key", comp.flattenToString())
            },
        )
    }
    context.startActivity(intent)
}
