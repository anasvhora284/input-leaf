package com.inputleaf.android.model

enum class WireProtocol(val magic: String) {
    BARRIER("Barrier"),
    SYNERGY("Synergy"),
}

sealed class InputLeapEvent {
    // Handshake
    data class Hello(
        val majorVersion: Int,
        val minorVersion: Int,
        val serverName: String,
        val protocol: WireProtocol = WireProtocol.BARRIER,
    ) : InputLeapEvent()
    data class QueryInfo(val dummy: Unit = Unit) : InputLeapEvent()
    // Control
    data class Enter(val x: Int, val y: Int, val seqNum: Int, val mask: Int) : InputLeapEvent()
    object Leave : InputLeapEvent()
    object KeepAlive : InputLeapEvent()
    object ResetOptions : InputLeapEvent()
    // Keyboard
    data class KeyDown(val keyId: Int, val mask: Int, val scancode: Int) : InputLeapEvent()
    data class KeyUp(val keyId: Int, val mask: Int, val scancode: Int) : InputLeapEvent()
    data class KeyRepeat(val keyId: Int, val mask: Int, val count: Int, val scancode: Int) : InputLeapEvent()
    // Mouse
    data class MouseMoveAbs(val x: Int, val y: Int) : InputLeapEvent()
    data class MouseMoveRel(val dx: Int, val dy: Int) : InputLeapEvent()
    data class MouseDown(val buttonId: Int) : InputLeapEvent()
    data class MouseUp(val buttonId: Int) : InputLeapEvent()
    data class MouseWheel(val xDelta: Int, val yDelta: Int) : InputLeapEvent()
    // Errors
    data class Incompatible(val major: Int, val minor: Int) : InputLeapEvent()
    object Busy : InputLeapEvent()
    object Unknown : InputLeapEvent()
    object BadMessage : InputLeapEvent()
    // Fallback
    data class Unhandled(val tag: String) : InputLeapEvent()
}
