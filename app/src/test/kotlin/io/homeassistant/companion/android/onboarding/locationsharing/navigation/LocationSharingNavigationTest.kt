package io.homeassistant.companion.android.onboarding.locationsharing.navigation

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavDestination.Companion.hasRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.LocationForSecureConnectionRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.testing.unit.stringResource
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the Location Sharing screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class LocationSharingNavigationTest : BaseOnboardingNavigationTest() {

    @Test
    fun `Given LocationSharing when agreeing with plain text access to share then show LocationForSecureConnection then goes back stop the app`() {
        testNavigation {
            navController.navigateToLocationSharing(42, true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)
            mockCheckPermission(true)

            onNodeWithText(stringResource(commonR.string.location_sharing_share))
                .performScrollTo()
                .performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true)

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            // In the test scenario since we never opened NameYourDevice the stack still contains Welcome
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given LocationSharing when agreeing without plain text access to share then onboarding is done`() {
        testNavigation {
            navController.navigateToLocationSharing(42, false)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            mockCheckPermission(true)
            onNodeWithText(stringResource(commonR.string.location_sharing_share))
                .performScrollTo()
                .performClick()

            assertTrue(onboardingDone)
        }
    }

    @Test
    fun `Given LocationSharing when denying to share with plain text access then goes to LocationForSecureConnection then goes back stop the app`() {
        testNavigation {
            navController.navigateToLocationSharing(42, true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            mockCheckPermission(false)

            onNodeWithText(stringResource(commonR.string.location_sharing_no_share))
                .performScrollTo()
                .performClick()
            assertFalse(onboardingDone)
            assertTrue(
                navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true,
            )

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            // In the test scenario since we never opened NameYourDevice the stack still contains Welcome
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given LocationSharing when denying to share without plain text access then onboarding is done`() {
        testNavigation {
            navController.navigateToLocationSharing(42, false)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            mockCheckPermission(false)
            onNodeWithText(stringResource(commonR.string.location_sharing_no_share))
                .performScrollTo()
                .performClick()
            assertTrue(onboardingDone)
        }
    }
}
