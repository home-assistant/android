package io.homeassistant.companion.android.onboarding.welcome.navigation

import androidx.compose.ui.test.assertIsDisplayed
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
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.compose.navigateToUri
import io.mockk.coVerify
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the Welcome screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class WelcomeNavigationTest : BaseOnboardingNavigationTest() {

    @Test
    fun `Given no action when starting the app then show Welcome`() {
        testNavigation {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
            onNodeWithText(stringResource(commonR.string.welcome_learn_more))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            coVerify { any<NavController>().navigateToUri(URL_GETTING_STARTED_DOCUMENTATION, any()) }
        }
    }
}
