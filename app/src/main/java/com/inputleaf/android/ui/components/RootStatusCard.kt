package com.inputleaf.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inputleaf.android.ui.RootStatus

@Composable
fun RootStatusCard(
    status: RootStatus,
    onRequestRootAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (status) {
        RootStatus.MISSING, RootStatus.CHECKING -> return
        RootStatus.GRANTED -> {
            GradientCard(
                modifier = modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                cornerRadius = 24.dp,
                elevation = 0.dp,
                padding = 20.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularAvatar(
                        icon = Icons.Rounded.CheckCircle,
                        size = 48.dp,
                        iconSize = 28.dp,
                        backgroundColor = MaterialTheme.colorScheme.tertiary,
                        iconTint = MaterialTheme.colorScheme.onTertiary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Root Ready",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "System cursor and HID keyboard can use su instead of Shizuku",
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        RootStatus.AVAILABLE, RootStatus.DENIED -> {
            val icon: ImageVector = if (status == RootStatus.DENIED) {
                Icons.Default.Warning
            } else {
                Icons.Rounded.Security
            }
            val color: Color = MaterialTheme.colorScheme.secondary
            val title = if (status == RootStatus.DENIED) "Root Denied" else "Root Available"
            val description = if (status == RootStatus.DENIED) {
                "Input Leaf was not granted su. You can still use Shizuku or Accessibility."
            } else {
                "Grant the su prompt to use the system cursor and physical HID keyboard without Shizuku."
            }
            GradientCard(
                modifier = modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                cornerRadius = 24.dp,
                elevation = 0.dp,
                padding = 20.dp,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularAvatar(
                            icon = icon,
                            size = 40.dp,
                            iconSize = 24.dp,
                            backgroundColor = color.copy(alpha = 0.1f),
                            iconTint = color,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRequestRootAccess) {
                        Text("Grant root", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
