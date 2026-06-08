package io.homeassistant.companion.android.onboarding.welcome.navigation

import androidx.compose.ui.test.assertIsDisplayed
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
import io.homeassistant.companion.android.onboarding.URL_GETTING_STARTED_DOCUMENTATION
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.compose.navigateToUri
import io.mockk.coVerify
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val INVITATION_URL = "http://homeassistant.local:8123"

/**
 * Navigation tests for the WelcomeInvitation screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class WelcomeInvitationNavigationTest : BaseOnboardingNavigationTest() {

    @Test
    fun `Given invitation link when starting the app then show WelcomeInvitation`() {
        testNavigation(urlToOnboard = INVITATION_URL, fromInvitation = true) {
            assertTrue(
                navController.currentBackStackEntry?.destination?.hasRoute<WelcomeInvitationRoute>() == true,
            )
            onNodeWithText(INVITATION_URL).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `Given WelcomeInvitation when click Accept then navigate to Connection`() {
        testNavigation(urlToOnboard = INVITATION_URL, fromInvitation = true) {
            onNodeWithText(stringResource(commonR.string.welcome_invitation_accept))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            assertTrue(
                navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true,
            )
        }
    }

    @Test
    fun `Given WelcomeInvitation when click Learn more then open docs`() {
        testNavigation(urlToOnboard = INVITATION_URL, fromInvitation = true) {
            // Learn more lives in the top bar as a help action.
            onNodeWithContentDescription(stringResource(commonR.string.get_help))
                .assertIsDisplayed()
                .performClick()
            coVerify { any<NavController>().navigateToUri(URL_GETTING_STARTED_DOCUMENTATION, any()) }
        }
    }

    @Test
    fun `Given no invitation flag when starting the app then show Welcome not WelcomeInvitation`() {
        testNavigation(urlToOnboard = INVITATION_URL, fromInvitation = false) {
            assertTrue(
                navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true,
            )
        }
    }
}
