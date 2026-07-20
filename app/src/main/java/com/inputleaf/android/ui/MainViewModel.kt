package com.inputleaf.android.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.network.ServerScanner
import com.inputleaf.android.service.ConnectionService
import com.inputleaf.android.service.CursorOverlayService
import com.inputleaf.android.storage.AppPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import rikka.shizuku.Shizuku
import java.net.InetAddress
import java.net.NetworkInterface

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val scanner = ServerScanner()
    private var service: ConnectionService? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _discoveredServers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val discoveredServers: StateFlow<List<ServerInfo>> = _discoveredServers

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    private val permissionProvider = PermissionStatusProvider(app)
    
    // Shizuku status
    val shizukuStatus: StateFlow<ShizukuStatus> = permissionProvider.shizukuStatus
    
    // Cursor overlay status
    val canDrawOverlays: StateFlow<Boolean> = permissionProvider.canDrawOverlays
    
    // Battery optimization status
    val batteryOptimizationExempt: StateFlow<Boolean> = permissionProvider.batteryOptimizationExempt
    
    val showCursor: Flow<Boolean> = prefs.showCursor

    val screenName: Flow<String> = prefs.screenName
    val autoConnect: Flow<Boolean> = prefs.autoConnect
    val fingerprints: Flow<Map<String, String>> = prefs.allFingerprints()
    val themeMode: Flow<String> = prefs.themeMode
    val cursorStyle: Flow<String> = prefs.cursorStyle
    val leafOnboardingComplete: Flow<Boolean> = prefs.leafOnboardingComplete
    val onboardingComplete: Flow<Boolean> = prefs.leafOnboardingComplete

    val mouseEnabled: Flow<Boolean> = prefs.mouseEnabled
    val keyboardEnabled: Flow<Boolean> = prefs.keyboardEnabled
    val favoriteServers: Flow<Set<String>> = prefs.favoriteServers
    val inputMethod: Flow<String> = prefs.inputMethod

    val shizukuAvailable: Flow<Boolean> = permissionProvider.shizukuAvailable
    val accessibilityAvailable: Flow<Boolean> = permissionProvider.accessibilityAvailable
    val imeEnabledAndSelected: Flow<Boolean> = permissionProvider.imeEnabledAndSelected

    // TOFU: suspending channel — UI collects this and shows FingerprintDialog
    private val _fingerprintRequest = Channel<FingerprintRequest>(1)
    val fingerprintRequest = _fingerprintRequest.receiveAsFlow()

    data class FingerprintRequest(
        val ip: String,
        val newFp: String,
        val oldFp: String?,
        val response: kotlinx.coroutines.CompletableDeferred<Boolean>
    )


    fun saveScreenName(name: String) { 
        viewModelScope.launch { 
            prefs.saveScreenName(name)
            // Reconnect with new screen name if currently connected
            val currentState = _connectionState.value
            if (currentState is ConnectionState.Idle || 
                currentState is ConnectionState.Active ||
                currentState is ConnectionState.Connecting ||
                currentState is ConnectionState.Handshaking) {
                // Get the current server IP and reconnect
                val serverIp = when (currentState) {
                    is ConnectionState.Idle -> currentState.serverIp
                    is ConnectionState.Active -> currentState.serverIp
                    is ConnectionState.Connecting -> currentState.serverIp
                    is ConnectionState.Handshaking -> currentState.serverIp
                    else -> null
                }
                if (serverIp != null) {
                    Log.d("InputLeaf", "Screen name changed, reconnecting with new name: $name")
                    service?.reconnect(serverIp, name.trim())
                }
            }
        } 
    }
    fun saveAutoConnect(v: Boolean) { viewModelScope.launch { prefs.saveAutoConnect(v) } }
    fun deleteFingerprint(ip: String) { viewModelScope.launch { prefs.removeFingerprint(ip) } }
    fun saveThemeMode(mode: String) { viewModelScope.launch { prefs.saveThemeMode(mode) } }
    fun completeOnboarding() { viewModelScope.launch { prefs.saveLeafOnboardingComplete() } }
    fun toggleMouseEnabled(enabled: Boolean) { viewModelScope.launch { prefs.saveMouseEnabled(enabled) } }
    fun toggleKeyboardEnabled(enabled: Boolean) { viewModelScope.launch { prefs.saveKeyboardEnabled(enabled) } }
    fun toggleFavoriteServer(ip: String) { viewModelScope.launch { prefs.toggleFavoriteServer(ip) } }
    fun saveInputMethod(method: String) { viewModelScope.launch { prefs.saveInputMethod(method) } }
    fun saveCursorStyle(style: String) { viewModelScope.launch { prefs.saveCursorStyle(style) } }

    // Called by UI after user taps Trust/Cancel in FingerprintDialog
    fun respondToFingerprint(request: FingerprintRequest, trusted: Boolean) {
        request.response.complete(trusted)
    }

    private var hasAutoConnected = false

    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localService = (binder as ConnectionService.LocalBinder).getService()
            service = localService
            
            // Push current service state immediately to avoid race conditions
            _connectionState.value = localService.state.value

            viewModelScope.launch {
                localService.state.collect { _connectionState.value = it }
            }
            // Wire TOFU callback: bridge service's suspend callback → UI Channel
            service!!.onFingerprintConfirmationRequired = { ip, newFp, oldFp ->
                val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                _fingerprintRequest.send(FingerprintRequest(ip, newFp, oldFp, deferred))
                deferred.await()
            }
            service!!.onConnectionRejected = {
                _errorState.value = "Connection not trusted"
            }
            
            // Auto-connect to last server if enabled
            if (!hasAutoConnected) {
                hasAutoConnected = true
                viewModelScope.launch {
                    val auto = prefs.autoConnect.first()
                    val lastIp = prefs.lastServerIp.first()
                    val currentState = service?.state?.value ?: ConnectionState.Disconnected
                    if (auto && !lastIp.isNullOrBlank() && currentState !is ConnectionState.Idle && currentState !is ConnectionState.Active) {
                        Log.i("InputLeaf", "Auto-connecting to last server: $lastIp")
                        connect(ServerInfo(ip = lastIp))
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
    }
    init {
        bindService()

        // Observe showCursor preference and update service
        viewModelScope.launch {
            prefs.showCursor.collect { enabled ->
                service?.setCursorOverlayEnabled(enabled)
            }
        }
    }
    fun checkShizukuStatus() = permissionProvider.checkShizukuStatus()
    fun requestShizukuPermission() = permissionProvider.requestShizukuPermission()
    fun checkOverlayPermission() = permissionProvider.checkOverlayPermission()
    fun checkBatteryOptimization() = permissionProvider.checkBatteryOptimization()
    
    fun saveShowCursor(enabled: Boolean) {
        viewModelScope.launch { 
            prefs.saveShowCursor(enabled)
            service?.setCursorOverlayEnabled(enabled)
        }
    }

    private fun bindService() {
        if (serviceBound) return
        val intent = Intent(getApplication(), ConnectionService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        serviceBound = true
    }

    fun scan() {
        viewModelScope.launch {
            _isScanning.value = true
            val ip = com.inputleaf.android.network.NetworkUtils.getLocalIpAddress(getApplication())
            Log.d("InputLeaf", "Scanning from IP: $ip")
            if (ip != null) {
                val results = scanner.scan(ip)
                Log.d("InputLeaf", "Scan done: ${results.size} servers found: $results")
                _discoveredServers.value = results
            } else {
                Log.e("InputLeaf", "Could not determine local IP address")
                _discoveredServers.value = emptyList()
            }
            _isScanning.value = false
        }
    }



    fun clearError() {
        _errorState.value = null
    }

    private suspend fun resolveInjector(): com.inputleaf.android.inject.InputInjector? {
        val method = prefs.inputMethod.first()
        val wm = getApplication<Application>().getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val bounds = wm.currentWindowMetrics.bounds
        val shizukuInjector = com.inputleaf.android.shizuku.ShizukuInputInjector(bounds.width(), bounds.height())
        val accessibilityInjector = com.inputleaf.android.inject.AccessibilityInputInjector(getApplication(), bounds.width(), bounds.height())

        val resolved = com.inputleaf.android.inject.InputMethodResolver.resolve(
            preferredMethod = method,
            isShizukuAvailable = shizukuInjector.isAvailable(),
            isAccessibilityAvailable = accessibilityInjector.isAvailable()
        )

        return when (resolved) {
            com.inputleaf.android.inject.ResolvedMethod.SHIZUKU -> shizukuInjector
            com.inputleaf.android.inject.ResolvedMethod.ACCESSIBILITY -> accessibilityInjector
            com.inputleaf.android.inject.ResolvedMethod.NONE -> null
        }
    }

    fun connect(server: ServerInfo) {
        viewModelScope.launch {
            val state = _connectionState.value
            if (state is ConnectionState.Connecting || state is ConnectionState.Handshaking) {
                Log.d("InputLeaf", "Ignoring connect — already connecting to ${server.ip}")
                return@launch
            }
            val name = prefs.screenName.first()
            prefs.saveLastServer(server.ip)
            
            val injector = resolveInjector()
            if (injector == null) {
                _errorState.value = "No input method available. Enable Shizuku or Accessibility Service."
                return@launch
            }
            
            val connected = injector.connect()
            if (!connected) {
                _errorState.value = "Failed to connect to input method: ${injector.name}"
                return@launch
            }
            
            service?.setInjector(injector)
            service?.connect(server.ip, name)
        }
    }

    fun disconnect() { service?.disconnect() }

    fun addManualServer(ip: String) {
        _discoveredServers.value = _discoveredServers.value + ServerInfo(ip = ip)
    }

    override fun onCleared() {
        permissionProvider.cleanup()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
        }
    }
}
