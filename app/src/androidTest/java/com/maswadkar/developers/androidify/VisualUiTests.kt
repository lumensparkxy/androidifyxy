package com.maswadkar.developers.androidify

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Visual UI Tests - Run these tests and watch them execute on your connected device!
 *
 * These tests interact with the actual UI components, clicking buttons, scrolling,
 * and verifying that elements are displayed correctly. You can see all the
 * interactions happening in real-time on your device/emulator.
 *
 * Run with: ./gradlew connectedAndroidTest
 * Or right-click on this file in Android Studio and select "Run 'VisualUiTests'"
 *
 * To run only the E2E test:
 * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.maswadkar.developers.androidify.VisualUiTests#endToEnd_loginAndTestAllScreens
 */
@RunWith(AndroidJUnit4::class)
class VisualUiTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // Test credentials
    private val testPhoneNumber = "9403513382"
    private val testOtp = "527800"

    // Helper to get string from resources
    private fun getString(resId: Int): String {
        return InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
    }

    // Helper function to wait with visual delay
    private fun visualDelay(ms: Long = 1000) {
        composeTestRule.waitForIdle()
        Thread.sleep(ms)
    }

    // Helper to wait for a node to appear (with timeout)
    private fun waitForNode(text: String, timeoutMs: Long = 10000) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                composeTestRule.onAllNodesWithText(text).onFirst().assertIsDisplayed()
                return
            } catch (_: AssertionError) {
                Thread.sleep(500)
                composeTestRule.waitForIdle()
            }
        }
        // Final assertion to get proper error
        composeTestRule.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }

    // Helper to navigate back to Home using back button (most screens have back, not drawer)
    private fun navigateToHome() {
        composeTestRule.waitForIdle()
        try {
            // Most detail screens have a back button, try it first
            composeTestRule.onNodeWithContentDescription(getString(R.string.back)).performClick()
            visualDelay(1000)
        } catch (_: Exception) {
            // If no back button, try drawer (Home screen and Chat screen have drawer)
            try {
                composeTestRule.onNodeWithContentDescription(getString(R.string.open_drawer)).performClick()
                visualDelay(500)
                composeTestRule.onAllNodesWithText(getString(R.string.menu_home)).onFirst().performClick()
                visualDelay(1000)
            } catch (_: Exception) {
                // Already on home or can't navigate
                visualDelay(500)
            }
        }
    }

    // ==================== END-TO-END TEST ====================

    /**
     * Complete end-to-end test:
     * 1. Login with phone number 9403513382
     * 2. Enter OTP 527800
     * 3. Navigate to and verify: AI Chat, Weather, Knowledge Base, Offers, Carbon Credits, Chat History
     */
    @Test
    fun endToEnd_loginAndTestAllScreens() {
        // ========== STEP 1: LOGIN SCREEN ==========
        visualDelay(1000)

        // Check if already logged in (Home screen visible)
        val isAlreadyLoggedIn = try {
            composeTestRule.onAllNodesWithText(getString(R.string.home_feature_chat_title)).onFirst().assertIsDisplayed()
            true
        } catch (_: AssertionError) {
            false
        }

        if (!isAlreadyLoggedIn) {
            // Verify Login Screen
            composeTestRule.onNodeWithText("Krishi AI").assertIsDisplayed()
            visualDelay(500)

            // Click "Sign in with Phone"
            composeTestRule.onNodeWithText("Sign in with Phone").performClick()
            visualDelay(1000)

            // ========== STEP 2: ENTER PHONE NUMBER ==========
            // Enter phone number in the text field
            composeTestRule.onNodeWithText("+91").assertIsDisplayed()

            // Find the phone input field and enter number
            composeTestRule.onNode(hasText("Phone number") or hasContentDescription("Phone number"))
                .performTextInput(testPhoneNumber)
            visualDelay(500)

            // Click "Send OTP" or continue button
            composeTestRule.onNodeWithText("Send OTP").performClick()
            visualDelay(3000) // Wait for OTP to be sent

            // ========== STEP 3: ENTER OTP ==========
            // Wait for OTP screen
            waitForNode("Verify OTP", 15000)
            visualDelay(500)

            // Enter OTP digits
            // OTP input might be individual boxes or a single field
            try {
                // Try single OTP field first
                composeTestRule.onNode(hasContentDescription("OTP") or hasText("Enter OTP"))
                    .performTextInput(testOtp)
            } catch (_: Exception) {
                // If that fails, try entering digits one by one in OTP boxes
                testOtp.forEachIndexed { index, digit ->
                    try {
                        composeTestRule.onAllNodesWithText("")[index].performTextInput(digit.toString())
                    } catch (_: Exception) {
                        // Ignore if can't find individual boxes
                    }
                }
            }
            visualDelay(500)

            // Click Verify button
            composeTestRule.onNodeWithText("Verify").performClick()
            visualDelay(5000) // Wait for verification and navigation to Home
        }

        // ========== STEP 4: HOME SCREEN ==========
        waitForNode(getString(R.string.home_feature_chat_title), 15000)
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_chat_title)).onFirst().assertIsDisplayed()
        visualDelay(1000)

        // ========== STEP 5: TEST AI CHAT SCREEN ==========
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_chat_title)).onFirst().performClick()
        visualDelay(2000)

        // Verify Chat Screen is displayed
        composeTestRule.onAllNodesWithText(getString(R.string.app_name)).onFirst().assertIsDisplayed()

        // Type a test message
        try {
            composeTestRule.onNodeWithText(getString(R.string.input_hint)).performClick()
            composeTestRule.onNodeWithText(getString(R.string.input_hint)).performTextInput("What crops should I plant in monsoon?")
            visualDelay(1000)

            // Send the message
            composeTestRule.onNodeWithContentDescription(getString(R.string.send_button)).performClick()
            visualDelay(3000) // Wait for AI response
        } catch (_: Exception) {
            // Input field might have different state
            visualDelay(1000)
        }

        // Go back to Home using drawer (Chat screen has drawer, not back button)
        composeTestRule.onNodeWithContentDescription(getString(R.string.open_drawer)).performClick()
        visualDelay(500)
        composeTestRule.onAllNodesWithText(getString(R.string.menu_home)).onFirst().performClick()
        visualDelay(1000)

        // ========== STEP 6: TEST WEATHER SCREEN ==========
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_weather_title)).onFirst().performScrollTo()
        visualDelay(500)
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_weather_title)).onFirst().performClick()
        visualDelay(3000) // Wait for weather to load

        // Verify Weather Screen
        composeTestRule.onAllNodesWithText(getString(R.string.weather_title)).onFirst().assertIsDisplayed()
        visualDelay(2000)

        // Go back to Home
        navigateToHome()

        // ========== STEP 7: TEST KNOWLEDGE BASE SCREEN ==========
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_knowledge_title)).onFirst().performScrollTo()
        visualDelay(500)
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_knowledge_title)).onFirst().performClick()
        visualDelay(2000)

        // Verify Knowledge Base Screen
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_knowledge_title)).onFirst().assertIsDisplayed()
        visualDelay(2000)

        // Go back to Home
        navigateToHome()

        // ========== STEP 8: TEST OFFERS SCREEN ==========
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_offers_title)).onFirst().performScrollTo()
        visualDelay(500)
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_offers_title)).onFirst().performClick()
        visualDelay(2000)

        // Verify Offers Screen
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_offers_title)).onFirst().assertIsDisplayed()
        visualDelay(2000)

        // Go back to Home
        navigateToHome()

        // ========== STEP 9: TEST CARBON CREDITS SCREEN ==========
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_carbon_title)).onFirst().performScrollTo()
        visualDelay(500)
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_carbon_title)).onFirst().performClick()
        visualDelay(2000)

        // Verify Carbon Credits Screen
        composeTestRule.onAllNodesWithText(getString(R.string.carbon_credits_headline)).onFirst().assertIsDisplayed()
        visualDelay(2000)

        // Go back to Home
        navigateToHome()

        // ========== STEP 10: TEST CHAT HISTORY SCREEN ==========
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_history_title)).onFirst().performScrollTo()
        visualDelay(500)
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_history_title)).onFirst().performClick()
        visualDelay(2000)

        // Verify History Screen - title is "Historical Conversations" from menu_history
        composeTestRule.onAllNodesWithText(getString(R.string.menu_history)).onFirst().assertIsDisplayed()
        visualDelay(2000)

        // Go back to Home
        navigateToHome()

        // ========== TEST COMPLETE ==========
        // Verify we're back on Home screen
        composeTestRule.onAllNodesWithText(getString(R.string.home_feature_chat_title)).onFirst().assertIsDisplayed()
        visualDelay(2000)
    }

    // ==================== INDIVIDUAL SCREEN TESTS ====================

    /**
     * Test just the login flow (if not already logged in)
     */
    @Test
    fun test_phoneLoginFlow() {
        visualDelay(1000)

        // Check if already logged in by looking for Home screen content
        val isLoggedIn = try {
            composeTestRule.onAllNodesWithText(getString(R.string.home_feature_chat_title)).onFirst().assertIsDisplayed()
            true
        } catch (_: AssertionError) {
            false
        }

        if (isLoggedIn) {
            // Already logged in, skip this test
            return
        }

        // On login screen - verify it and test phone flow
        composeTestRule.onNodeWithText("Krishi AI").assertIsDisplayed()

        // Click Phone Sign In
        composeTestRule.onNodeWithText("Sign in with Phone").performClick()
        visualDelay(1000)

        // Verify phone input is shown
        composeTestRule.onNodeWithText("+91").assertIsDisplayed()
        visualDelay(1000)
    }

    /**
     * Test navigation drawer functionality (requires being logged in)
     */
    @Test
    fun test_navigationDrawer() {
        visualDelay(1000)

        // Check if logged in
        try {
            composeTestRule.onAllNodesWithText(getString(R.string.home_feature_chat_title)).onFirst().assertIsDisplayed()
        } catch (_: AssertionError) {
            // Not logged in, skip
            return
        }

        // Open drawer
        composeTestRule.onNodeWithContentDescription(getString(R.string.open_drawer)).performClick()
        visualDelay(1000)

        // Verify drawer items
        composeTestRule.onAllNodesWithText(getString(R.string.menu_home)).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(getString(R.string.menu_new_chat)).onFirst().assertIsDisplayed()
        visualDelay(1000)

        // Close drawer by clicking Home
        composeTestRule.onAllNodesWithText(getString(R.string.menu_home)).onFirst().performClick()
        visualDelay(500)
    }

    /**
     * Test scrolling through all home screen features
     */
    @Test
    fun test_homeScreenFeatures() {
        visualDelay(1000)

        // Check if logged in
        try {
            composeTestRule.onAllNodesWithText(getString(R.string.home_feature_chat_title)).onFirst().assertIsDisplayed()
        } catch (_: AssertionError) {
            // Not logged in, skip
            return
        }

        // Scroll through features
        val features = listOf(
            R.string.home_feature_chat_title,
            R.string.home_feature_diagnosis_title,
            R.string.home_feature_mandi_title,
            R.string.home_feature_weather_title,
            R.string.home_feature_knowledge_title,
            R.string.home_feature_offers_title,
            R.string.home_feature_carbon_title,
            R.string.home_feature_history_title
        )

        features.forEach { featureResId ->
            val featureText = getString(featureResId)
            composeTestRule.onAllNodesWithText(featureText).onFirst().performScrollTo()
            visualDelay(300)
            composeTestRule.onAllNodesWithText(featureText).onFirst().assertIsDisplayed()
        }

        visualDelay(1000)
    }
}
