package io.homeassistant.companion.android.onboarding.nameyourdevice

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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class NameYourDeviceScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    @Test
    fun `Given empty screen when interacting with the view then handle interactions`() {
        composeTestRule.apply {
            testScreen("", false) {
                onNodeWithText(stringResource(R.string.name_your_device_save)).assertIsNotEnabled()

                onNodeWithContentDescription(stringResource(R.string.name_your_device_clear_name)).assertIsNotDisplayed()
            }
        }
    }

    @Test
    fun `Given screen with device name when interacting with the view then handle interactions`() {
        composeTestRule.apply {
            testScreen("Pixel 42", true) {
                onNodeWithText(stringResource(R.string.name_your_device_save))
                    .performScrollTo() // We need to scroll to the button since it is not visible because of the spacer on tests
                    .assertIsDisplayed().assertIsEnabled().performClick()
                assertTrue(saveClicked)

                onNodeWithContentDescription(stringResource(R.string.name_your_device_clear_name)).assertIsDisplayed().performClick()
                assertTrue(changedName?.isEmpty() == true)
            }
        }
    }

    private class TestHelper {
        var backClicked = false
        var helpClicked = false
        var saveClicked = false

        var changedName: String? = null
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(deviceName: String, saveClickable: Boolean, block: TestHelper.() -> Unit) {
        TestHelper().apply {
            setContent {
                NameYourDeviceScreen(
                    onHelpClick = { helpClicked = true },
                    onBackClick = { backClicked = true },
                    deviceName = deviceName,
                    onDeviceNameChange = { changedName = it },
                    saveClickable = saveClickable,
                    onSaveClick = {
                        Timber.e("clicked")
                        saveClicked = true
                    },
                )
            }

            onNodeWithText(stringResource(R.string.name_your_device_title)).assertIsDisplayed()
            onNodeWithText(stringResource(R.string.name_your_device_content)).assertIsDisplayed()

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(backClicked)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).assertIsDisplayed().performClick()
            assertTrue(helpClicked)

            block()
        }
    }
}
