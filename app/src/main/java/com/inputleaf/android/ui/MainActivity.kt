package com.inputleaf.android.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.inputleaf.android.util.BatteryOptimizationHelper
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
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

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
