package io.homeassistant.companion.android.onboarding.nameyourdevice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
class NameYourDeviceScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given empty screen when interacting with the view then handle interactions`() {
        composeTestRule.apply {
            testScreen("", saveClickable = false, deviceNameEditable = true) {
                onNodeWithText(stringResource(commonR.string.name_your_device_save)).assertIsNotEnabled()

                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsNotDisplayed()
            }
        }
    }

    @Test
    fun `Given screen with device name when interacting with the view then handle interactions`() {
        composeTestRule.apply {
            testScreen("Pixel 42", saveClickable = true, deviceNameEditable = true) {
                onNodeWithText(stringResource(commonR.string.name_your_device_save))
                    .performScrollTo() // We need to scroll to the button since it is not visible because of the spacer on tests
                    .assertIsDisplayed().assertIsEnabled().performClick()
                assertTrue(saveClicked)

                onNodeWithText("Pixel 42").assertIsDisplayed()

                onNodeWithTag(DEVICE_NAME_TEXT_FIELD_TAG).assertIsDisplayed().assertIsEnabled().performTextInput("Hello ")
                assertEquals("Hello Pixel 42", changedName)

                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsDisplayed().performClick()
                assertTrue(changedName?.isEmpty() == true)
            }
        }
    }

    @Test
    fun `Given screen with device name and saving when deviceNameEditable is false then handle interactions`() {
        composeTestRule.apply {
            testScreen("Pixel 42", saveClickable = false, deviceNameEditable = false) {
                onNodeWithText(stringResource(commonR.string.name_your_device_save))
                    .performScrollTo() // We need to scroll to the button since it is not visible because of the spacer on tests
                    .assertIsDisplayed().assertIsNotEnabled()

                onNodeWithTag(DEVICE_NAME_TEXT_FIELD_TAG).assertIsDisplayed().assertIsNotEnabled()
                onNodeWithText("Pixel 42").assertIsDisplayed()

                // Set fake data to see if click actually does something. In this test it shouldn't do anything since it is disabled
                changedName = "dummy data"

                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsDisplayed().performClick()
                assertEquals("dummy data", changedName)
            }
        }
    }

    private class TestHelper {
        var backClicked = false
        var helpClicked = false
        var saveClicked = false

        var changedName: String? = null
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(deviceName: String, saveClickable: Boolean, deviceNameEditable: Boolean, block: TestHelper.() -> Unit) {
        TestHelper().apply {
            setContent {
                NameYourDeviceScreen(
                    onHelpClick = { helpClicked = true },
                    onBackClick = { backClicked = true },
                    deviceName = deviceName,
                    onDeviceNameChange = { changedName = it },
                    saveClickable = saveClickable,
                    deviceNameEditable = deviceNameEditable,
                    onSaveClick = {
                        saveClicked = true
                    },
                )
            }

            onNodeWithText(stringResource(commonR.string.name_your_device_title)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.name_your_device_content)).assertIsDisplayed()

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(backClicked)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).assertIsDisplayed().performClick()
            assertTrue(helpClicked)

            block()
        }
    }
}
