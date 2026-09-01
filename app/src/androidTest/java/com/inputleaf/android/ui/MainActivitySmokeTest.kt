package com.inputleaf.android.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Smoke test that launches the real [MainActivity] on an emulator. A fresh install has no
 * onboarding flag, so the first run must render the onboarding welcome page and advance
 * through its pager controls.
 */
class MainActivitySmokeTest {

    // MainActivity asks for POST_NOTIFICATIONS on API 33+; grant it up front so the system
    // dialog cannot interfere with the composed UI under test.
    private val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // The permission must be granted before the activity launches and shows the system dialog.
    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    private fun waitAndAssertText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun `fresh install shows onboarding welcome page`() {
        waitAndAssertText("Welcome to Input Leaf")
    }

    @Test
    fun `next button advances to shizuku permission page`() {
        waitAndAssertText("Welcome to Input Leaf")

        composeRule.onNodeWithText("Next").performClick()

        waitAndAssertText("Shizuku Setup (Optional)")
    }
}
