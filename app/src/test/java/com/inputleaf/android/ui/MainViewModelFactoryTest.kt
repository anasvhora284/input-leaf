package com.inputleaf.android.ui

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class MainViewModelFactoryTest {

    @Test
    fun `main view model exposes single argument application constructor for reflection`() {
        val constructor = MainViewModel::class.java.getConstructor(Application::class.java)
        assertThat(constructor).isNotNull()
    }

    @Test
    fun `factory creates main view model instance successfully`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val factory = MainViewModelFactory(app)
        val viewModel = factory.create(MainViewModel::class.java)
        assertThat(viewModel).isNotNull()
    }
}
