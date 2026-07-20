package com.inputleaf.android.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Input Leaf brand (teal + leaf green)
val TealDeep = Color(0xFF0C3244)
val TealNavy = Color(0xFF002F35)
val TealDark = Color(0xFF001A20)
val LeafLight = Color(0xFFAEEA00)
val LeafBright = Color(0xFF76FF03)
val LeafGreen = Color(0xFF2E7D32)
val LeafForest = Color(0xFF1B5E20)
val MistGreen = Color(0xFF7CB8A8)
val MistGreenDim = Color(0xFF5A9A8A)

// Legacy aliases (purple) — migrate callers to brand greens
val Purple300 = LeafLight
val Purple400 = LeafBright
val Purple500 = LeafGreen
val Purple600 = LeafForest
val Purple700 = LeafForest
val Purple100 = Color(0xFFC8E6C9)
val Purple50 = Color(0xFFE8F5E9)

// Semantic colors
val Success400 = Color(0xFFA7F3D0)
val Success500 = Color(0xFF10B981)
val Success600 = Color(0xFF059669)
val Success100 = Color(0xFFD1FAE5)
val Warning500 = Color(0xFFFF9800)
val Warning600 = Color(0xFFF57C00)

// Surface colors
val Surface = Color(0xFFF4F8F7)
val SurfaceVariant = Color(0xFFE8F0EE)
val Background = Color(0xFFEEF4F2)

// Text colors
val TextPrimary = Color(0xFF0D1F1A)
val TextSecondary = Color(0xFF4A635C)
val TextTertiary = Color(0xFF7A948C)

// Gradients
object Gradients {
    val Primary = Brush.linearGradient(
        colors = listOf(LeafBright, LeafGreen),
    )

    val Accent = Brush.linearGradient(
        colors = listOf(MistGreen, Color(0xFFB2DFDB)),
    )

    val Success = Brush.linearGradient(
        colors = listOf(Success100, Success400),
    )

    val Splash = Brush.verticalGradient(
        colors = listOf(TealNavy, TealDeep, TealDark),
    )
}
