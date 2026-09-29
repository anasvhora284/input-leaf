package com.inputleaf.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inputleaf.android.ui.PermissionMethod
import com.inputleaf.android.ui.PermissionMethodAccordionState
import com.inputleaf.android.ui.activePermissionMethod
import com.inputleaf.android.ui.RootStatus
import com.inputleaf.android.ui.ShizukuStatus

@Composable
fun PermissionMethodsAccordion(
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
    modifier: Modifier = Modifier,
    activeMethod: PermissionMethod? = null,
) {
    var accordion by rememberSaveable(stateSaver = permissionMethodAccordionSaver) {
        mutableStateOf(PermissionMethodAccordionState())
    }

    val inUse = activePermissionMethod(
        active = activeMethod,
        shizukuReady = shizukuStatus == ShizukuStatus.READY,
        rootGranted = rootStatus == RootStatus.GRANTED,
        accessibilityAvailable = accessibilityAvailable,
    )
    LaunchedEffect(inUse) {
        accordion = accordion.onActiveMethodChanged(inUse)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PermissionMethodSection(
            title = "Shizuku",
            subtitle = methodSubtitle(shizukuMethodSubtitle(shizukuStatus), inUse == PermissionMethod.SHIZUKU),
            icon = Icons.Rounded.FlashOn,
            expanded = accordion.isExpanded(PermissionMethod.SHIZUKU),
            onHeaderClick = { accordion = accordion.onHeaderClick(PermissionMethod.SHIZUKU) },
        ) {
            ShizukuStatusCard(
                status = shizukuStatus,
                onRequestPermission = onRequestShizukuPermission,
            )
        }

        PermissionMethodSection(
            title = "Root",
            subtitle = methodSubtitle(rootMethodSubtitle(rootStatus), inUse == PermissionMethod.ROOT),
            icon = Icons.Rounded.Security,
            expanded = accordion.isExpanded(PermissionMethod.ROOT),
            onHeaderClick = { accordion = accordion.onHeaderClick(PermissionMethod.ROOT) },
        ) {
            if (rootStatus == RootStatus.MISSING || rootStatus == RootStatus.CHECKING) {
                RootUnavailableCard(status = rootStatus)
            } else {
                RootStatusCard(
                    status = rootStatus,
                    onRequestRootAccess = onRequestRootAccess,
                )
            }
        }

        PermissionMethodSection(
            title = "Non-root Accessibility",
            subtitle = methodSubtitle(
                accessibilityMethodSubtitle(accessibilityAvailable, canDrawOverlays, imeEnabledAndSelected),
                inUse == PermissionMethod.ACCESSIBILITY,
            ),
            icon = Icons.Rounded.Accessibility,
            expanded = accordion.isExpanded(PermissionMethod.ACCESSIBILITY),
            onHeaderClick = {
                accordion = accordion.onHeaderClick(PermissionMethod.ACCESSIBILITY)
            },
        ) {
            PermissionCard(
                icon = Icons.Rounded.Accessibility,
                title = "Accessibility Service",
                description = "Touch fallback when Shizuku or root cannot attach a native HID mouse",
                buttonLabel = "Enable",
                isGranted = accessibilityAvailable,
                onRequestPermission = onRequestAccessibilityService,
            )
            PermissionCard(
                icon = Icons.Rounded.Warning,
                title = "Virtual Keyboard",
                description = "Required for hardware shortcuts like Ctrl+C without Shizuku or root",
                buttonLabel = "Select Keyboard",
                isGranted = imeEnabledAndSelected,
                onRequestPermission = onRequestImeSetup,
            )
            PermissionCard(
                icon = Icons.Rounded.Warning,
                title = "Overlay Permission",
                description = "Required to show cursor overlay",
                isGranted = canDrawOverlays,
                onRequestPermission = onRequestOverlayPermission,
            )
        }

        PermissionCard(
            icon = Icons.Rounded.Warning,
            title = "Battery Optimization",
            description = "Go to: Battery usage → Allow background activity",
            buttonLabel = "Open App Info",
            isGranted = batteryOptimizationExempt,
            onRequestPermission = onRequestBatteryOptimization,
        )
    }
}

@Composable
private fun PermissionMethodSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stateLabel = if (expanded) "expanded" else "collapsed"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = title
                        stateDescription = stateLabel
                    }
                    .clickable(onClick = onHeaderClick)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularAvatar(
                    icon = icon,
                    size = 40.dp,
                    iconSize = 22.dp,
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun RootUnavailableCard(status: RootStatus) {
    val title = if (status == RootStatus.CHECKING) {
        "Checking for root…"
    } else {
        "Root not available"
    }
    val description = if (status == RootStatus.CHECKING) {
        "Looking for su on this device."
    } else {
        "No su binary was found. You can still use Shizuku or Accessibility."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularAvatar(
                    icon = Icons.Rounded.Warning,
                    size = 40.dp,
                    iconSize = 24.dp,
                    backgroundColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        }
    }
}

private fun shizukuMethodSubtitle(status: ShizukuStatus): String = when (status) {
    ShizukuStatus.READY -> "Ready"
    ShizukuStatus.NOT_INSTALLED -> "Not installed"
    ShizukuStatus.NOT_RUNNING -> "Not running"
    ShizukuStatus.PERMISSION_REQUIRED -> "Permission needed"
    ShizukuStatus.CHECKING -> "Checking…"
}

private fun rootMethodSubtitle(status: RootStatus): String = when (status) {
    RootStatus.GRANTED -> "Ready"
    RootStatus.AVAILABLE -> "Grant su"
    RootStatus.DENIED -> "Denied"
    RootStatus.MISSING -> "Not available"
    RootStatus.CHECKING -> "Checking…"
}

private fun accessibilityMethodSubtitle(
    accessibilityAvailable: Boolean,
    canDrawOverlays: Boolean,
    imeEnabledAndSelected: Boolean,
): String {
    val granted = listOf(accessibilityAvailable, canDrawOverlays, imeEnabledAndSelected).count { it }
    return if (granted == 3) "Ready" else "$granted of 3 set up"
}

private fun methodSubtitle(base: String, inUse: Boolean): String =
    if (inUse) "$base · in use" else base

private val permissionMethodAccordionSaver =
    Saver<PermissionMethodAccordionState, String>(
        save = { "${it.expanded.name}|${it.userChosen}" },
        restore = {
            val parts = it.split("|")
            PermissionMethodAccordionState(
                expanded = PermissionMethod.valueOf(parts[0]),
                userChosen = parts.getOrNull(1).toBoolean(),
            )
        },
    )
