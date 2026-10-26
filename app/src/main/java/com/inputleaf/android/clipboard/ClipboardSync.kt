package com.inputleaf.android.clipboard

import com.inputleaf.android.model.InputLeapEvent

/**
 * Text clipboard sync between the server and this device (#56), protocol side only.
 *
 * Server to device: DCLP chunks are reassembled and the text is applied. Device to
 * server: on Leave, the current clipboard is sent if it differs from what was last
 * exchanged, so the server's own text is never echoed back.
 */
class ClipboardSync {
    private val assembler = ClipboardAssembler()

    /** Last text received from or sent to the server. */
    @Volatile private var lastSynced: String? = null

    /** @return text to put on the device clipboard, once a transfer completes. */
    fun onServerEvent(event: InputLeapEvent): String? {
        if (event !is InputLeapEvent.ClipboardData) return null
        // id 1 is the X11 middle-click selection; Android has only the main clipboard.
        if (event.id != PRIMARY) return null
        val blob = assembler.accept(event.mark, event.data) ?: return null
        val text = ClipboardCodec.unmarshallText(blob) ?: return null
        lastSynced = text
        return text
    }

    /** @return text to send on Leave, or null when nothing new was copied here. */
    fun onLeave(deviceText: String?): String? {
        if (deviceText.isNullOrEmpty() || deviceText == lastSynced) return null
        lastSynced = deviceText
        return deviceText
    }

    companion object {
        const val PRIMARY = 0
    }
}
