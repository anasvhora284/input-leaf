package com.inputleaf.android.privilege

enum class PrivilegeKind {
    SHIZUKU,
    ROOT,
    NONE,
}

enum class RootAvailability {
    MISSING,
    UNKNOWN,
    DENIED,
    GRANTED,
    ;

    fun canUse(): Boolean = this == UNKNOWN || this == GRANTED
}

data class PrivilegeSnapshot(
    val shizukuReady: Boolean,
    val root: RootAvailability,
)

enum class ResolvedInjector {
    PRIVILEGED_SHIZUKU,
    PRIVILEGED_ROOT,
    ACCESSIBILITY,
    NONE,
}
