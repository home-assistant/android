package io.homeassistant.companion.android.onboarding.manualserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ManualServerScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given empty screen when interacting with the view then handle interactions`() {
        composeTestRule.apply {
            testScreen(serverUrl = "", isServerUrlValid = false) {
                onNodeWithText(stringResource(commonR.string.manual_server_connect))
                    .assertIsDisplayed()
                    .assertIsNotEnabled()

                onNodeWithText(stringResource(commonR.string.manual_server_wrong_url)).assertIsNotDisplayed()

                onNodeWithText("http://homeassistant.local:8123").assertIsDisplayed()
                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsNotDisplayed()
            }
        }
    }

    @Test
    fun `Given screen with invalid URL when interacting with the view then handle interactions`() {
        val invalidUrl = "invalid"
        composeTestRule.apply {
            testScreen(serverUrl = invalidUrl, isServerUrlValid = false) {
                onNodeWithText(stringResource(commonR.string.manual_server_connect))
                    .assertIsDisplayed()
                    .assertIsNotEnabled()

                onNodeWithText(stringResource(commonR.string.manual_server_wrong_url)).assertIsDisplayed()

                onNodeWithText(invalidUrl).assertIsDisplayed()
                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsDisplayed().performClick()
                assertEquals("", changedUrl)
            }
        }
    }

    @Test
    fun `Given screen with valid URL when interacting with the view then handle interactions`() {
        val validUrl = "http://openhomefoundation.org"
        composeTestRule.apply {
            testScreen(serverUrl = validUrl, isServerUrlValid = true) {
                onNodeWithText(stringResource(commonR.string.manual_server_connect))
                    .assertIsDisplayed()
                    .assertIsEnabled()
                    .performClick()

                assertTrue(onConnectClicked)

                onNodeWithText(stringResource(commonR.string.manual_server_wrong_url)).assertIsNotDisplayed()

                onNodeWithText(validUrl).assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given screen with URL when interacting with the text field then handle interactions`() {
        val validUrl = "http://openhomefoundation.org"
        composeTestRule.apply {
            testScreen(serverUrl = validUrl, isServerUrlValid = true) {
                onNodeWithText(stringResource(commonR.string.manual_server_connect))
                    .assertIsDisplayed()
                    .assertIsEnabled()
                    .performClick()

                assertTrue(onConnectClicked)

                onNodeWithText(stringResource(commonR.string.manual_server_wrong_url)).assertIsNotDisplayed()

                onNodeWithText(validUrl).assertIsDisplayed().performTextInput("hello")
                assertEquals("hellohttp://openhomefoundation.org", changedUrl)

                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsDisplayed().performClick()
                assertEquals("", changedUrl)
            }
        }
    }

    private class TestHelper {
        var backClicked = false
        var helpClicked = false
        var onConnectClicked = false
        var changedUrl: String? = null
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(serverUrl: String, isServerUrlValid: Boolean, dsl: TestHelper.() -> Unit = {}) {
        TestHelper().apply {
            setContent {
                ManualServerScreen(
                    onBackClick = { backClicked = true },
                    onConnectClick = { onConnectClicked = true },
                    onHelpClick = { helpClicked = true },
                    isServerUrlValid = isServerUrlValid,
                    onServerUrlChange = { changedUrl = it },
                    serverUrl = serverUrl,
                )
            }
            onNodeWithText(stringResource(commonR.string.manual_server_title)).assertIsDisplayed()

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(backClicked)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).assertIsDisplayed().performClick()
            assertTrue(helpClicked)

            onNodeWithText(stringResource(commonR.string.manual_server_connect)).performScrollTo().assertIsDisplayed()

            dsl()
        }
    }
}
