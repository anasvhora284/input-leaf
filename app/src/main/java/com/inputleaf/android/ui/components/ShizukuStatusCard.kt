package com.inputleaf.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import com.inputleaf.android.ui.MainViewModel
import com.inputleaf.android.ui.theme.Gradients
import com.inputleaf.android.ui.theme.Purple600
import com.inputleaf.android.ui.theme.Success500
import com.inputleaf.android.ui.theme.Success600
import com.inputleaf.android.ui.theme.TextPrimary
import com.inputleaf.android.ui.theme.TextSecondary
import com.inputleaf.android.ui.theme.Warning500

@Composable
fun ShizukuStatusCard(
    status: MainViewModel.ShizukuStatus,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    when (status) {
        MainViewModel.ShizukuStatus.READY -> {
            GradientCard(
                modifier = modifier.fillMaxWidth(),
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
                        iconTint = Success500
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shizuku Ready",
                            fontWeight = FontWeight.SemiBold,
                            color = Success600,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Input injection enabled",
                            color = Success600.copy(alpha = 0.8f),
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
                    icon = Icons.Default.Warning; color = MaterialTheme.colorScheme.error
                    title = "Shizuku Not Installed"
                    description = "Install Shizuku from Play Store to enable mouse/keyboard input."
                    actionLabel = "Install Shizuku"
                    action = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, 
                            Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                    }
                }
                MainViewModel.ShizukuStatus.NOT_RUNNING -> {
                    icon = Icons.Default.Warning; color = Warning500
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
                    icon = Icons.Default.Warning; color = Warning500
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
                modifier = modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surface,
                cornerRadius = 24.dp,
                elevation = 1.dp,
                padding = 20.dp
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            CircularAvatar(
                                icon = icon,
                                size = 40.dp,
                                iconSize = 24.dp,
                                backgroundColor = color.copy(alpha = 0.1f),
                                iconTint = color
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = description,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (actionLabel != null && action != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = action) {
                            Text(actionLabel, color = Purple600)
                        }
                    }
                }
            }
        }
    }
}
