package com.inputleaf.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inputleaf.android.R
import com.inputleaf.android.ui.components.PermissionMethodsAccordion

@Composable
fun OnboardingScreen(
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
    onRequestImeSetup: () -> Unit,
    onRequestAccessibilityService: () -> Unit,
    onComplete: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 2

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(totalPages) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (index <= currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (currentPage) {
                    0 -> WelcomePage()
                    else -> PermissionMethodsPage(
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentPage > 0) {
                    OutlinedButton(onClick = { currentPage-- }) {
                        Text("Back")
                    }
                } else {
                    TextButton(onClick = onComplete) {
                        Text("Skip")
                    }
                }

                if (currentPage < totalPages - 1) {
                    Button(onClick = { currentPage++ }) {
                        Text("Next")
                    }
                } else {
                    Button(onClick = onComplete) {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionMethodsPage(
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
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Choose an input method",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick one method. Grant Shizuku or root for the system cursor and physical HID keyboard. Accessibility still works as a fallback. You do not need both Shizuku and root.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        PermissionMethodsAccordion(
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

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Image(
            painter = painterResource(id = R.drawable.ic_splash_logo),
            contentDescription = "Input Leaf Logo",
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to Input Leaf",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Share your computer's keyboard and mouse with your Android device — seamlessly.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                FeatureItem(Icons.Rounded.Mouse, "Mouse sharing", "Move your cursor from your PC to your phone")
                Spacer(modifier = Modifier.height(12.dp))
                FeatureItem(Icons.Rounded.Keyboard, "Keyboard sharing", "Type on your phone using your computer's keyboard")
                Spacer(modifier = Modifier.height(12.dp))
                FeatureItem(Icons.Rounded.Lock, "Secure connection", "TLS encrypted, trust-on-first-use")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Android extension of Input Leap — Open Source KVM",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureItem(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
