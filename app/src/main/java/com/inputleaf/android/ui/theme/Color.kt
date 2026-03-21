package com.inputleaf.android.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary colors (purple/lavender theme)
val Purple300 = Color(0xFFC4B5FD)
val Purple400 = Color(0xFFA78BFA)
val Purple500 = Color(0xFF8B5CF6)
val Purple600 = Color(0xFF7C3AED)
val Purple700 = Color(0xFF6D28D9)
val Purple100 = Color(0xFFDDD6FE)
val Purple50 = Color(0xFFE9D5FF)

// Semantic colors
val Success400 = Color(0xFFA7F3D0)
val Success500 = Color(0xFF10B981)
val Success600 = Color(0xFF059669)
val Success100 = Color(0xFFD1FAE5)

// Surface colors
val Surface = Color(0xFFFAFAFA)
val SurfaceVariant = Color(0xFFF5F5F5)
val Background = Color(0xFFF0F0F5)

// Text colors
val TextPrimary = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF666666)
val TextTertiary = Color(0xFF999999)

// Gradients
object Gradients {
    val Primary = Brush.linearGradient(
        colors = listOf(Purple400, Purple500)
    )
    
    val Accent = Brush.linearGradient(
        colors = listOf(Purple100, Purple50)
    )
    
    val Success = Brush.linearGradient(
        colors = listOf(Success100, Success400)
    )
}
