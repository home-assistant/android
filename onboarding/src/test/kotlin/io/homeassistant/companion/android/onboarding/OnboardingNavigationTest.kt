package io.homeassistant.companion.android.onboarding

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.testing.TestNavHostController
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.compose.navigateToUri
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.testing.unit.stringResource
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.verify
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class OnboardingNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        mockkStatic(NavController::navigateToUri)
        every { any<NavController>().navigateToUri(any()) } just Runs

        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())

            NavHost(
                navController = navController,
                startDestination = OnboardingRoute,
            ) {
                onboarding(
                    navController,
                    onShowSnackbar = { message, action -> true },
                )
            }
        }
    }

    @Test
    fun `Given no action when starting the app then show Welcome`() {
        assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        composeTestRule.apply {
            onNodeWithText(stringResource(R.string.welcome_learn_more)).performScrollTo().assertIsDisplayed().performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io") }
        }
    }

    @Test
    fun `Given clicking on connect button when starting the onboarding then show ServerDiscovery then back goes to Welcome`() {
        composeTestRule.apply {
            onNodeWithText(stringResource(R.string.welcome_connect_to_ha)).assertIsDisplayed().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io/installation/") }

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }
}
