package com.inputleaf.android.inject

/**
 * Native (UHID / AOSP) pointer availability for overlay arbitration.
 *
 * [PENDING] and [ACTIVE] must hide Input Leaf's overlay so it cannot sit on top of
 * the system pointer. [FALLBACK] is the only on-screen state that may show the overlay.
 */
enum class NativePointerState {
    /** Off-screen, disconnected, or no native pointer path. */
    NONE,

    /** UHID mouse attach is in flight — hide overlay to avoid a dual-cursor flash. */
    PENDING,

    /** Native pointer is live. */
    ACTIVE,

    /** UHID mouse failed or timed out; overlay may be used if the user enabled it. */
    FALLBACK,
}
