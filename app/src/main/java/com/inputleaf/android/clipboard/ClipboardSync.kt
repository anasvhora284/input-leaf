package com.inputleaf.android.clipboard

import com.inputleaf.android.model.InputLeapEvent

/** Receive text/images from the server; the existing device-to-server path remains text only. */
class ClipboardSync internal constructor(private val assembler: ClipboardAssembler) {
    constructor(onRejected: (String) -> Unit = {}) : this(ClipboardAssembler(onRejected = onRejected))

    private var sequence: Int? = null
    private var lastSynced: String? = null
    private var server: String? = null

    /** Retain text deduplication across reconnects, but discard unfinished transfers. */
    @Synchronized
    fun reset() {
        assembler.reset()
        sequence = null
    }

    @Synchronized
    fun beginSession(server: String) {
        reset()
        if (this.server != server) lastSynced = null
        this.server = server
    }

    @Synchronized
    fun onServerEvent(event: InputLeapEvent): ClipboardContent? {
        if (event is InputLeapEvent.ClipboardGrab && event.id == PRIMARY) reset()
        if (event !is InputLeapEvent.ClipboardData || event.id != PRIMARY) return null
        if (event.mark == ClipboardAssembler.MARK_START) sequence = event.seqNum
        if (sequence != event.seqNum) return null
        val blob = assembler.accept(event.mark, event.data)
        if (event.mark == ClipboardAssembler.MARK_END) sequence = null
        val content = blob?.let(ClipboardCodec::unmarshall) ?: return null
        lastSynced = content.text
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
