package com.inputleaf.android.service

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectionServiceBoundsTest {

    @Test
    fun `resolveBounds prefers window metrics when valid`() {
        val wmBounds = Rect(0, 0, 1920, 1080)
        val displayRealSize = Point(2560, 1600)
        val result = ScreenBoundsResolver.resolveBounds(
            windowMetricsBounds = wmBounds,
            displayRealSize = displayRealSize,
            legacyDisplaySize = null,
            fallbackMetricsWidth = 800,
            fallbackMetricsHeight = 600,
        )
        assertThat(result.width()).isEqualTo(1920)
        assertThat(result.height()).isEqualTo(1080)
    }

    @Test
    fun `resolveBounds falls back to display real size when window metrics empty or null`() {
        val emptyWmBounds = Rect(0, 0, 0, 0)
        val displayRealSize = Point(2560, 1600)
        val result = ScreenBoundsResolver.resolveBounds(
            windowMetricsBounds = emptyWmBounds,
            displayRealSize = displayRealSize,
            legacyDisplaySize = null,
            fallbackMetricsWidth = 800,
            fallbackMetricsHeight = 600,
        )
        assertThat(result.width()).isEqualTo(2560)
        assertThat(result.height()).isEqualTo(1600)
    }

    @Test
    fun `resolveBounds falls back to legacy display size when display real size missing`() {
        val legacySize = Point(1280, 800)
        val result = ScreenBoundsResolver.resolveBounds(
            windowMetricsBounds = null,
            displayRealSize = null,
            legacyDisplaySize = legacySize,
            fallbackMetricsWidth = 800,
            fallbackMetricsHeight = 600,
        )
        assertThat(result.width()).isEqualTo(1280)
        assertThat(result.height()).isEqualTo(800)
    }

    @Test
    fun `resolveBounds falls back to resources display metrics when displays missing`() {
        val result = ScreenBoundsResolver.resolveBounds(
            windowMetricsBounds = null,
            displayRealSize = null,
            legacyDisplaySize = null,
            fallbackMetricsWidth = 1024,
            fallbackMetricsHeight = 768,
        )
        assertThat(result.width()).isEqualTo(1024)
        assertThat(result.height()).isEqualTo(768)
    }

    @Test
    fun `resolveBounds enforces minimum 1x1 dimensions on empty or negative fallback`() {
        val result = ScreenBoundsResolver.resolveBounds(
            windowMetricsBounds = null,
            displayRealSize = null,
            legacyDisplaySize = null,
            fallbackMetricsWidth = 0,
            fallbackMetricsHeight = -5,
        )
        assertThat(result.width()).isAtLeast(1)
        assertThat(result.height()).isAtLeast(1)
    }

    @Test
    fun `resolveFromContext succeeds on application context without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bounds = ScreenBoundsResolver.resolveFromContext(context)
        assertThat(bounds.width()).isGreaterThan(0)
        assertThat(bounds.height()).isGreaterThan(0)
    }
}
