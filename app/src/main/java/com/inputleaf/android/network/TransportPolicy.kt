package com.inputleaf.android.network

/**
 * Explicit TLS vs plain selection. Never probes both in one connect attempt.
 */
object TransportPolicy {
    fun order(tlsEnabled: Boolean): List<ServerTransport> =
        if (tlsEnabled) listOf(ServerTransport.TLS) else listOf(ServerTransport.PLAIN)
}
