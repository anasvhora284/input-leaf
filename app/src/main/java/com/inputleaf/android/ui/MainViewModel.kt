package com.inputleaf.android.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.model.ServerInfo
import com.inputleaf.android.network.ClientCertificateSummary
import com.inputleaf.android.network.ClientCertificateValidationResult
import com.inputleaf.android.network.ConnectResult
import com.inputleaf.android.network.ConnectionTransportPolicy
import com.inputleaf.android.network.ServerScanner
import com.inputleaf.android.service.ConnectionService
import com.inputleaf.android.service.CursorOverlayService
import com.inputleaf.android.storage.AppPreferences
import com.inputleaf.android.storage.ClientCertificateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.NetworkInterface

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private const val MAX_CLIENT_CERTIFICATE_BYTES = 16 * 1024 * 1024

internal fun connectionFailureMessage(
    reason: ConnectResult.FailureReason,
    detail: String? = null,
): String = when (reason) {
    ConnectResult.FailureReason.NETWORK -> "Could not reach the Deskflow server"
    ConnectResult.FailureReason.TLS_AGAINST_PLAIN_SERVER ->
        "Server is not using TLS. Select Auto or Plain only, or enable TLS in Deskflow."
    ConnectResult.FailureReason.CERTIFICATE_MISMATCH ->
        "Deskflow's TLS certificate changed. Remove the trusted server only if you expect this."
    ConnectResult.FailureReason.CLIENT_CERT_REQUIRED ->
        "Deskflow requires a client certificate. Import one in Settings, then trust its fingerprint on the server."
    ConnectResult.FailureReason.HANDSHAKE ->
        "Deskflow handshake failed on the selected transport"
    ConnectResult.FailureReason.INCOMPATIBLE ->
        detail ?: "Deskflow rejected this client's protocol version"
    ConnectResult.FailureReason.BUSY ->
        "This screen name is already connected to Deskflow"
}

internal fun clientCertificateImportError(
    result: ClientCertificateValidationResult,
): String? = when (result) {
    is ClientCertificateValidationResult.Success -> null
    ClientCertificateValidationResult.IncorrectPassword -> "Incorrect PKCS12 password"
    ClientCertificateValidationResult.InvalidFormat ->
        "File is not a valid PKCS12 (.p12 or .pfx) bundle"
    ClientCertificateValidationResult.NoPrivateKey ->
        "The certificate bundle does not contain a private key"
    ClientCertificateValidationResult.KeyMismatch ->
        "The private key does not match the client certificate"
    ClientCertificateValidationResult.Expired -> "The client certificate has expired"
    ClientCertificateValidationResult.NotYetValid ->
        "The client certificate is not valid yet"
    ClientCertificateValidationResult.UnsupportedKey ->
        "The client certificate uses an unsupported key type"
    ClientCertificateValidationResult.StorageError ->
        "Could not securely store the client certificate"
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val clientCertificateStore = ClientCertificateStore(app)
    private val scanner = ServerScanner()
    private var service: ConnectionService? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _discoveredServers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val discoveredServers: StateFlow<List<ServerInfo>> = _discoveredServers

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState

    private val _clientCertificateSummary = MutableStateFlow<ClientCertificateSummary?>(null)
    val clientCertificateSummary: StateFlow<ClientCertificateSummary?> =
        _clientCertificateSummary

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
    val connectionTransportPolicy: Flow<ConnectionTransportPolicy> =
        prefs.connectionTransportPolicy

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
    fun saveConnectionTransportPolicy(policy: ConnectionTransportPolicy) {
        viewModelScope.launch { prefs.saveConnectionTransportPolicy(policy) }
    }
    fun importClientCertificate(uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val pkcs12 = try {
                readClientCertificate(uri)
            } catch (_: IllegalArgumentException) {
                _errorState.value = "Client certificate must be smaller than 16 MB"
                return@launch
            } catch (error: Exception) {
                Log.e("InputLeaf", "Failed to read client certificate", error)
                _errorState.value = "Could not read the selected certificate file"
                return@launch
            }
            val passwordChars = password.toCharArray()
            val result = try {
                clientCertificateStore.importCertificate(pkcs12, passwordChars)
            } finally {
                pkcs12.fill(0)
                passwordChars.fill('\u0000')
            }
            if (result is ClientCertificateValidationResult.Success) {
                _clientCertificateSummary.value = result.summary
            }
            clientCertificateImportError(result)?.let { _errorState.value = it }
        }
    }

    fun clearClientCertificate() {
        viewModelScope.launch(Dispatchers.IO) {
            clientCertificateStore.clear()
            _clientCertificateSummary.value = null
        }
    }

    private fun readClientCertificate(uri: Uri): ByteArray {
        val input = checkNotNull(getApplication<Application>().contentResolver.openInputStream(uri))
        return input.use {
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                try {
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_CLIENT_CERTIFICATE_BYTES)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } finally {
                    buffer.fill(0)
                }
            }
        }
    }

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
            service!!.onConnectionFailed = { reason, detail ->
                _errorState.value = connectionFailureMessage(reason, detail)
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

        viewModelScope.launch(Dispatchers.IO) {
            _clientCertificateSummary.value = clientCertificateStore.summary()
        }

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
