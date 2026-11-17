package com.inputleaf.android.ui

import android.Manifest
import android.content.Intent
import com.inputleaf.android.model.ConnectionState
import com.inputleaf.android.service.ACTION_QUICK_CONNECT
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import com.inputleaf.android.ui.theme.InputLeafTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        viewModel.scan()
        handleQuickConnect(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorState.collect { error ->
                    error?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState(initial = "SYSTEM")

            val isDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            DisposableEffect(isDarkTheme) {
                val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDarkTheme }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                onDispose {}
            }

            InputLeafTheme(darkTheme = isDarkTheme) {
                Surface(Modifier.fillMaxSize()) {
                    LeafNavigation(viewModel)
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickConnect(intent)
    }

    /**
     * Quick Settings tile tap (#81): connect to the last server, then go back to whatever
     * the user was doing once it's up. On failure the app stays so the error is visible.
     */
    private fun handleQuickConnect(intent: Intent?) {
        if (intent?.action != ACTION_QUICK_CONNECT) return
        intent.action = null
        lifecycleScope.launch {
            if (!viewModel.connectToLastServer()) return@launch
            withTimeoutOrNull(30_000) {
                viewModel.connectionState.first { it is ConnectionState.Idle || it is ConnectionState.Active }
            }?.let { moveTaskToBack(true) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check Shizuku status when returning to app
        viewModel.checkShizukuStatus()
        viewModel.checkRootStatus()
        // Re-check overlay permission (user may have granted it in settings)
        viewModel.checkOverlayPermission()
        // Re-check battery optimization
        viewModel.checkBatteryOptimization()
    }
}
