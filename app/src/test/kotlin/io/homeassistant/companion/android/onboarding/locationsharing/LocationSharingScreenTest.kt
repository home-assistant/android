package io.homeassistant.companion.android.onboarding.locationsharing

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
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
import androidx.test.core.app.ApplicationProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.LocationPermissionActivityResultRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    @Before
    fun setUp() {
        // Mock PackageManager to resolve the battery optimization intent.
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val shadowPackageManager = shadowOf(context.packageManager)

        // Register a fake activity that handles the battery optimization intent
        val component = ComponentName("com.android.settings", "BatteryOptimizationActivity")
        shadowPackageManager.addActivityIfNotPresent(component)
        // IntentFilter needs CATEGORY_DEFAULT for MATCH_DEFAULT_ONLY and data scheme for the URI
        val intentFilter = IntentFilter(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("package")
        }
        shadowPackageManager.addIntentFilterForActivity(component, intentFilter)
    }

    @Test
    fun `Given screen displayed when clicking on share and granting permission then go to next screen and location response set to true and ask for battery optimization`() {
        composeTestRule.apply {
            testScreen {
                // Check all elements are displayed
                onNodeWithText(stringResource(commonR.string.location_sharing_title)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.location_sharing_content)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.location_sharing_share)).performScrollTo().assertIsDisplayed().performClick()

                assertTrue(locationSharingResponse == true)
                assertTrue(goToNextScreenClicked)
                registry.assertLocationPermissionRequested()
                registry.assertBatteryOptimizationRequested()
            }
        }
    }

    @Test
    fun `Given screen displayed when clicking do not share then go to next screen and location response set to false without battery optimization`() {
        composeTestRule.apply {
            testScreen(false) {
                // Check all elements are displayed
                onNodeWithText(stringResource(commonR.string.location_sharing_title)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.location_sharing_content)).assertIsDisplayed()
                onNodeWithText(stringResource(commonR.string.location_sharing_no_share)).performScrollTo().assertIsDisplayed().performClick()

                assertTrue(locationSharingResponse == false)
                assertTrue(goToNextScreenClicked)
                registry.assertLocationPermissionNotRequested()
                registry.assertBatteryOptimizationNotRequested()
            }
        }
    }

    @Test
    fun `Given screen displayed when clicking on share and not granting permission then go to next screen and location response set to true and still ask for battery optimization`() {
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
                // Battery optimization is still requested even when permission is denied
                registry.assertBatteryOptimizationRequested()
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
