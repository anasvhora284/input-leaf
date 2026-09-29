package com.inputleaf.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.inputleaf.android.ui.components.PermissionMethodsAccordion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    shizukuStatus: ShizukuStatus,
    rootStatus: RootStatus,
    accessibilityAvailable: Boolean,
    canDrawOverlays: Boolean,
    batteryOptimizationExempt: Boolean,
    imeEnabledAndSelected: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onRequestRootAccess: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestAccessibilityService: () -> Unit,
    onRequestImeSetup: () -> Unit,
    activeMethod: PermissionMethod? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Setup") },
                subtitle = { Text("Permissions") },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Pick one input method. Grant Shizuku or root for the system cursor and physical HID keyboard. Accessibility still works as a fallback. You do not need both Shizuku and root.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                PermissionMethodsAccordion(
                    activeMethod = activeMethod,
                    shizukuStatus = shizukuStatus,
                    rootStatus = rootStatus,
                    accessibilityAvailable = accessibilityAvailable,
                    canDrawOverlays = canDrawOverlays,
                    batteryOptimizationExempt = batteryOptimizationExempt,
                    imeEnabledAndSelected = imeEnabledAndSelected,
                    onRequestShizukuPermission = onRequestShizukuPermission,
                    onRequestRootAccess = onRequestRootAccess,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestBatteryOptimization = onRequestBatteryOptimization,
                    onRequestAccessibilityService = onRequestAccessibilityService,
                    onRequestImeSetup = onRequestImeSetup,
                )
            }
        }
    }
}
