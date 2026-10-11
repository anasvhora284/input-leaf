package com.inputleaf.android.ui

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.storage.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class RootGrantRememberedTest {

    @Test
    fun `connect time grant is remembered and a denial is forgotten`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = AppPreferences(app)
        val provider = PermissionStatusProvider(app, prefs)
        try {
            provider.rememberRootGrant(true)
            assertThat(prefs.rootGrantRemembered.first()).isTrue()

            provider.rememberRootGrant(false)
            assertThat(prefs.rootGrantRemembered.first()).isFalse()
        } finally {
            provider.cleanup()
        }
    }
}
