package com.inputleaf.android.ui

/**
 * Input setup methods shown in the permissions accordion.
 * Order matches the UI: Shizuku, Root, then non-root Accessibility.
 */
enum class PermissionMethod {
    SHIZUKU,
    ROOT,
    ACCESSIBILITY,
}

/**
 * The method the accordion should open on when the user has not picked one.
 *
 * [active] is the method a live session is actually injecting through. With no session,
 * this falls back to whichever method would be chosen right now, in the same order
 * [com.inputleaf.android.privilege.PrivilegeSelector] uses, so the open card always
 * matches what the app would really do.
 *
 * @return null when nothing is set up yet, leaving the caller's own default in place.
 */
fun activePermissionMethod(
    active: PermissionMethod?,
    shizukuReady: Boolean,
    rootGranted: Boolean,
    accessibilityAvailable: Boolean,
): PermissionMethod? {
    if (active != null) return active
    return when {
        shizukuReady -> PermissionMethod.SHIZUKU
        rootGranted -> PermissionMethod.ROOT
        accessibilityAvailable -> PermissionMethod.ACCESSIBILITY
        else -> null
    }
}

/**
 * Exclusive accordion: exactly one method is expanded.
 *
 * Opens on the method actually in use, but stops following it the moment the user
 * taps a header — otherwise connecting or losing Shizuku mid-read would yank the
 * card out from under them.
 */
data class PermissionMethodAccordionState(
    val expanded: PermissionMethod = PermissionMethod.SHIZUKU,
    val userChosen: Boolean = false,
) {
    fun onHeaderClick(method: PermissionMethod): PermissionMethodAccordionState {
        return copy(expanded = method, userChosen = true)
    }

    /** Follows the in-use method until the user expresses a preference. */
    fun onActiveMethodChanged(method: PermissionMethod?): PermissionMethodAccordionState {
        if (userChosen || method == null || method == expanded) return this
        return copy(expanded = method)
    }

    fun isExpanded(method: PermissionMethod): Boolean = expanded == method
}
