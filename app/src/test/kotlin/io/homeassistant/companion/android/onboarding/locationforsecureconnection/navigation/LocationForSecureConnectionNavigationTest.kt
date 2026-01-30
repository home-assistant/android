package io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.LocationForSecureConnectionViewModel
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.SetHomeNetworkRoute
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.compose.navigateToUri
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
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

    // Mock the ViewModel to prevent the real allowInsecureConnection() from executing.
    // Without this, the suspend function switches to Dispatchers.IO and the coroutine resumes
    // on a non-main thread, causing navigation to fail with "Method setCurrentState must be
    // called on the main thread" because LifecycleRegistry requires main thread access.
    // This occurs due to the interaction between Robolectric, Compose testing, and coroutines,
    // where dispatcher context is not properly preserved across suspend function boundaries.
    @BindValue
    @JvmField
    val locationForSecureConnectionViewModel = spyk(LocationForSecureConnectionViewModel(0, mockk())).apply {
        coJustRun { this@apply.allowInsecureConnection(any()) }
    }

    @Test
    fun `Given LocationForSecureConnection when agreeing to share then show SetHomeNetwork`() {
        testNavigation {
            navController.navigateToLocationForSecureConnection(42)
            assertTrue(
                navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true,
            )

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            coVerify { any<NavController>().navigateToUri(URL_SECURITY_LEVEL_DOCUMENTATION, any()) }

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
            coVerify { any<NavController>().navigateToUri(URL_SECURITY_LEVEL_DOCUMENTATION, any()) }

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
