package com.inputleaf.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputleaf.android.ui.theme.CustomShapes
import com.inputleaf.android.ui.theme.Gradients
import com.inputleaf.android.ui.theme.Purple500
import com.inputleaf.android.ui.theme.TextTertiary
import kotlin.math.roundToInt

data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun AnimatedBottomNavigation(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val density = LocalDensity.current
    var containerSize = remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    
    // Calculate indicator position fraction (0.0, 0.25, 0.5, 0.75 for 4 items)
    val indicatorPosition by animateFloatAsState(
        targetValue = selectedIndex / items.size.toFloat(),
        animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "indicator_position"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Container with shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { containerSize.value = it }
                .shadow(
                    elevation = 8.dp,
                    shape = CustomShapes.BottomNav,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
                .clip(CustomShapes.BottomNav)
                .background(Color.White.copy(alpha = 0.95f))
                .padding(8.dp)
        ) {
            // Animated background indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f / items.size)
                    .height(48.dp)
                    .offset(
                        x = with(density) {
                            (containerSize.value.width * indicatorPosition - 8.dp.toPx()).toDp()
                        }
                    )
                    .shadow(
                        elevation = 4.dp,
                        shape = CustomShapes.Pill,
                        ambientColor = Purple500.copy(alpha = 0.4f),
                        spotColor = Purple500.copy(alpha = 0.4f)
                    )
                    .clip(CustomShapes.Pill)
                    .background(Gradients.Primary)
            )

            // Nav items
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEachIndexed { index, item ->
                    NavItemView(
                        item = item,
                        isSelected = index == selectedIndex,
                        onClick = { onItemSelected(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItemView(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) Color.White else TextTertiary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else TextTertiary
        )
    }
}
