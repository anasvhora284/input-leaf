package com.inputleaf.android.ui

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.network.ConnectResult
import org.junit.Test

class ConnectionFailureMessageTest {
    @Test fun `policy mismatch explains how to recover`() {
        assertThat(
            connectionFailureMessage(
                ConnectResult.FailureReason.TLS_AGAINST_PLAIN_SERVER
            )
        ).contains("Select Auto or Plain only")
    }

    @Test fun `each connection failure has a user-facing message`() {
        for (reason in ConnectResult.FailureReason.entries) {
            assertThat(connectionFailureMessage(reason)).isNotEmpty()
        }
    }

    @Test fun `incompatible server details are preserved`() {
        assertThat(
            connectionFailureMessage(
                ConnectResult.FailureReason.INCOMPATIBLE,
                "Server requires 2.0",
            )
        ).isEqualTo("Server requires 2.0")
    }
}
