package com.inputleaf.android.network

import android.app.Application
import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowWifiInfo
import java.net.InetAddress

/** With no usable network interface, the address WifiManager reports is scanned instead. */
@RunWith(RobolectricTestRunner::class)
class NetworkUtilsFallbackTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun wifiIp(address: String?) {
        val info: WifiInfo = ShadowWifiInfo.newInstance()
        if (address != null) shadowOf(info).setInetAddress(InetAddress.getByName(address))
        shadowOf((app as Context).getSystemService(Context.WIFI_SERVICE) as WifiManager).setConnectionInfo(info)
    }

    @Test fun `falls back to the WifiManager address`() {
        wifiIp("192.168.1.20")
        assertThat(NetworkUtils.getLocalIpAddresses(app) { emptyList() }).containsExactly("192.168.1.20")
    }

    @Test fun `is empty when WifiManager has no address either`() {
        wifiIp(null)
        assertThat(NetworkUtils.getLocalIpAddresses(app) { emptyList() }).isEmpty()
    }
}
