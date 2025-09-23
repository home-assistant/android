package io.homeassistant.companion.android.compose

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.ActivityNavigator
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.savedstate.SavedState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.frontend.navigation.FrontendActivityRoute
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.stringResource
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class HAAppTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    lateinit var activityNavigator: ActivityNavigator

    private fun setApp(startDestination: HAStartDestinationRoute?) {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            activityNavigator = spyk(ActivityNavigator(composeTestRule.activity))
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            navController.navigatorProvider.addNavigator(activityNavigator)

            HAApp(
                navController = navController,
                startDestination = startDestination,
            )
        }
    }

    @Test
    fun `Given HAApp when no start destination then show loading`() {
        setApp(null)
        composeTestRule.apply {
            assertNull(navController.currentBackStackEntry)
            onNodeWithContentDescription(stringResource(R.string.loading_content_description)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given HAApp when navigate to Welcome then show Welcome`() {
        setApp(OnboardingRoute())
        composeTestRule.apply {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
            onNodeWithText(stringResource(R.string.welcome_home_assistant_title)).assertIsDisplayed()
            onNodeWithText(stringResource(R.string.welcome_details)).assertIsDisplayed()
            onNodeWithContentDescription(stringResource(R.string.home_assistant_branding_icon_content_description)).assertIsDisplayed()
            onNodeWithText(stringResource(R.string.welcome_connect_to_ha)).performScrollTo().assertIsDisplayed()
            onNodeWithText(stringResource(R.string.welcome_learn_more)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `Given HAApp when navigate to Frontend then navigate to FrontEndActivity and finish current activity`() {
        setApp(FrontendRoute())
        composeTestRule.apply {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<FrontendRoute>() == true)
            verify(exactly = 1) {
                activityNavigator.navigate(
                    match {
                        it.route == FrontendActivityRoute.serializer().descriptor.serialName + "?path={path}&server={server}"
                    },
                    any<SavedState>(),
                    any(),
                    any(),
                )
            }
            // TODO remove this once we are using WebViewActivity anymore
            assertTrue(activity.isFinishing)
        }
    }
}
