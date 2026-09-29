package com.inputleaf.android.ui

import android.Manifest
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
