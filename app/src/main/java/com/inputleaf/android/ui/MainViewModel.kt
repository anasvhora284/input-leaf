package com.inputleaf.android.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.IBinder
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
    
    val showCursor: Flow<Boolean> = prefs.showCursor

    val screenName: Flow<String> = prefs.screenName
    val autoConnect: Flow<Boolean> = prefs.autoConnect
    val fingerprints: Flow<Map<String, String>> = prefs.allFingerprints()

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

    fun saveScreenName(name: String) { viewModelScope.launch { prefs.saveScreenName(name) } }
    fun saveAutoConnect(v: Boolean) { viewModelScope.launch { prefs.saveAutoConnect(v) } }
    fun deleteFingerprint(ip: String) { viewModelScope.launch { prefs.removeFingerprint(ip) } }

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
            val wm = getApplication<Application>().getSystemService(WifiManager::class.java)
            val ipInt = wm.connectionInfo.ipAddress
            val ip = "${ipInt and 0xFF}.${(ipInt shr 8) and 0xFF}.${(ipInt shr 16) and 0xFF}.${(ipInt shr 24) and 0xFF}"
            Log.d("InputLeaf", "Scanning from IP: $ip (raw=$ipInt)")
            val results = scanner.scan(ip)
            Log.d("InputLeaf", "Scan done: ${results.size} servers found: $results")
            _discoveredServers.value = results
            _isScanning.value = false
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
