package com.inputleaf.android.inject

import android.Manifest
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.storage.dataStore
import com.inputleaf.android.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the TV-safe node clicker against the real Compose accessibility hierarchy. */
@RunWith(AndroidJUnit4::class)
class AccessibilityTvClickTest {
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

    // Phone system images show this dialog over MainActivity; TV images do not.
    @get:Rule
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun nodeClickAtSoftwareCursorActivatesComposeButton() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { context.dataStore.edit { it.clear() } }

        ActivityScenario.launch(MainActivity::class.java).use {
            val next = waitForNode("Next")
            val bounds = Rect().also(next::getBoundsInScreen)

            val clicked = AccessibilityNodeClicker.clickAt(
                roots = accessibilityRoots(),
                x = bounds.exactCenterX(),
                y = bounds.exactCenterY(),
            )

            assertThat(clicked).isTrue()
            waitForNode("Choose an input method")
        }
    }

    private fun waitForNode(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val node = accessibilityRoots().firstNotNullOfOrNull { root ->
                findNodeByText(root, text)
            }
            if (node != null) return node
            SystemClock.sleep(50)
        }
        error("Accessibility node not found: " + text + "; " + hierarchySummary())
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) {
                return node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::add)
            }
        }
        return null
    }

    private fun accessibilityRoots(): List<AccessibilityNodeInfo> = buildList {
        uiAutomation.rootInActiveWindow?.let(::add)
        for (window in uiAutomation.windows) {
            val root = window.root ?: continue
            if (none { it == root }) add(root)
        }
    }

    private fun hierarchySummary(): String {
        val roots = accessibilityRoots()
        val summaries = roots.map { root ->
            val texts = mutableListOf<String>()
            val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
            pending.add(root)
            while (pending.isNotEmpty() && texts.size < 20) {
                val node = pending.removeFirst()
                node.text?.toString()?.takeIf(String::isNotBlank)?.let(texts::add)
                node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(texts::add)
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(pending::add)
                }
            }
            "package=" + root.packageName + ", texts=" + texts
        }
        return "roots=" + roots.size + " " + summaries
    }
}
