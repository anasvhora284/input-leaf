package com.inputleaf.android.inject

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class AccessibilityConfigTest {
    private val config = File("src/main/res/xml/accessibility_service_config.xml").readText()

    // The service only reads events while it taps the keyboard picker on Android 8-10, and
    // turns them on for that moment. Listening to every event of every app slows the TV box.
    @Test
    fun `service config does not subscribe to events`() {
        assertThat(config).doesNotContain("accessibilityEventTypes")
        assertThat(config).doesNotContain("typeAllMask")
    }

    @Test
    fun `service config keeps the abilities the injector needs`() {
        assertThat(config).contains("android:canPerformGestures=\"true\"")
        assertThat(config).contains("android:canRetrieveWindowContent=\"true\"")
        assertThat(config).contains("flagRetrieveInteractiveWindows")
    }
}
