package com.inputleaf.android.util

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.network.NetworkUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class NetworkUtilsLocalIpTest {
    @Test
    fun `getLocalIpAddresses probes interfaces without throwing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val ips = NetworkUtils.getLocalIpAddresses(app)
        ips.forEach { assertThat(it).contains(".") }
    }
}
