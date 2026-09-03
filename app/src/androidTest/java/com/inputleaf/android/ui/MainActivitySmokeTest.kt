package com.inputleaf.android.ui

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.inputleaf.android.storage.dataStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Smoke test that launches the real [MainActivity] on an emulator. A fresh install has no
 * onboarding flag, so the first run must render the onboarding welcome page and advance
 * through its pager controls.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    // MainActivity asks for POST_NOTIFICATIONS on API 33+; grant it up front so the system
    // dialog cannot interfere with the composed UI under test.
    private val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    // The tests need fresh-install state, but an activity-launching compose rule evaluates
    // before any @Before can run, so the reset must be a rule of its own. Clearing through
    // the app's own DataStore singleton also covers reused local emulators that completed
    // onboarding manually.
    private val resetAppDataRule: ExternalResource = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            runBlocking { context.dataStore.edit { it.clear() } }
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    // The permission grant and DataStore reset both complete before the activity launches.
    @get:Rule
    val ruleChain: RuleChain =
        RuleChain.outerRule(permissionRule).around(resetAppDataRule).around(composeRule)

    private fun waitAndAssertText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun freshInstallShowsOnboardingWelcomePage() {
        waitAndAssertText("Welcome to Input Leaf")
    }

    @Test
    fun nextButtonAdvancesToShizukuPermissionPage() {
        waitAndAssertText("Welcome to Input Leaf")

        composeRule.onNodeWithText("Next").performClick()

        waitAndAssertText("Shizuku Setup (Optional)")
    }
}
