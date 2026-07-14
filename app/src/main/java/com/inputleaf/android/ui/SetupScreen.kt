package com.inputleaf.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inputleaf.android.ui.components.CircularAvatar
import com.inputleaf.android.ui.components.GradientCard
import com.inputleaf.android.ui.components.ShizukuStatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    shizukuStatus: ShizukuStatus,
    accessibilityAvailable: Boolean,
    canDrawOverlays: Boolean,
    batteryOptimizationExempt: Boolean,
    imeEnabledAndSelected: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestAccessibilityService: () -> Unit,
    onRequestImeSetup: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup") }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Grant all required permissions to use InputLeaf.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                ShizukuStatusCard(
                    status = shizukuStatus,
                    onRequestPermission = onRequestShizukuPermission
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Rounded.Accessibility,
                    title = "Accessibility Service",
                    description = "Rootless touch & keyboard injection — works without Shizuku",
                    buttonLabel = "Enable",
                    isGranted = accessibilityAvailable,
                    onRequestPermission = onRequestAccessibilityService
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Default.Warning,
                    title = "Virtual Keyboard",
                    description = "Required for hardware shortcuts like Ctrl+C without Shizuku",
                    buttonLabel = "Select Keyboard",
                    isGranted = imeEnabledAndSelected,
                    onRequestPermission = onRequestImeSetup
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Default.Warning,
                    title = "Overlay Permission",
                    description = "Required to show cursor overlay",
                    isGranted = canDrawOverlays,
                    onRequestPermission = onRequestOverlayPermission
                )
            }

            item {
                PermissionCard(
                    icon = Icons.Default.Warning,
                    title = "Battery Optimization",
                    description = "Go to: Battery usage → Allow background activity",
                    buttonLabel = "Open App Info",
                    isGranted = batteryOptimizationExempt,
                    onRequestPermission = onRequestBatteryOptimization
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String = "Grant",
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.tertiaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "bg_color"
    )
    val avatarBgColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.tertiary
                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
        animationSpec = tween(300),
        label = "avatar_bg_color"
    )
    val iconTintColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.onTertiary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "icon_tint_color"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.onTertiaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "title_color"
    )
    val descColor by animateColorAsState(
        targetValue = if (isGranted) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        animationSpec = tween(300),
        label = "desc_color"
    )

    GradientCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRequestPermission() }
            .animateContentSize(
                animationSpec = tween(300)
            ),
        backgroundColor = bgColor,
        cornerRadius = 24.dp,
        elevation = 0.dp,
        padding = 20.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularAvatar(
                icon = if (isGranted) Icons.Rounded.CheckCircle else icon,
                size = 48.dp,
                iconSize = 24.dp,
                backgroundColor = avatarBgColor,
                iconTint = iconTintColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = descColor
                )
                AnimatedVisibility(
                    visible = !isGranted,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(buttonLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
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
