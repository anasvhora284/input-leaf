package com.inputleaf.android.network

object TransportPolicy {
    fun order(
        policy: ConnectionTransportPolicy,
        preferredTransport: ServerTransport?,
        hasPinnedFingerprint: Boolean,
    ): List<ServerTransport> = when (policy) {
        ConnectionTransportPolicy.TLS_ONLY -> listOf(ServerTransport.TLS)
        ConnectionTransportPolicy.PLAIN_ONLY -> listOf(ServerTransport.PLAIN)
        ConnectionTransportPolicy.AUTO -> autoOrder(preferredTransport, hasPinnedFingerprint)
    }

    private fun autoOrder(
        preferredTransport: ServerTransport?,
        hasPinnedFingerprint: Boolean,
    ): List<ServerTransport> {
        if (preferredTransport != null) {
            val fallback = when (preferredTransport) {
                ServerTransport.TLS -> ServerTransport.PLAIN
                ServerTransport.PLAIN -> ServerTransport.TLS
            }
            return listOf(preferredTransport, fallback)
        }
        return if (hasPinnedFingerprint) {
            listOf(ServerTransport.TLS, ServerTransport.PLAIN)
        } else {
            listOf(ServerTransport.PLAIN, ServerTransport.TLS)
        }
    }
}
