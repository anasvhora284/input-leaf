package com.inputleaf.android.ui

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.inputleaf.android.privilege.LibSuRootAccess
import com.inputleaf.android.privilege.RootAvailability
import com.inputleaf.android.storage.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    CHECKING,
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    READY
}

enum class RootStatus {
    CHECKING,
    MISSING,
    AVAILABLE,
    DENIED,
    GRANTED,
}

class PermissionStatusProvider(
    private val app: Application,
    private val prefs: AppPreferences = AppPreferences(app),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.CHECKING)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus

    @Volatile private var rootReprobeStarted = false

    private val _rootStatus = MutableStateFlow(RootStatus.CHECKING)
    val rootStatus: StateFlow<RootStatus> = _rootStatus
    
    private val _canDrawOverlays = MutableStateFlow(false)
    val canDrawOverlays: StateFlow<Boolean> = _canDrawOverlays
    
    private val _batteryOptimizationExempt = MutableStateFlow(false)
    val batteryOptimizationExempt: StateFlow<Boolean> = _batteryOptimizationExempt

    val shizukuAvailable: Flow<Boolean> = shizukuStatus.map { it == ShizukuStatus.READY }

    val rootGranted: Flow<Boolean> = rootStatus.map { it == RootStatus.GRANTED }

    val rootUsable: Flow<Boolean> = rootStatus.map {
        it == RootStatus.GRANTED || it == RootStatus.AVAILABLE
    }

    val accessibilityAvailable: Flow<Boolean> = flow {
        val resolver = app.contentResolver
        val comp = ComponentName(
            app,
            com.inputleaf.android.inject.AccessibilityInputService::class.java
        )
        val shortName = comp.flattenToShortString()
        val fullName = comp.flattenToString()
        
        while (true) {
            val enabledServices = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            emit(enabledServices.contains(shortName) || enabledServices.contains(fullName))
            delay(2000)
        }
    }.distinctUntilChanged()

    val imeEnabledAndSelected: Flow<Boolean> = flow {
        val resolver = app.contentResolver
        val comp = ComponentName(
            app,
            com.inputleaf.android.inject.InputLeafIME::class.java
        )
        val shortName = comp.flattenToShortString()
        val fullName = comp.flattenToString()
        
        while (true) {
            val defaultIme = Settings.Secure.getString(
                resolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            ) ?: ""
            emit(defaultIme == shortName || defaultIme == fullName)
            delay(2000)
        }
    }.distinctUntilChanged()

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
        setupShizukuListeners()
        checkShizukuStatus()
        checkRootStatus()
        checkOverlayPermission()
        checkBatteryOptimization()
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
        _shizukuStatus.value = try {
            if (!Shizuku.pingBinder()) {
                // Check if Shizuku is installed
                val pm = app.packageManager
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
    
    fun checkRootStatus() {
        val availability = LibSuRootAccess.availability()
        _rootStatus.value = rootStatusFrom(availability)
        if (availability == RootAvailability.UNKNOWN) {
            reprobeRememberedGrant()
        }
    }

    /**
     * Resolve an [RootAvailability.UNKNOWN] that is only unknown because libsu has not
     * built a shell yet.
     *
     * `Shell.isAppGrantedRoot()` cannot tell "already granted" from "never asked" before
     * that, so a device where su was granted long ago still cold-starts as AVAILABLE and
     * shows a false "Setup Required". Creating the shell settles it, and on an already
     * granted app the su manager re-grants with no dialog — so this only runs once the
     * app has actually been granted before, and never prompts anyone out of the blue.
     */
    private fun reprobeRememberedGrant() {
        if (rootReprobeStarted) return
        rootReprobeStarted = true
        scope.launch(Dispatchers.IO) {
            if (!prefs.rootGrantRemembered.first()) return@launch
            val granted = runCatching { LibSuRootAccess.requestAccess() }.getOrDefault(false)
            Log.i("InputLeaf", "Silent root re-probe of remembered grant: granted=$granted")
            updateRootGrant(granted)
        }
    }

    fun rootAvailability(): RootAvailability = LibSuRootAccess.availability()

    fun requestRootAccess() {
        rootReprobeStarted = true
        scope.launch(Dispatchers.IO) {
            val granted = try {
                LibSuRootAccess.requestAccess()
            } catch (e: Exception) {
                Log.e("InputLeaf", "Error requesting root access", e)
                false
            }
            updateRootGrant(granted)
        }
    }

    /** Publishes the settled grant and remembers it so the next cold start can skip the tap. */
    private suspend fun updateRootGrant(granted: Boolean) {
        _rootStatus.value = if (granted) {
            RootStatus.GRANTED
        } else {
            val settled = rootStatusFrom(LibSuRootAccess.availability())
            if (settled == RootStatus.GRANTED) RootStatus.GRANTED else RootStatus.DENIED
        }
        // Clearing on a revoke matters as much as setting it: otherwise every later
        // launch would fire an su prompt the user has already turned down.
        runCatching { prefs.saveRootGrantRemembered(_rootStatus.value == RootStatus.GRANTED) }
    }

    private fun rootStatusFrom(availability: RootAvailability): RootStatus {
        return when (availability) {
            RootAvailability.MISSING -> RootStatus.MISSING
            RootAvailability.UNKNOWN -> RootStatus.AVAILABLE
            RootAvailability.DENIED -> RootStatus.DENIED
            RootAvailability.GRANTED -> RootStatus.GRANTED
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
        _canDrawOverlays.value = Settings.canDrawOverlays(app)
    }
    
    fun checkBatteryOptimization() {
        val powerManager = app.getSystemService(PowerManager::class.java)
        _batteryOptimizationExempt.value = powerManager.isIgnoringBatteryOptimizations(app.packageName)
    }

    fun cleanup() {
        scope.cancel()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
