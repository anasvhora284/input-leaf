package com.inputleaf.android.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputleaf.android.R

@Composable
fun OnboardingScreen(
    shizukuStatus: ShizukuStatus,
    accessibilityAvailable: Boolean,
    canDrawOverlays: Boolean,
    batteryOptimizationExempt: Boolean,
    imeEnabledAndSelected: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestImeSetup: () -> Unit,
    onComplete: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 6
    val context = LocalContext.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Progress indicator
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

            // Page content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (currentPage) {
                    0 -> WelcomePage()
                    1 -> PermissionPage(
                        icon = Icons.Rounded.Security,
                        title = "Shizuku Setup (Optional)",
                        description = "Shizuku lets Input Leaf inject mouse & keyboard events at the system level — the most powerful input method. Skip if you prefer Accessibility Service instead.",
                        whyNeeded = "Shizuku provides low-latency, root-equivalent input injection without actually rooting your device. If Shizuku is unavailable, you can use the Accessibility Service on the next page.",
                        isGranted = shizukuStatus == ShizukuStatus.READY,
                        statusText = when (shizukuStatus) {
                            ShizukuStatus.READY -> "Ready ✓"
                            ShizukuStatus.NOT_INSTALLED -> "Not installed"
                            ShizukuStatus.NOT_RUNNING -> "Not running"
                            ShizukuStatus.PERMISSION_REQUIRED -> "Permission needed"
                            ShizukuStatus.CHECKING -> "Checking..."
                        },
                        actionLabel = when (shizukuStatus) {
                            ShizukuStatus.NOT_INSTALLED -> "Install Shizuku"
                            ShizukuStatus.NOT_RUNNING -> "Open Shizuku"
                            ShizukuStatus.PERMISSION_REQUIRED -> "Grant Permission"
                            else -> null
                        },
                        onAction = when (shizukuStatus) {
                            ShizukuStatus.NOT_INSTALLED -> ({
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                            })
                            ShizukuStatus.NOT_RUNNING -> ({
                                context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                                    context.startActivity(it)
                                }
                                Unit
                            })
                            ShizukuStatus.PERMISSION_REQUIRED -> onRequestShizukuPermission
                            else -> ({})
                        }
                    )
                    2 -> PermissionPage(
                        icon = Icons.Rounded.Accessibility,
                        title = "Accessibility Service",
                        description = "Enable Input Leaf's Accessibility Service — a no-root, no-Shizuku way to inject touch events. Works on any Android device.",
                        whyNeeded = "The Accessibility Service lets Input Leaf simulate taps without Shizuku. It works on stock Android with zero extra apps.",
                        isGranted = accessibilityAvailable,
                        statusText = if (accessibilityAvailable) "Enabled ✓" else "Disabled",
                        actionLabel = if (!accessibilityAvailable) "Open Accessibility Settings" else null,
                        onAction = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                val comp = ComponentName(
                                    context.packageName,
                                    com.inputleaf.android.inject.AccessibilityInputService::class.java.name
                                )
                                putExtra(":settings:show_fragment_args",
                                    android.os.Bundle().apply { putString(":settings:fragment_args_key", comp.flattenToString()) })
                            }
                            context.startActivity(intent)
                        }
                    )
                    3 -> PermissionPage(
                        icon = Icons.Default.Warning,
                        title = "Virtual Keyboard",
                        description = "Required to inject hardware keyboard shortcuts like Ctrl+C and Alt+Tab.",
                        whyNeeded = "To fully mimic a physical keyboard without Shizuku, Input Leaf uses a custom Virtual Keyboard. You need to enable it and set it as your active keyboard.",
                        isGranted = imeEnabledAndSelected,
                        statusText = if (imeEnabledAndSelected) "Selected ✓" else "Not selected",
                        actionLabel = if (!imeEnabledAndSelected) "Select Keyboard" else null,
                        onAction = onRequestImeSetup
                    )
                    4 -> PermissionPage(
                        icon = Icons.Rounded.Visibility,
                        title = "Overlay Permission",
                        description = "Allows Input Leaf to display a cursor on your screen when your computer's mouse moves to this device.",
                        whyNeeded = "Android requires explicit permission to draw over other apps. This is needed to show the cursor overlay so you can see where the mouse pointer is on your phone.",
                        isGranted = canDrawOverlays,
                        statusText = if (canDrawOverlays) "Granted ✓" else "Not granted",
                        actionLabel = if (!canDrawOverlays) "Grant Permission" else null,
                        onAction = onRequestOverlayPermission
                    )
                    5 -> PermissionPage(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = "Battery Optimization",
                        description = "Prevents Android from killing the connection when your phone goes to sleep.\n\nGo to: Battery usage → Allow background activity",
                        whyNeeded = "Android aggressively kills background apps to save battery. Exempting Input Leaf ensures your KVM connection stays alive even when the screen is off.",
                        isGranted = batteryOptimizationExempt,
                        statusText = if (batteryOptimizationExempt) "Exempted ✓" else "Not exempted",
                        actionLabel = if (!batteryOptimizationExempt) "Open App Info" else null,
                        onAction = onRequestBatteryOptimization
                    )
                }
            }

            // Navigation buttons
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
private fun WelcomePage() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Image(
            painter = painterResource(id = R.drawable.ic_logo),
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

@Composable
private fun PermissionPage(
    icon: ImageVector,
    title: String,
    description: String,
    whyNeeded: String,
    isGranted: Boolean,
    statusText: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    var showWhy by remember { mutableStateOf(false) }
    
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.tertiaryContainer
                      else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "bg_color"
    )
    val iconTintColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.onTertiaryContainer
                      else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "icon_tint_color"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Icon with status
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = bgColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isGranted) Icons.Rounded.CheckCircle else icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = iconTintColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status chip
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = statusText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = iconTintColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // "Why do we need this?" expandable
        TextButton(onClick = { showWhy = !showWhy }) {
            Icon(
                imageVector = if (showWhy) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Why do we need this?")
        }

        AnimatedVisibility(
            visible = showWhy,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = whyNeeded,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action button
        if (actionLabel != null) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel)
            }
        } else if (isGranted) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "All set!",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
