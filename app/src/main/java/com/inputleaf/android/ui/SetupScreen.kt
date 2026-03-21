package com.inputleaf.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inputleaf.android.ui.components.CircularAvatar
import com.inputleaf.android.ui.components.GradientCard
import com.inputleaf.android.ui.components.ShizukuStatusCard
import com.inputleaf.android.ui.theme.Gradients
import com.inputleaf.android.ui.theme.Success500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    shizukuStatus: MainViewModel.ShizukuStatus,
    canDrawOverlays: Boolean,
    batteryOptimizationExempt: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit
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
                    description = "Allow background activity",
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
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    GradientCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (isGranted) MaterialTheme.colorScheme.surface 
                         else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        cornerRadius = 24.dp,
        elevation = 1.dp,
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
                backgroundColor = if (isGranted) Success500.copy(alpha = 0.1f) 
                                 else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                iconTint = if (isGranted) Success500 else MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isGranted) {
                Button(onClick = onRequestPermission) {
                    Text("Grant")
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
