package io.homeassistant.companion.android.onboarding.wearmtls

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.core.app.ActivityOptionsCompat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import io.mockk.mockk
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
class WearMTLSScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given empty screen when interacting with the view then handle interactions`() {
        composeTestRule.apply {
            testScreen {
                onNodeWithText(stringResource(commonR.string.select_file)).performScrollTo().assertIsDisplayed()
                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsNotDisplayed()
                onNodeWithText(stringResource(commonR.string.wear_mtls_next)).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `Given empty screen when picking a file then set selectedUri`() {
        composeTestRule.apply {
            val selectedUri = mockk<Uri>()
            testScreen(selectedUri = selectedUri) {
                onNodeWithText(stringResource(commonR.string.select_file)).performScrollTo().assertIsDisplayed().performClick()
                assertEquals(selectedUri, uriSelected)
            }
        }
    }

    @Test
    fun `Given screen with cert and password set when isError true with the view then handle interactions`() {
        composeTestRule.apply {
            testScreen(selectedFilename = "super_file", currentPassword = "password", isError = true) {
                onNodeWithText("super_file").performScrollTo().assertIsDisplayed()
                onNodeWithText("password").performScrollTo().assertIsDisplayed().performTextInput("1234")
                assertEquals("1234password", passwordSet)
                onNodeWithText(stringResource(commonR.string.wear_mtls_open_error)).assertIsDisplayed()
                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.wear_mtls_next)).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `Given screen with cert and password set when no errors and certValidated with the view then handle interactions`() {
        composeTestRule.apply {
            testScreen(selectedUri = mockk(), selectedFilename = "super_file", currentPassword = "password", isError = false, isCertValidated = true) {
                onNodeWithText("super_file").performScrollTo().assertIsDisplayed()
                onNodeWithText("password").performScrollTo().assertIsDisplayed().performTextInput("1234")
                assertEquals("1234password", passwordSet)
                onNodeWithText(stringResource(commonR.string.wear_mtls_open_error)).assertIsNotDisplayed()
                onNodeWithContentDescription(stringResource(commonR.string.clear_text)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.wear_mtls_next)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
                assertTrue(nextClicked)
            }
        }
    }

    @Test
    fun `Given selected cert when clicking selecting and cancel selection then initial selected file stays selected`() {
        composeTestRule.apply {
            val selectedUri = mockk<Uri>()
            testScreen(selectedUri = selectedUri, onClickSelectedUri = null) {
                // Manually set the selectedUri to verify that the click won't override the value with null
                // since we are emulating that the user didn't pick any file by returning onClickSelectedUri=null.
                uriSelected = selectedUri
                // We use an empty filename otherwise the perform clicks clicks onto the clear icon ... This is due to the
                // constraints on the small size of the screen under tests. But we can still test the behavior.
                onNodeWithText(stringResource(commonR.string.select_file)).performScrollTo().assertIsDisplayed().performClick()
                assertEquals(selectedUri, uriSelected)
            }
        }
    }

    private class TestHelper {
        var backClicked = false
        var helpClicked = false
        var nextClicked = false
        var uriSelected: Uri? = null
        var passwordSet: String? = null
    }

    private fun AndroidComposeTestRule<*, *>.testScreen(
        selectedUri: Uri? = null,
        selectedFilename: String? = null,
        currentPassword: String = "",
        isCertValidated: Boolean = false,
        isError: Boolean = false,
        onClickSelectedUri: Uri? = selectedUri,
        block: TestHelper.() -> Unit,
    ) {
        TestHelper().apply {
            setContent {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                        override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
                            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                                dispatchResult(requestCode, onClickSelectedUri)
                            }
                        }
                    },
                ) {
                    WearMTLSScreen(
                        onHelpClick = { helpClicked = true },
                        onBackClick = { backClicked = true },
                        onNext = { _, _ ->
                            nextClicked = true
                        },
                        onPasswordChange = {
                            passwordSet = it
                        },
                        onFileChange = {
                            uriSelected = it
                        },
                        selectedUri = selectedUri,
                        selectedFilename = selectedFilename,
                        currentPassword = currentPassword,
                        isCertValidated = isCertValidated,
                        isError = isError,

                    )
                }
            }

            onNodeWithText(stringResource(commonR.string.wear_mtls_content)).assertIsDisplayed()

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(backClicked)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).assertIsDisplayed().performClick()
            assertTrue(helpClicked)

            block()
        }
    }
}
