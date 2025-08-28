package io.homeassistant.companion.android.compose

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.navigateToFrontend
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.onboarding.navigateToOnboarding
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.stringResources
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
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
        Timber.Forest.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true

        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())

            HAApp(
                navController = navController,
            )
        }
    }

    @Test
    fun `Given HAApp when navigate to Welcome then show Welcome`() {
        composeTestRule.apply {
            navController.navigateToOnboarding()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
            onNodeWithText(stringResources(R.string.welcome_home_assistant_title)).assertIsDisplayed()
            onNodeWithText(stringResources(R.string.welcome_details)).assertIsDisplayed()
            onNodeWithText(stringResources(R.string.welcome_connect_to_ha)).assertIsDisplayed()
            onNodeWithText(stringResources(R.string.welcome_learn_more)).assertIsDisplayed()
            onNodeWithContentDescription(stringResources(R.string.home_assistant_branding_icon_content_description)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given HAApp when navigate to Welcome then show Frontend`() {
        composeTestRule.apply {
            navController.navigateToFrontend()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<FrontendRoute>() == true)
            onNodeWithTag("frontend_placeholder").assertIsDisplayed()
        }
    }
}
