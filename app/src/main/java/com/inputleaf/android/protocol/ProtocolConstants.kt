package com.inputleaf.android.protocol

object ProtocolConstants {
    const val DEFAULT_PORT = 24800
    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 6
    const val MAX_MESSAGE_LEN = 4 * 1024 * 1024

    // Server→Client tags
    const val TAG_HELLO         = "HELO"
    const val TAG_QUERY_INFO    = "QINF"
    const val TAG_ENTER         = "CINN"
    const val TAG_LEAVE         = "COUT"
    const val TAG_KEEPALIVE     = "CALV"
    const val TAG_RESET_OPTIONS = "CROP"
    const val TAG_KEY_DOWN      = "DKDN"
    const val TAG_KEY_UP        = "DKUP"
    const val TAG_KEY_REPEAT    = "DKRP"
    const val TAG_MOUSE_MOVE    = "DMMV"
    const val TAG_MOUSE_REL     = "DMRM"
    const val TAG_MOUSE_DOWN    = "DMDN"
    const val TAG_MOUSE_UP      = "DMUP"
    const val TAG_MOUSE_WHEEL   = "DMWM"
    const val TAG_INCOMPATIBLE  = "EICV"
    const val TAG_BUSY          = "EBSY"
    const val TAG_UNKNOWN       = "EUNK"
    const val TAG_BAD           = "EBAD"

    // Client→Server write-only tags (never used in parser when-branches)
    const val TAG_HELLO_BACK    = "HELO" // same wire tag as TAG_HELLO — write-only
    const val TAG_DATA_INFO     = "DINF"
    const val TAG_INFO_ACK      = "CIAK"
}
