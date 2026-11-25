package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class AccessibilityConfigTest {
    private val config = File("src/main/res/xml/accessibility_service_config.xml").readText()
    private val configV30 = File("src/main/res/xml-v30/accessibility_service_config.xml").readText()

    // Android 11+ keeps the service's node cache fresh without a subscription, so it listens
    // to nothing there. Listening to every event of every app slows the TV box.
    @Test
    fun `android 11 config does not subscribe to events`() {
        assertThat(configV30).doesNotContain("accessibilityEventTypes")
        assertThat(configV30).doesNotContain("typeAllMask")
    }

    // Android 8-10 only clear the cached tree on events the service subscribes to, so
    // clicks and scrolls would hit a stale tree. These are the events that clear it; the
    // keyboard picker tap needs the window ones too.
    @Test
    fun `android 8 to 10 config subscribes only to the cache and picker events`() {
        assertThat(config).doesNotContain("typeAllMask")
        val types = Regex("accessibilityEventTypes=\"([^\"]*)\"").find(config)!!.groupValues[1].split("|")
        assertThat(types).containsAtLeast(
            "typeWindowsChanged", "typeWindowStateChanged", "typeWindowContentChanged", "typeViewFocused",
        )
    }

    @Test
    fun `service configs keep the abilities the injector needs`() {
        for (c in listOf(config, configV30)) {
            assertThat(c).contains("android:canPerformGestures=\"true\"")
            assertThat(c).contains("android:canRetrieveWindowContent=\"true\"")
            assertThat(c).contains("flagRetrieveInteractiveWindows")
        }
    }
}
