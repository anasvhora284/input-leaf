package com.inputleaf.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val InputLeafShapes = Shapes(
    // Buttons and small elements
    small = RoundedCornerShape(20.dp),
    // Cards and containers
    medium = RoundedCornerShape(28.dp),
    // Large cards and sheets
    large = RoundedCornerShape(32.dp)
)

// Custom shapes for specific components
object CustomShapes {
    val BottomNav = RoundedCornerShape(32.dp)
    val Card = RoundedCornerShape(28.dp)
    val CardLarge = RoundedCornerShape(32.dp)
    val Button = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(24.dp)
    val Toggle = RoundedCornerShape(16.dp)
}
