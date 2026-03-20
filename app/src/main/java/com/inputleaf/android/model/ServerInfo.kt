package com.inputleaf.android.model

data class ServerInfo(
    val ip: String,
    val port: Int = 24800,
    val name: String = ""   // from kMsgHello, empty if not yet discovered
)
