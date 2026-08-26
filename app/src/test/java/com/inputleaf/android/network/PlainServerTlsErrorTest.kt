package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.net.ssl.SSLException

class PlainServerTlsErrorTest {
    @Test fun `detects plain-server TLS packet errors`() {
        assertThat(
            InputLeapConnection.isPlainServerTlsError(
                SSLException("Unable to parse TLS packet header")
            )
        ).isTrue()
        assertThat(
            InputLeapConnection.isPlainServerTlsError(
                SSLException("Unsupported or unrecognized SSL message")
            )
        ).isTrue()
    }

    @Test fun `detects wrapped plain-server TLS errors`() {
        val error = IllegalStateException(
            "Handshake failed",
            SSLException("Not an SSLv2 hello"),
        )

        assertThat(InputLeapConnection.isPlainServerTlsError(error)).isTrue()
    }

    @Test fun `does not confuse certificate failures with plain servers`() {
        val error = SSLException("Certificate fingerprint mismatch")

        assertThat(InputLeapConnection.isPlainServerTlsError(error)).isFalse()
        assertThat(InputLeapConnection.isCertificateMismatch(error)).isTrue()
    }
}
