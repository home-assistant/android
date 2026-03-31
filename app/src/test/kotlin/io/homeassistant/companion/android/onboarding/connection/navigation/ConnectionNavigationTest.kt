package io.homeassistant.companion.android.onboarding.connection.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.onboarding.connection.CONNECTION_SCREEN_TAG
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.testing.unit.stringResource
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the Connection screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class ConnectionNavigationTest : BaseOnboardingNavigationTest() {

    @Test
    fun `Given skipWelcome and urlToOnboard when starting then show Connection screen and no back arrow`() {
        val url = "http://ha.org"
        testNavigation(skipWelcome = true, urlToOnboard = url) {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true)
            assertEquals(url, navController.currentBackStackEntry?.toRoute<ConnectionRoute>()?.url)

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsNotDisplayed()
        }
    }

    @Test
    fun `Given clicking on connect button with server to onboard when starting the onboarding then show Connection screen then back goes to Welcome`() {
        testNavigation("http://homeassistant.local") {
            onNodeWithText(stringResource(commonR.string.welcome_connect_to_ha))
                .assertIsDisplayed()
                .performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true)

            onNodeWithTag(CONNECTION_SCREEN_TAG).assertIsDisplayed()

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }
}
