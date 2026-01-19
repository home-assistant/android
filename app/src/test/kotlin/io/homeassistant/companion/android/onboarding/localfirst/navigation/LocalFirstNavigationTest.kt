package io.homeassistant.companion.android.onboarding.localfirst.navigation

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavDestination.Companion.hasRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.LocationForSecureConnectionRoute
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.LocationSharingRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.testing.unit.stringResource
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the Local First screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class LocalFirstNavigationTest : BaseOnboardingNavigationTest() {

    @Test
    fun `Given LocalFirst when pressing next then show LocationSharing then goes back stop the app`() {
        testNavigation {
            navController.navigateToLocalFirst(42, true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)
            onNodeWithText(stringResource(commonR.string.local_first_next))
                .performScrollTo()
                .performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            // In the test scenario since we never opened NameYourDevice the stack still contains Welcome
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given no location tracking from LocalFirst with HTTP when next clicked then show LocationForSecureConnection`() {
        testNavigation(hasLocationTracking = false) {
            navController.navigateToLocalFirst(serverId = 42, hasPlainTextAccess = true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)

            onNodeWithText(stringResource(commonR.string.local_first_next))
                .performScrollTo()
                .performClick()

            assertTrue(
                navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true,
            )
        }
    }

    @Test
    fun `Given no location tracking from LocalFirst with HTTPS when next clicked then onboarding completes`() {
        testNavigation(hasLocationTracking = false) {
            navController.navigateToLocalFirst(serverId = 42, hasPlainTextAccess = false)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)

            onNodeWithText(stringResource(commonR.string.local_first_next))
                .performScrollTo()
                .performClick()

            assertTrue(onboardingDone)
        }
    }
}
