package com.inputleaf.android.privilege

/**
 * Chooses how Input Leaf opens `/dev/uhid` and injects events.
 *
 * Preference is Shizuku (already working) then root (`su`), then none.
 * Accessibility is not a privilege path; the caller may still use it as
 * overlay/IME fallback when this returns [PrivilegeKind.NONE].
 */
object PrivilegeSelector {
    fun select(snapshot: PrivilegeSnapshot): PrivilegeKind {
        return when {
            snapshot.shizukuReady -> PrivilegeKind.SHIZUKU
            snapshot.root.canUse() -> PrivilegeKind.ROOT
            else -> PrivilegeKind.NONE
        }
    }
}

object InjectorMethodResolver {
    fun resolve(
        method: String,
        snapshot: PrivilegeSnapshot,
        accessibilityAvailable: Boolean,
    ): ResolvedInjector {
        return when (method) {
            "shizuku" ->
                if (snapshot.shizukuReady) {
                    ResolvedInjector.PRIVILEGED_SHIZUKU
                } else {
                    ResolvedInjector.NONE
                }
            "root" ->
                if (snapshot.root.canUse()) {
                    ResolvedInjector.PRIVILEGED_ROOT
                } else {
                    ResolvedInjector.NONE
                }
            "accessibility" ->
                if (accessibilityAvailable) {
                    ResolvedInjector.ACCESSIBILITY
                } else {
                    ResolvedInjector.NONE
                }
            else -> when (PrivilegeSelector.select(snapshot)) {
                PrivilegeKind.SHIZUKU -> ResolvedInjector.PRIVILEGED_SHIZUKU
                PrivilegeKind.ROOT -> ResolvedInjector.PRIVILEGED_ROOT
                PrivilegeKind.NONE ->
                    if (accessibilityAvailable) {
                        ResolvedInjector.ACCESSIBILITY
                    } else {
                        ResolvedInjector.NONE
                    }
            }
        }
    }
}
