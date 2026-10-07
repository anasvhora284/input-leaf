package com.inputleaf.android.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerRecordCodecTest {

    @Test
    fun `encode produces v2 prefixed unpadded url-safe base64 string`() {
        val encoded = ServerRecordCodec.encode("192.168.1.10", "tls")
        assertThat(encoded).startsWith("v2|")
        assertThat(encoded).doesNotContain("=")
        assertThat(encoded.split("|")).hasSize(3)
    }

    @Test
    fun `roundtrip standard hostnames and ipv4 addresses`() {
        val cases = listOf(
            "192.168.1.10" to "tls",
            "desktop-pc.local" to "plain",
            "10.0.0.1" to "a".repeat(64),
        )
        for ((server, value) in cases) {
            val encoded = ServerRecordCodec.encode(server, value)
            val decoded = ServerRecordCodec.decode(encoded)
            assertThat(decoded).isNotNull()
            assertThat(decoded?.first).isEqualTo(server)
            assertThat(decoded?.second).isEqualTo(value)
        }
    }

    @Test
    fun `roundtrip ipv6 literal addresses without corruption`() {
        val ipv6Addresses = listOf(
            "2001:db8::1",
            "::1",
            "fe80::1ff:fe23:4567:890a",
            "[fe80::1%wlan0]:24800",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
        )
        for (addr in ipv6Addresses) {
            val encodedFp = ServerRecordCodec.encode(addr, "f".repeat(64))
            val decodedFp = ServerRecordCodec.decode(encodedFp)
            assertThat(decodedFp).isNotNull()
            assertThat(decodedFp?.first).isEqualTo(addr)
            assertThat(decodedFp?.second).isEqualTo("f".repeat(64))

            val encodedMode = ServerRecordCodec.encode(addr, "tls")
            val decodedMode = ServerRecordCodec.decode(encodedMode)
            assertThat(decodedMode).isNotNull()
            assertThat(decodedMode?.first).isEqualTo(addr)
            assertThat(decodedMode?.second).isEqualTo("tls")
        }
    }

    @Test
    fun `decode parses legacy server-colon-value lines for ipv4 and hostnames`() {
        val decoded1 = ServerRecordCodec.decode("192.168.1.10:tls")
        assertThat(decoded1).isEqualTo("192.168.1.10" to "tls")

        val decoded2 = ServerRecordCodec.decode("macbook.local:plain")
        assertThat(decoded2).isEqualTo("macbook.local" to "plain")

        val fp = "ab".repeat(32)
        val decoded3 = ServerRecordCodec.decode("server.lan:$fp")
        assertThat(decoded3).isEqualTo("server.lan" to fp)
    }

    @Test
    fun `decode parses legacy ipv6 addresses by splitting on last colon`() {
        val decoded1 = ServerRecordCodec.decode("2001:db8::1:tls")
        assertThat(decoded1).isEqualTo("2001:db8::1" to "tls")

        val fp = "cd".repeat(32)
        val decoded2 = ServerRecordCodec.decode("2001:db8::1:$fp")
        assertThat(decoded2).isEqualTo("2001:db8::1" to fp)

        val decoded3 = ServerRecordCodec.decode("::1:plain")
        assertThat(decoded3).isEqualTo("::1" to "plain")
    }

    @Test
    fun `decode rejects malformed inputs gracefully`() {
        assertThat(ServerRecordCodec.decode("")).isNull()
        assertThat(ServerRecordCodec.decode("   ")).isNull()
        assertThat(ServerRecordCodec.decode("malformed_without_colon")).isNull()
        assertThat(ServerRecordCodec.decode(":value_only")).isNull()
        assertThat(ServerRecordCodec.decode("server_only:")).isNull()
        assertThat(ServerRecordCodec.decode("v2|only_one_part")).isNull()
        assertThat(ServerRecordCodec.decode("v2|bad base64!|bad base64!")).isNull()
    }
}
