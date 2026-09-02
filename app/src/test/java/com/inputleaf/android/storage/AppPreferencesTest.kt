package com.inputleaf.android.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun `getDefaultScreenName sanitizes model name properly`() {
        assertThat(AppPreferences.getDefaultScreenName("Pixel 7 Pro")).isEqualTo("pixel-7-pro")
        assertThat(AppPreferences.getDefaultScreenName("SM-G991B")).isEqualTo("sm-g991b")
        assertThat(AppPreferences.getDefaultScreenName("")).isEqualTo("android-phone")
        assertThat(AppPreferences.getDefaultScreenName("Special @#$ Name")).isEqualTo("special--name")
        assertThat(AppPreferences.getDefaultScreenName(null)).isEqualTo("android-phone")
    }
}
