package com.inputleaf.android.service

/**
 * How the overlay cursor looks (#71). Only Accessibility mode draws it; Shizuku and root
 * use Android's own pointer, which has its own settings.
 *
 * [fill] and [outline] colour the Android-style arrow; the Input Leaf cursor keeps its colours.
 */
data class CursorLook(
    val style: String = "default",
    val size: CursorSize = CursorSize.NORMAL,
    val fill: CursorColor = CursorColor.BLACK,
    val outline: CursorColor = CursorColor.WHITE,
) {
    val hotspot: CursorHotspot get() = CursorHotspot.forStyle(style)

    fun windowSizePx(density: Float): Int = CursorHotspot.windowSizePx(density * size.scale)
}

enum class CursorSize(val storageValue: String, val label: String, val scale: Float) {
    SMALL("small", "Small", 0.75f),
    NORMAL("normal", "Normal", 1f),
    LARGE("large", "Large", 1.5f),
    EXTRA_LARGE("xlarge", "Extra large", 2f);

    companion object {
        fun fromStorage(value: String?): CursorSize = entries.firstOrNull { it.storageValue == value } ?: NORMAL
    }
}

/** Arrow colours, close to the pointer fill choices in Android's own mouse settings. */
enum class CursorColor(val storageValue: String, val label: String, val argb: Int) {
    BLACK("black", "Black", 0xFF000000.toInt()),
    WHITE("white", "White", 0xFFFFFFFF.toInt()),
    GREEN("green", "Green", 0xFF34A853.toInt()),
    YELLOW("yellow", "Yellow", 0xFFFBBC04.toInt()),
    PINK("pink", "Pink", 0xFFF06292.toInt()),
    BLUE("blue", "Blue", 0xFF4285F4.toInt());

    companion object {
        /** Outline choices, as in Android's settings: a light or dark border. */
        val OUTLINES = listOf(WHITE, BLACK)

        fun fromStorage(value: String?, default: CursorColor): CursorColor =
            entries.firstOrNull { it.storageValue == value } ?: default
    }
}
