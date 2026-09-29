package com.inputleaf.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inputleaf.android.ui.ShizukuStatus

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShizukuStatusCard(
    status: ShizukuStatus,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    when (status) {
        ShizukuStatus.READY -> {
            Card(
                onClick = {
                    context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                        context.startActivity(it)
                    }
                },
                modifier = modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularAvatar(
                        icon = Icons.Rounded.CheckCircle,
                        size = 48.dp,
                        iconSize = 28.dp,
                        backgroundColor = MaterialTheme.colorScheme.tertiary,
                        iconTint = MaterialTheme.colorScheme.onTertiary,
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Shizuku Ready", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Input injection enabled", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        else -> {
            val icon: ImageVector?
            val title: String
            val description: String
            val actionLabel: String?
            val action: (() -> Unit)?

            when (status) {
                ShizukuStatus.CHECKING -> {
                    icon = null; title = "Checking Shizuku..."
                    description = ""; actionLabel = null; action = null
                }
                ShizukuStatus.NOT_INSTALLED -> {
                    icon = Icons.Rounded.Warning
                    title = "Shizuku Not Installed"
                    description = "Install Shizuku, or grant root on a rooted device, to enable system-level mouse and keyboard."
                    actionLabel = "Install Shizuku"
                    action = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, 
                            Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                    }
                }
                ShizukuStatus.NOT_RUNNING -> {
                    icon = Icons.Rounded.Warning
                    title = "Shizuku Not Running"
                    description = "Open Shizuku and start it via Wireless Debugging (Android 11+) or ADB. Rooted devices can grant su instead."
                    actionLabel = "Open Shizuku"
                    action = {
                        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                            context.startActivity(it)
                        }
                    }
                }
                ShizukuStatus.PERMISSION_REQUIRED -> {
                    icon = Icons.Rounded.Warning
                    title = "Permission Required"
                    description = "Grant Input Leaf permission to use Shizuku, or grant root, for system-level input."
                    actionLabel = "Grant Permission"; action = onRequestPermission
                }
            }
            
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            CircularAvatar(
                                icon = icon,
                                size = 40.dp,
                                iconSize = 24.dp,
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (actionLabel != null && action != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = action) {
                            Text(actionLabel)
                        }
                    }
                }
            }
        }
    }
}
