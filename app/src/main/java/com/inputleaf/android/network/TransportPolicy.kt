package com.inputleaf.android.network

object TransportPolicy {
    fun order(
        policy: ConnectionTransportPolicy,
        preferredTransport: ServerTransport?,
        detectedMode: ServerSecurityMode? = null,
    ): List<ServerTransport> = when (policy) {
        ConnectionTransportPolicy.TLS_ONLY -> listOf(ServerTransport.TLS)
        ConnectionTransportPolicy.PLAIN_ONLY -> listOf(ServerTransport.PLAIN)
        ConnectionTransportPolicy.AUTO -> autoOrder(preferredTransport, detectedMode)
    }

    private fun autoOrder(
        preferredTransport: ServerTransport?,
        detectedMode: ServerSecurityMode?,
    ): List<ServerTransport> {
        when (detectedMode) {
            ServerSecurityMode.PLAIN -> return listOf(ServerTransport.PLAIN)
            ServerSecurityMode.TLS,
            ServerSecurityMode.TLS_CLIENT_CERT_REQUIRED -> return listOf(ServerTransport.TLS)
            null -> Unit
        }
        if (preferredTransport != null) {
            val fallback = when (preferredTransport) {
                ServerTransport.TLS -> ServerTransport.PLAIN
                ServerTransport.PLAIN -> ServerTransport.TLS
            }
            return listOf(preferredTransport, fallback)
        }
        // Deskflow is TLS by default. Probing TLS first fails fast on plain servers;
        // probing plain first stalls for the handshake timeout on TLS servers.
        return listOf(ServerTransport.TLS, ServerTransport.PLAIN)
    }
}
