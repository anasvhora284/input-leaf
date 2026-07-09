package com.inputleaf.android.inject

object InputMethodResolver {
    fun resolve(
        preferredMethod: String,
        isShizukuAvailable: Boolean,
        isAccessibilityAvailable: Boolean
    ): ResolvedMethod {
        return when (preferredMethod) {
            "shizuku" -> if (isShizukuAvailable) ResolvedMethod.SHIZUKU else ResolvedMethod.NONE
            "accessibility" -> if (isAccessibilityAvailable) ResolvedMethod.ACCESSIBILITY else ResolvedMethod.NONE
            "auto" -> {
                if (isShizukuAvailable) ResolvedMethod.SHIZUKU
                else if (isAccessibilityAvailable) ResolvedMethod.ACCESSIBILITY
                else ResolvedMethod.NONE
            }
            else -> ResolvedMethod.NONE
        }
    }
}

enum class ResolvedMethod {
    SHIZUKU, ACCESSIBILITY, NONE
}
