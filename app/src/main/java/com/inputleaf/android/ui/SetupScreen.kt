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
import com.inputleaf.android.ui.theme.Gradients

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    shizukuStatus: MainViewModel.ShizukuStatus,
    onRequestShizukuPermission: () -> Unit
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
                    text = "Shizuku Setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Shizuku is required for mouse and keyboard input injection on Android.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                ShizukuStatusCard(shizukuStatus, onRequestShizukuPermission)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Setup Steps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        SetupStep(
                            number = "1",
                            title = "Install Shizuku",
                            description = "Download Shizuku from Play Store"
                        )
                        SetupStep(
                            number = "2",
                            title = "Start Shizuku",
                            description = "Open Shizuku and start it via Wireless Debugging (Android 11+) or ADB"
                        )
                        SetupStep(
                            number = "3",
                            title = "Grant Permission",
                            description = "Allow Input-Leaf to use Shizuku for input injection"
                        )
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

@Composable
private fun ShizukuStatusCard(
    status: MainViewModel.ShizukuStatus,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    
    when (status) {
        MainViewModel.ShizukuStatus.READY -> {
            GradientCard(
                modifier = Modifier.fillMaxWidth(),
                gradient = Gradients.Success,
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
                        icon = Icons.Rounded.CheckCircle,
                        size = 48.dp,
                        iconSize = 28.dp,
                        backgroundColor = Color.White,
                        iconTint = Color(0xFF10B981)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shizuku Ready",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF065F46),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Input injection enabled",
                            color = Color(0xFF047857),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        else -> {
            val icon: ImageVector?
            val color: Color
            val title: String
            val description: String
            val actionLabel: String?
            val action: (() -> Unit)?

            when (status) {
                MainViewModel.ShizukuStatus.CHECKING -> {
                    icon = null; color = Color.Gray; title = "Checking Shizuku..."
                    description = ""; actionLabel = null; action = null
                }
                MainViewModel.ShizukuStatus.NOT_INSTALLED -> {
                    icon = Icons.Default.Warning; color = Color(0xFFF44336)
                    title = "Shizuku Not Installed"
                    description = "Install Shizuku from Play Store to enable mouse/keyboard input."
                    actionLabel = "Install Shizuku"
                    action = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, 
                            Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                    }
                }
                MainViewModel.ShizukuStatus.NOT_RUNNING -> {
                    icon = Icons.Default.Warning; color = Color(0xFFFF9800)
                    title = "Shizuku Not Running"
                    description = "Open Shizuku app and start it via Wireless Debugging (Android 11+) or ADB."
                    actionLabel = "Open Shizuku"
                    action = {
                        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                            context.startActivity(it)
                        }
                    }
                }
                MainViewModel.ShizukuStatus.PERMISSION_REQUIRED -> {
                    icon = Icons.Default.Warning; color = Color(0xFFFF9800)
                    title = "Permission Required"
                    description = "Grant Input-Leaf permission to use Shizuku for input injection."
                    actionLabel = "Grant Permission"; action = onRequestPermission
                }
                else -> {
                    icon = null; color = Color.Gray; title = "Unknown"
                    description = ""; actionLabel = null; action = null
                }
            }
            
            GradientCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color.White,
                cornerRadius = 24.dp,
                elevation = 1.dp,
                padding = 20.dp
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            CircularAvatar(
                                icon = icon,
                                size = 48.dp,
                                iconSize = 24.dp,
                                backgroundColor = color.copy(alpha = 0.1f),
                                iconTint = color
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (actionLabel != null && action != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = action,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(actionLabel)
                        }
                    }
                }
            }
        }
    }
}
