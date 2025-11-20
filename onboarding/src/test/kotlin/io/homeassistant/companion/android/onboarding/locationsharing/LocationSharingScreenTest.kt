package io.homeassistant.companion.android.onboarding.locationsharing

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
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
import io.homeassistant.companion.android.compose.LocationPermissionActivityResultRegistry
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class LocationSharingScreenTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Test
    fun `Given screen displayed when clicking on share and granting permission then go to next screen and location response set to true`() {
        composeTestRule.apply {
            testScreen {
                // Check all elements are displayed
                onNodeWithText(stringResource(commonR.string.location_sharing_title)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.location_sharing_content)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.location_sharing_share)).performScrollTo().assertIsDisplayed().performClick()

                assertTrue(locationSharingResponse == true)
                assertTrue(goToNextScreenClicked)
                registry.assertLocationPermissionRequested()
            }
        }
    }

    @Test
    fun `Given screen displayed when clicking do not share then go to next screen and location response set to false`() {
        composeTestRule.apply {
            testScreen(false) {
                // Check all elements are displayed
                onNodeWithText(stringResource(commonR.string.location_sharing_title)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.location_sharing_content)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.location_sharing_no_share)).performScrollTo().assertIsDisplayed().performClick()

                assertTrue(locationSharingResponse == false)
                assertTrue(goToNextScreenClicked)
                registry.assertLocationPermissionNotRequested()
            }
        }
    }

    @Test
    fun `Given screen displayed when clicking on share and not granting permission then go to next screen and location response set to true`() {
        composeTestRule.apply {
            testScreen(false) {
                // Check all elements are displayed
                onNodeWithText(stringResource(commonR.string.location_sharing_title)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.location_sharing_content)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.location_sharing_share)).performScrollTo().assertIsDisplayed().performClick()

                assertTrue(locationSharingResponse == true)
                assertTrue(goToNextScreenClicked)
                // background is only requested if foreground is granted
                registry.assertLocationPermissionRequested(false)
            }
        }
    }

    private class TestHelper(locationPermissionGranted: Boolean) {
        var helpClicked = false
        var goToNextScreenClicked = false
        var locationSharingResponse: Boolean? = null

        val registry = LocationPermissionActivityResultRegistry(locationPermissionGranted)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    private fun AndroidComposeTestRule<*, *>.testScreen(
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
                    LocationSharingScreen(
                        onHelpClick = { helpClicked = true },
                        onGoToNextScreen = { goToNextScreenClicked = true },
                        onLocationSharingResponse = { locationSharingResponse = it },
                    )
                }
            }

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            assertTrue(helpClicked)

            block()
        }
    }
}
