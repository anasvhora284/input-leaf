package com.inputleaf.android.clipboard

import com.inputleaf.android.model.InputLeapEvent

/** Receive text/images from the server; the existing device-to-server path remains text only. */
class ClipboardSync {
    private val assembler = ClipboardAssembler()
    private var sequence: Int? = null
    private var lastSynced: String? = null

    @Synchronized
    fun reset() {
        assembler.reset()
        sequence = null
        lastSynced = null
    }

    @Synchronized
    fun onServerEvent(event: InputLeapEvent): ClipboardContent? {
        if (event is InputLeapEvent.ClipboardGrab && event.id == PRIMARY) {
            assembler.reset()
            sequence = null
        }
        if (event !is InputLeapEvent.ClipboardData || event.id != PRIMARY) return null
        if (event.mark == ClipboardAssembler.MARK_START) sequence = event.seqNum
        if (sequence != event.seqNum) return null
        val blob = assembler.accept(event.mark, event.data)
        if (event.mark == ClipboardAssembler.MARK_END) sequence = null
        val content = blob?.let(ClipboardCodec::unmarshall) ?: return null
        content.text?.let { lastSynced = it }
        return content
    }

    /** No image data or URI is sent back to the server. */
    @Synchronized
    fun onLeave(deviceText: String?): String? {
        if (deviceText.isNullOrEmpty() || deviceText == lastSynced) return null
        lastSynced = deviceText
        return deviceText
    }

    companion object {
        const val PRIMARY = 0
    }
}
