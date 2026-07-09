package com.inputleaf.android.ui

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    CHECKING,
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    READY
}

class PermissionStatusProvider(private val app: Application) {

    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.CHECKING)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus
    
    private val _canDrawOverlays = MutableStateFlow(false)
    val canDrawOverlays: StateFlow<Boolean> = _canDrawOverlays
    
    private val _batteryOptimizationExempt = MutableStateFlow(false)
    val batteryOptimizationExempt: StateFlow<Boolean> = _batteryOptimizationExempt

    val shizukuAvailable: Flow<Boolean> = shizukuStatus.map { it == ShizukuStatus.READY }

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
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
