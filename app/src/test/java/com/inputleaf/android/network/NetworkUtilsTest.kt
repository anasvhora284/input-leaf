package com.inputleaf.android.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NetworkUtilsTest {
    @Test
    fun `isPrivateIP identifies 10 dot 0 dot 0 dot 0 range`() {
        assertThat(NetworkUtils.isPrivateIP("10.0.0.1")).isTrue()
        assertThat(NetworkUtils.isPrivateIP("10.255.255.254")).isTrue()
    }

    @Test
    fun `isPrivateIP identifies 172 dot 16 to 31 range`() {
        assertThat(NetworkUtils.isPrivateIP("172.16.0.1")).isTrue()
        assertThat(NetworkUtils.isPrivateIP("172.24.10.5")).isTrue()
        assertThat(NetworkUtils.isPrivateIP("172.31.255.255")).isTrue()
        assertThat(NetworkUtils.isPrivateIP("172.15.0.1")).isFalse()
        assertThat(NetworkUtils.isPrivateIP("172.32.0.1")).isFalse()
    }

    @Test
    fun `isPrivateIP identifies 192 dot 168 range`() {
        assertThat(NetworkUtils.isPrivateIP("192.168.1.1")).isTrue()
        assertThat(NetworkUtils.isPrivateIP("192.168.0.254")).isTrue()
        assertThat(NetworkUtils.isPrivateIP("192.169.1.1")).isFalse()
    }

    @Test
    fun `isPrivateIP rejects public and invalid IPs`() {
        assertThat(NetworkUtils.isPrivateIP("8.8.8.8")).isFalse()
        assertThat(NetworkUtils.isPrivateIP("1.1.1.1")).isFalse()
        assertThat(NetworkUtils.isPrivateIP("invalid")).isFalse()
        assertThat(NetworkUtils.isPrivateIP("192.168.1")).isFalse()
        assertThat(NetworkUtils.isPrivateIP("192.168.1.abc")).isFalse()
        assertThat(NetworkUtils.isPrivateIP("256.1.1.1")).isFalse()
        assertThat(NetworkUtils.isPrivateIP("10.0.0.256")).isFalse()
    }

    @Test
    fun `scanAddresses includes the hotspot as well as Wi-Fi`() {
        // OnePlus on office Wi-Fi (wlan0) while hosting a hotspot (wlan1) for the PC.
        val ips = NetworkUtils.scanAddresses(
            listOf(
                "rmnet_data2" to listOf("192.0.0.2"),
                "wlan1" to listOf("10.153.185.185"),
                "wlan0" to listOf("192.168.30.207"),
            ),
        )
        assertThat(ips).containsExactly("192.168.30.207", "10.153.185.185").inOrder()
    }

    @Test
    fun `scanAddresses skips cellular and public addresses on unknown interfaces`() {
        val ips = NetworkUtils.scanAddresses(
            listOf(
                "rmnet0" to listOf("10.20.30.40"),
                "tun0" to listOf("100.64.0.2"),
                "eth1" to listOf("172.16.5.9"),
            ),
        )
        assertThat(ips).containsExactly("172.16.5.9")
    }

    @Test
    fun `scanAddresses keeps any address on Wi-Fi and scans each subnet once`() {
        val ips = NetworkUtils.scanAddresses(
            listOf(
                "wlan0" to listOf("100.70.1.5"),
                "ap0" to listOf("100.70.1.9"),
            ),
        )
        assertThat(ips).containsExactly("100.70.1.5")
    }
}
