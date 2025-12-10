package io.homeassistant.companion.android.webview.insecure

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.LocationPermissionActivityResultRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class BlockInsecureScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given screen displayed with missing location when clicking fix then location permission is requested`() {
        composeTestRule.apply {
            testScreen(missingLocation = true, missingHomeSetup = false) {
                // Verify the banner text is displayed
                onNodeWithText(stringResource(commonR.string.block_insecure_missing_location))
                    .performScrollTo()
                    .assertIsDisplayed()

                // Click the enable location button
                onNodeWithText(stringResource(commonR.string.block_insecure_action_enable_location))
                    .performScrollTo()
                    .performClick()

                registry.assertLocationPermissionRequested()
                assertTrue(openLocationSettingsClicked)
            }
        }
    }

    @Test
    fun `Given screen displayed with missing home setup when clicking fix then configure home network is triggered`() {
        composeTestRule.apply {
            testScreen(missingLocation = false, missingHomeSetup = true) {
                onNodeWithText(stringResource(commonR.string.block_insecure_missing_home_setup))
                    .performScrollTo()
                    .assertIsDisplayed()

                // Click the configure home network button
                onNodeWithText(stringResource(commonR.string.block_insecure_action_configure_home))
                    .performScrollTo()
                    .performClick()

                assertTrue(configureHomeNetworkClicked)
            }
        }
    }

    @Test
    fun `Given screen displayed when clicking open settings then open settings is triggered`() {
        composeTestRule.apply {
            testScreen(missingLocation = false, missingHomeSetup = false) {
                onNodeWithText(stringResource(commonR.string.block_insecure_open_settings))
                    .performScrollTo()
                    .assertIsDisplayed()
                    .performClick()

                assertTrue(openSettingsClicked)
            }
        }
    }

    @Test
    fun `Given screen displayed when clicking change security level then change security level is triggered`() {
        composeTestRule.apply {
            testScreen(missingLocation = false, missingHomeSetup = false) {
                onNodeWithText(stringResource(commonR.string.block_insecure_change_security_level))
                    .performScrollTo()
                    .assertIsDisplayed()
                    .performClick()

                assertTrue(changeSecurityLevelClicked)
            }
        }
    }

    @Test
    fun `Given screen displayed with both missing location and home setup then both banners are shown`() {
        composeTestRule.apply {
            testScreen(missingLocation = true, missingHomeSetup = true) {
                onNodeWithText(stringResource(commonR.string.block_insecure_missing_location))
                    .performScrollTo()
                    .assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.block_insecure_missing_home_setup))
                    .performScrollTo()
                    .assertIsDisplayed()
            }
        }
    }

    @Test
    fun `Given screen displayed with no missing then both banners are hidden`() {
        composeTestRule.apply {
            testScreen(missingLocation = false, missingHomeSetup = false) {
                onNodeWithText(stringResource(commonR.string.block_insecure_missing_location))
                    .assertIsNotDisplayed()
                onNodeWithText(stringResource(commonR.string.block_insecure_missing_home_setup))
                    .assertIsNotDisplayed()
            }
        }
    }

    private class TestHelper(locationPermissionGranted: Boolean) {
        var retryClicked = false
        var helpClicked = false
        var openSettingsClicked = false
        var changeSecurityLevelClicked = false
        var openLocationSettingsClicked = false
        var configureHomeNetworkClicked = false

        val registry = LocationPermissionActivityResultRegistry(locationPermissionGranted)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    private fun AndroidComposeTestRule<*, *>.testScreen(
        missingLocation: Boolean,
        missingHomeSetup: Boolean,
        locationPermissionGranted: Boolean = true,
        block: TestHelper.() -> Unit,
    ) {
        TestHelper(locationPermissionGranted).apply {
            setContent {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                        override val activityResultRegistry: ActivityResultRegistry = registry
                    },
                ) {
                    BlockInsecureScreen(
                        missingHomeSetup = missingHomeSetup,
                        missingLocation = missingLocation,
                        onRetry = { retryClicked = true },
                        onHelpClick = { helpClicked = true },
                        onOpenSettings = { openSettingsClicked = true },
                        onChangeSecurityLevel = { changeSecurityLevelClicked = true },
                        onOpenLocationSettings = { openLocationSettingsClicked = true },
                        onConfigureHomeNetwork = { configureHomeNetworkClicked = true },
                    )
                }
            }
            onNodeWithText(stringResource(commonR.string.block_insecure_title)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.block_insecure_content)).assertIsDisplayed()

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).assertIsDisplayed().performClick()
            assertTrue(helpClicked)

            onNodeWithContentDescription(stringResource(commonR.string.block_insecure_retry))
                .assertIsDisplayed()
                .performClick()
            assertTrue(retryClicked)

            block()
        }
    }
}
