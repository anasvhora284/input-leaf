package com.inputleaf.android.inject

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ImeSwitcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gboard = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"

    @After
    fun tearDown() = ImeSwitcher.restore(context)

    private fun select(id: String) =
        Settings.Secure.putString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, id)

    private fun enable(vararg ids: String) {
        val infos = ids.map { id -> mock(InputMethodInfo::class.java).also { `when`(it.id).thenReturn(id) } }
        shadowOf(context.getSystemService(InputMethodManager::class.java)).setEnabledInputMethodInfoList(infos)
    }

    @Test
    @Config(sdk = [33])
    fun `entering remembers the user's keyboard and leaving gives it back`() {
        select(gboard)
        enable(gboard, ImeSwitcher.ourId(context))

        ImeSwitcher.useOurs(context)
        assertThat(ImeSwitcher.wanted).isTrue()
        assertThat(ImeSwitcher.previousId(context)).isEqualTo(gboard)

        // Already ours: the saved keyboard must not become Input Leaf's own.
        select(ImeSwitcher.ourId(context))
        ImeSwitcher.useOurs(context)
        assertThat(ImeSwitcher.previousId(context)).isEqualTo(gboard)

        ImeSwitcher.restore(context, leaving = true)
        assertThat(ImeSwitcher.wanted).isFalse()
    }

    @Test
    @Config(sdk = [29])
    fun `before Android 11 the keyboard stays until the session ends`() {
        select(gboard)
        enable(gboard, ImeSwitcher.ourId(context))
        ImeSwitcher.useOurs(context)
        select(ImeSwitcher.ourId(context))

        ImeSwitcher.restore(context, leaving = true)
        assertThat(ImeSwitcher.wanted).isTrue()

        // A second Enter must not pop the picker again.
        select(gboard)
        ImeSwitcher.useOurs(context)
        assertThat(ImeSwitcher.wanted).isTrue()

        ImeSwitcher.restore(context)
        assertThat(ImeSwitcher.wanted).isFalse()
    }
}
