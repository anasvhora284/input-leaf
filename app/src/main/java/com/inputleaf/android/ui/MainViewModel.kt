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

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    // Shizuku status
    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.CHECKING)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus
    
    // Cursor overlay status
    private val _canDrawOverlays = MutableStateFlow(false)
    val canDrawOverlays: StateFlow<Boolean> = _canDrawOverlays
    
    // Battery optimization status
    private val _batteryOptimizationExempt = MutableStateFlow(false)
    val batteryOptimizationExempt: StateFlow<Boolean> = _batteryOptimizationExempt
    
    val showCursor: Flow<Boolean> = prefs.showCursor

    val screenName: Flow<String> = prefs.screenName
    val autoConnect: Flow<Boolean> = prefs.autoConnect
    val fingerprints: Flow<Map<String, String>> = prefs.allFingerprints()
    val themeMode: Flow<String> = prefs.themeMode
    val onboardingComplete: Flow<Boolean> = prefs.onboardingComplete
    val mouseEnabled: Flow<Boolean> = prefs.mouseEnabled
    val keyboardEnabled: Flow<Boolean> = prefs.keyboardEnabled
    val favoriteServers: Flow<Set<String>> = prefs.favoriteServers

    // TOFU: suspending channel — UI collects this and shows FingerprintDialog
    private val _fingerprintRequest = Channel<FingerprintRequest>(1)
    val fingerprintRequest = _fingerprintRequest.receiveAsFlow()

    data class FingerprintRequest(
        val ip: String,
        val newFp: String,
        val oldFp: String?,
        val response: kotlinx.coroutines.CompletableDeferred<Boolean>
    )
    
    enum class ShizukuStatus {
        CHECKING,
        NOT_INSTALLED,
        NOT_RUNNING,
        PERMISSION_REQUIRED,
        READY
    }

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
                    service?.disconnect()
                    // Small delay to ensure disconnect completes
                    kotlinx.coroutines.delay(500)
                    service?.connect(serverIp, name.trim())
                }
            }
        } 
    }
    fun saveAutoConnect(v: Boolean) { viewModelScope.launch { prefs.saveAutoConnect(v) } }
    fun deleteFingerprint(ip: String) { viewModelScope.launch { prefs.removeFingerprint(ip) } }
    fun saveThemeMode(mode: String) { viewModelScope.launch { prefs.saveThemeMode(mode) } }
    fun completeOnboarding() { viewModelScope.launch { prefs.saveOnboardingComplete() } }
    fun toggleMouseEnabled(enabled: Boolean) { viewModelScope.launch { prefs.saveMouseEnabled(enabled) } }
    fun toggleKeyboardEnabled(enabled: Boolean) { viewModelScope.launch { prefs.saveKeyboardEnabled(enabled) } }
    fun toggleFavoriteServer(ip: String) { viewModelScope.launch { prefs.toggleFavoriteServer(ip) } }

    // Called by UI after user taps Trust/Cancel in FingerprintDialog
    fun respondToFingerprint(request: FingerprintRequest, trusted: Boolean) {
        request.response.complete(trusted)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as ConnectionService.LocalBinder).getService()
            viewModelScope.launch {
                service!!.state.collect { _connectionState.value = it }
            }
            // Wire TOFU callback: bridge service's suspend callback → UI Channel
            service!!.onFingerprintConfirmationRequired = { ip, newFp, oldFp ->
                val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                _fingerprintRequest.send(FingerprintRequest(ip, newFp, oldFp, deferred))
                deferred.await()
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
    }
    
    // Shizuku permission listener
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        Log.d("InputLeaf", "Shizuku permission result: $grantResult")
        checkShizukuStatus()
    }
    
    // Shizuku binder lifecycle listener
    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        Log.d("InputLeaf", "Shizuku binder received")
        checkShizukuStatus()
    }
    
    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d("InputLeaf", "Shizuku binder dead")
        _shizukuStatus.value = ShizukuStatus.NOT_RUNNING
    }

    init {
        bindService()
        setupShizukuListeners()
        checkShizukuStatus()
        checkOverlayPermission()
        checkBatteryOptimization()
        
        // Observe showCursor preference and update service
        viewModelScope.launch {
            prefs.showCursor.collect { enabled ->
                service?.setCursorOverlayEnabled(enabled)
            }
        }
    }
    
    private fun setupShizukuListeners() {
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.addBinderReceivedListener(shizukuBinderListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        } catch (e: Exception) {
            Log.e("InputLeaf", "Failed to setup Shizuku listeners", e)
        }
    }
    
    fun checkShizukuStatus() {
        viewModelScope.launch {
            _shizukuStatus.value = try {
                if (!Shizuku.pingBinder()) {
                    // Check if Shizuku is installed
                    val pm = getApplication<Application>().packageManager
                    val shizukuInstalled = try {
                        pm.getPackageInfo("moe.shizuku.privileged.api", 0)
                        true
                    } catch (e: PackageManager.NameNotFoundException) {
                        false
                    }
                    if (shizukuInstalled) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
                } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    ShizukuStatus.PERMISSION_REQUIRED
                } else {
                    ShizukuStatus.READY
                }
            } catch (e: Exception) {
                Log.e("InputLeaf", "Error checking Shizuku status", e)
                ShizukuStatus.NOT_INSTALLED
            }
        }
    }
    
    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    if (Shizuku.shouldShowRequestPermissionRationale()) {
                        // User previously denied - show explanation in UI
                        Log.w("InputLeaf", "Shizuku permission was previously denied")
                    }
                    Shizuku.requestPermission(0)
                }
            }
        } catch (e: Exception) {
            Log.e("InputLeaf", "Error requesting Shizuku permission", e)
        }
    }
    
    fun checkOverlayPermission() {
        _canDrawOverlays.value = Settings.canDrawOverlays(getApplication())
    }
    
    fun checkBatteryOptimization() {
        val app = getApplication<Application>()
        val powerManager = app.getSystemService(PowerManager::class.java)
        _batteryOptimizationExempt.value = powerManager.isIgnoringBatteryOptimizations(app.packageName)
    }
    
    fun saveShowCursor(enabled: Boolean) {
        viewModelScope.launch { 
            prefs.saveShowCursor(enabled)
            service?.setCursorOverlayEnabled(enabled)
        }
    }

    private fun bindService() {
        val intent = Intent(getApplication(), ConnectionService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun scan() {
        viewModelScope.launch {
            _isScanning.value = true
            val ip = getLocalIpAddress()
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

    /**
     * Get the local IP address for network scanning.
     * Uses a prioritized approach to find the correct interface:
     * 1. Wi-Fi interfaces (wlan0, swlan0) - for regular Wi-Fi client mode
     * 2. Hotspot interfaces (ap0, swlan0) - for when phone is a hotspot
     * 3. WifiManager fallback - for older Android or edge cases
     * 4. Any private network interface - last resort
     */
    private fun getLocalIpAddress(): String? {
        // Priority 1: Look for Wi-Fi/hotspot interfaces by name
        val wifiInterfaceNames = listOf("wlan0", "wlan1", "swlan0", "ap0", "eth0")
        
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            
            // Log all interfaces for debugging
            interfaces.forEach { iface ->
                if (iface.isUp && !iface.isLoopback) {
                    val addrs = iface.inetAddresses.toList()
                        .filter { it.hostAddress?.contains('.') == true }
                        .mapNotNull { it.hostAddress }
                    if (addrs.isNotEmpty()) {
                        Log.d("InputLeaf", "Interface ${iface.name}: $addrs")
                    }
                }
            }
            
            // First pass: Look for preferred Wi-Fi interfaces
            for (ifaceName in wifiInterfaceNames) {
                val iface = interfaces.find { it.name == ifaceName && it.isUp && !it.isLoopback }
                if (iface != null) {
                    val ip = getIPv4FromInterface(iface)
                    if (ip != null) {
                        Log.d("InputLeaf", "Using preferred interface ${iface.name}: $ip")
                        return ip
                    }
                }
            }
            
            // Second pass: WifiManager fallback (works for regular Wi-Fi)
            val wifiIp = getWifiManagerIp()
            if (wifiIp != null && wifiIp != "0.0.0.0") {
                Log.d("InputLeaf", "Using WifiManager IP: $wifiIp")
                return wifiIp
            }
            
            // Third pass: Any private network interface (except cellular)
            val cellularPrefixes = listOf("rmnet", "ccmni", "pdp", "ppp", "uwbr")
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                // Skip cellular interfaces
                if (cellularPrefixes.any { iface.name.startsWith(it) }) continue
                
                val ip = getIPv4FromInterface(iface)
                if (ip != null && isPrivateIP(ip)) {
                    Log.d("InputLeaf", "Using fallback interface ${iface.name}: $ip")
                    return ip
                }
            }
        } catch (e: Exception) {
            Log.e("InputLeaf", "Error getting IP address: ${e.message}", e)
        }
        
        Log.e("InputLeaf", "Could not determine local IP address")
        return null
    }
    
    private fun getIPv4FromInterface(iface: NetworkInterface): String? {
        val addresses = iface.inetAddresses
        while (addresses.hasMoreElements()) {
            val address = addresses.nextElement()
            if (!address.isLoopbackAddress) {
                val hostAddress = address.hostAddress
                // Must be IPv4 (contains dots, no colons for IPv6)
                if (hostAddress != null && hostAddress.contains('.') && !hostAddress.contains(':')) {
                    // Strip any zone ID suffix (e.g., %wlan0)
                    return hostAddress.substringBefore('%')
                }
            }
        }
        return null
    }
    
    @Suppress("DEPRECATION")
    private fun getWifiManagerIp(): String? {
        return try {
            val wm = getApplication<Application>().getSystemService(WifiManager::class.java)
            val ipInt = wm.connectionInfo.ipAddress
            if (ipInt == 0) return null
            "${ipInt and 0xFF}.${(ipInt shr 8) and 0xFF}.${(ipInt shr 16) and 0xFF}.${(ipInt shr 24) and 0xFF}"
        } catch (e: Exception) {
            Log.w("InputLeaf", "WifiManager fallback failed: ${e.message}")
            null
        }
    }
    
    private fun isPrivateIP(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return when {
            first == 10 -> true  // 10.0.0.0/8
            first == 172 && second in 16..31 -> true  // 172.16.0.0/12
            first == 192 && second == 168 -> true  // 192.168.0.0/16
            else -> false
        }
    }

    fun connect(server: ServerInfo) {
        viewModelScope.launch {
            val name = prefs.screenName.first()
            prefs.saveLastServer(server.ip)
            
            // Try to connect Shizuku injector before connecting to server
            if (_shizukuStatus.value == ShizukuStatus.READY) {
                val shizukuConnected = service?.connectShizukuInjector() ?: false
                Log.d("InputLeaf", "Shizuku injector connected: $shizukuConnected")
            }
            
            service?.connect(server.ip, name)
        }
    }

    fun disconnect() { service?.disconnect() }

    fun addManualServer(ip: String) {
        _discoveredServers.value = _discoveredServers.value + ServerInfo(ip = ip)
    }

    override fun onCleared() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        } catch (e: Exception) {
            // Ignore
        }
        getApplication<Application>().unbindService(serviceConnection)
    }
}
