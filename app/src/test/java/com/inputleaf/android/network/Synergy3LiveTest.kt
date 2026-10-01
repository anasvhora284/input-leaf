package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Optional smoke test against a configured Synergy server; never edits server settings. */
class Synergy3LiveTest {
    @Test fun `real Synergy core accepts the Android connection implementation`() = runBlocking {
        val host = System.getenv("INPUT_LEAF_SYNERGY_HOST")
        assumeTrue("Set INPUT_LEAF_SYNERGY_HOST to opt in", !host.isNullOrBlank())
        val screen = requireNotNull(System.getenv("INPUT_LEAF_SYNERGY_SCREEN"))
        val serverFingerprint = requireNotNull(System.getenv("INPUT_LEAF_SYNERGY_FINGERPRINT"))
        val material = ClientCertificateMaterial(
            File(requireNotNull(System.getenv("INPUT_LEAF_SYNERGY_PKCS12"))).readBytes(),
            requireNotNull(System.getenv("INPUT_LEAF_SYNERGY_PASSWORD")).toCharArray(),
        )
        val connection = InputLeapConnection(
            ip = host!!,
            port = System.getenv("INPUT_LEAF_SYNERGY_PORT")?.toInt() ?: 24800,
            transportPolicy = ConnectionTransportPolicy.TLS_ONLY,
            pinnedFingerprint = serverFingerprint,
            clientCertificate = material,
            // A live smoke test must not silently approve a different server certificate.
            onCertificate = { false },
        )
        try {
            val result = connection.connect(screen, 1080, 2400)
            println("Synergy live handshake: $result")
            assertThat(result).isInstanceOf(ConnectResult.Ok::class.java)
            assertThat((result as ConnectResult.Ok).transport).isEqualTo(ServerTransport.TLS)
            assertThat(result.banner.major).isEqualTo(1)
        } finally {
            connection.close()
            material.clear()
        }
    }
}
