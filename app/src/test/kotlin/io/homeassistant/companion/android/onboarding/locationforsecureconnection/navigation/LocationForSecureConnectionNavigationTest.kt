package io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.SetHomeNetworkRoute
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.compose.navigateToUri
import io.mockk.verify
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the Location For Secure Connection screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class LocationForSecureConnectionNavigationTest : BaseOnboardingNavigationTest() {

    @Test
    fun `Given LocationForSecureConnection when agreeing to share then show SetHomeNetwork`() {
        testNavigation {
            navController.navigateToLocationForSecureConnection(42)
            assertTrue(
                navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true,
            )

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri(URL_SECURITY_LEVEL_DOCUMENTATION) }

            onNodeWithText(stringResource(commonR.string.connection_security_most_secure))
                .performScrollTo()
                .performClick()
            onNodeWithText(stringResource(commonR.string.location_secure_connection_next))
                .performScrollTo()
                .performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<SetHomeNetworkRoute>() == true)
        }
    }

    @Test
    fun `Given LocationForSecureConnection when choosing less secure option then onboarding completes`() {
        testNavigation {
            navController.navigateToLocationForSecureConnection(42)
            assertTrue(
                navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true,
            )

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri(URL_SECURITY_LEVEL_DOCUMENTATION) }

            onNodeWithText(stringResource(commonR.string.connection_security_less_secure))
                .performScrollTo()
                .performClick()
            onNodeWithText(stringResource(commonR.string.location_secure_connection_next))
                .performScrollTo()
                .performClick()

            assertTrue(onboardingDone)
        }
    }
}
