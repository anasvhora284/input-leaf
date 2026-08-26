package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransportPolicyTest {
    @Test fun `TLS only never falls back to plain`() {
        assertThat(
            TransportPolicy.order(
                policy = ConnectionTransportPolicy.TLS_ONLY,
                preferredTransport = ServerTransport.PLAIN,
            )
        ).containsExactly(ServerTransport.TLS)
    }

    @Test fun `plain only never probes TLS`() {
        assertThat(
            TransportPolicy.order(
                policy = ConnectionTransportPolicy.PLAIN_ONLY,
                preferredTransport = ServerTransport.TLS,
            )
        ).containsExactly(ServerTransport.PLAIN)
    }

    @Test fun `auto tries the learned transport before its fallback`() {
        assertThat(
            TransportPolicy.order(
                policy = ConnectionTransportPolicy.AUTO,
                preferredTransport = ServerTransport.TLS,
            )
        ).containsExactly(ServerTransport.TLS, ServerTransport.PLAIN).inOrder()
        assertThat(
            TransportPolicy.order(
                policy = ConnectionTransportPolicy.AUTO,
                preferredTransport = ServerTransport.PLAIN,
            )
        ).containsExactly(ServerTransport.PLAIN, ServerTransport.TLS).inOrder()
    }

    @Test fun `auto uses TLS first even without a stored fingerprint`() {
        assertThat(
            TransportPolicy.order(
                policy = ConnectionTransportPolicy.AUTO,
                preferredTransport = null,
            )
        ).containsExactly(ServerTransport.TLS, ServerTransport.PLAIN).inOrder()
    }

    @Test fun `auto uses the probed server mode instead of a stale learned transport`() {
        assertThat(
            TransportPolicy.order(
                policy = ConnectionTransportPolicy.AUTO,
                preferredTransport = ServerTransport.PLAIN,
                detectedMode = ServerSecurityMode.TLS,
            )
        ).containsExactly(ServerTransport.TLS)
        assertThat(
            TransportPolicy.order(
                policy = ConnectionTransportPolicy.AUTO,
                preferredTransport = ServerTransport.TLS,
                detectedMode = ServerSecurityMode.PLAIN,
            )
        ).containsExactly(ServerTransport.PLAIN)
        assertThat(
            TransportPolicy.order(
                policy = ConnectionTransportPolicy.AUTO,
                preferredTransport = null,
                detectedMode = ServerSecurityMode.TLS_CLIENT_CERT_REQUIRED,
            )
        ).containsExactly(ServerTransport.TLS)
    }

    @Test fun `unknown stored policy safely defaults to auto`() {
        assertThat(ConnectionTransportPolicy.fromStorage("future_mode"))
            .isEqualTo(ConnectionTransportPolicy.AUTO)
        assertThat(ConnectionTransportPolicy.fromStorage(null))
            .isEqualTo(ConnectionTransportPolicy.AUTO)
    }

    @Test fun `certificate and server rejections never downgrade to another transport`() {
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldFallbackWithinAttempt(
                ConnectResult.FailureReason.CERTIFICATE_MISMATCH
            )
        ).isFalse()
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldFallbackWithinAttempt(
                ConnectResult.FailureReason.CLIENT_CERT_REQUIRED
            )
        ).isFalse()
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldFallbackWithinAttempt(
                ConnectResult.FailureReason.INCOMPATIBLE
            )
        ).isFalse()
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldFallbackWithinAttempt(
                ConnectResult.FailureReason.BUSY
            )
        ).isFalse()
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldFallbackWithinAttempt(
                ConnectResult.FailureReason.NETWORK
            )
        ).isFalse()
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldFallbackWithinAttempt(
                ConnectResult.FailureReason.HANDSHAKE
            )
        ).isFalse()
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldFallbackWithinAttempt(
                ConnectResult.FailureReason.TLS_AGAINST_PLAIN_SERVER
            )
        ).isTrue()
    }

    @Test fun `explicit policies never retry a failed connection`() {
        for (policy in listOf(
            ConnectionTransportPolicy.TLS_ONLY,
            ConnectionTransportPolicy.PLAIN_ONLY,
        )) {
            for (reason in ConnectResult.FailureReason.entries) {
                assertThat(policy.shouldRetry(reason)).isFalse()
                assertThat(policy.shouldFallbackWithinAttempt(reason)).isFalse()
            }
        }
    }

    @Test fun `auto retries only transient connection and handshake failures`() {
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldRetry(ConnectResult.FailureReason.NETWORK)
        ).isTrue()
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldRetry(ConnectResult.FailureReason.HANDSHAKE)
        ).isTrue()
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldRetry(
                ConnectResult.FailureReason.TLS_AGAINST_PLAIN_SERVER
            )
        ).isFalse()
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldRetry(
                ConnectResult.FailureReason.CERTIFICATE_MISMATCH
            )
        ).isFalse()
        assertThat(
            ConnectionTransportPolicy.AUTO.shouldRetry(
                ConnectResult.FailureReason.CLIENT_CERT_REQUIRED
            )
        ).isFalse()
    }

    @Test fun `network failure is reported over a later transport mismatch`() {
        val network = ConnectResult.Failed(ConnectResult.FailureReason.NETWORK, "refused")
        val mismatch = ConnectResult.Failed(
            ConnectResult.FailureReason.TLS_AGAINST_PLAIN_SERVER,
            "not TLS",
        )

        assertThat(InputLeapConnection.selectFailureToReport(network, mismatch))
            .isEqualTo(network)
        assertThat(InputLeapConnection.selectFailureToReport(mismatch, network))
            .isEqualTo(network)
    }
}
