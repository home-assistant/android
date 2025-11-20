package io.homeassistant.companion.android.compose

import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.ActivityNavigator
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.savedstate.SavedState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.automotive.navigation.AutomotiveRoute
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.compose.composable.HA_WEBVIEW_TAG
import io.homeassistant.companion.android.frontend.navigation.FrontendActivityRoute
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.WearOnboardingRoute
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.navigateToLocationForSecureConnection
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.stringResource
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class HAAppTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private lateinit var navController: TestNavHostController

    lateinit var activityNavigator: ActivityNavigator

    private fun testApp(startDestination: HAStartDestinationRoute?, isAutomotive: Boolean = false, testContent: suspend AndroidComposeTestRule<*, *>.() -> Unit) {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            activityNavigator = spyk(ActivityNavigator(composeTestRule.activity))
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            navController.navigatorProvider.addNavigator(activityNavigator)

            val spyActivity = spyk(composeTestRule.activity)
            val spyPackageManager = spyk(composeTestRule.activity.packageManager)
            every { spyActivity.packageManager } returns spyPackageManager
            every { spyPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) } returns isAutomotive

            CompositionLocalProvider(LocalActivity provides spyActivity) {
                HAApp(
                    navController = navController,
                    startDestination = startDestination,
                )
            }
        }
        runTest { composeTestRule.testContent() }
    }

    @Test
    fun `Given no start start destination when starts then show loading`() {
        testApp(null) {
            assertNull(navController.currentBackStackEntry)
            onNodeWithContentDescription(stringResource(commonR.string.loading_content_description)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given default OnboardingRoute as start when starts then show Welcome`() {
        testApp(OnboardingRoute(hasLocationTracking = true)) {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
            onNodeWithText(stringResource(commonR.string.welcome_home_assistant_title)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.welcome_details)).assertIsDisplayed()
            onNodeWithContentDescription(stringResource(commonR.string.home_assistant_branding_icon_content_description)).assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.welcome_connect_to_ha)).performScrollTo().assertIsDisplayed()
            onNodeWithText(stringResource(commonR.string.welcome_learn_more)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `Given OnboardingRoute with skipWelcome without urlToOnboard as start when starts then show ServerDiscovery`() {
        testApp(OnboardingRoute(hasLocationTracking = true, skipWelcome = true)) {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }

    @Test
    fun `Given OnboardingRoute with skipWelcome with urlToOnboard as start when starts then show ServerDiscovery`() {
        val url = "http://ha.org"
        testApp(OnboardingRoute(hasLocationTracking = true, skipWelcome = true, urlToOnboard = url)) {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true)
            assertEquals(url, navController.currentBackStackEntry?.toRoute<ConnectionRoute>()?.url)
        }
    }

    @Test
    fun `Given FrontendRoute as start when starts then navigate to Frontend and finish current activity`() {
        testApp(FrontendRoute()) {
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

    @Test
    fun `Given WearOnboardingRoute with url to onboard as start when starts then navigate to ConnectionScreen`() {
        testApp(WearOnboardingRoute("wear", "http://ha")) {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `Given WearOnboardingRoute without as start when starts then navigate to ServerDiscoveryScreen`() {
        testApp(WearOnboardingRoute("wear", null)) {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.searching_home_network)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given onboarding done then navigate to FrontEnd`() {
        testApp(OnboardingRoute(hasLocationTracking = true)) {
            navController.navigateToLocationForSecureConnection(42)

            onNodeWithText(stringResource(commonR.string.connection_security_less_secure)).performScrollTo().performClick()
            onNodeWithText(stringResource(commonR.string.location_secure_connection_next)).performScrollTo().assertIsEnabled().assertIsDisplayed().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<FrontendRoute>() == true)
        }
    }

    @Test
    fun `Given onboarding done on automotive then navigate to Automotive screen`() {
        testApp(OnboardingRoute(hasLocationTracking = true), isAutomotive = true) {
            navController.navigateToLocationForSecureConnection(42)

            onNodeWithText(stringResource(commonR.string.connection_security_less_secure)).performScrollTo().performClick()
            onNodeWithText(stringResource(commonR.string.location_secure_connection_next)).performScrollTo().assertIsEnabled().assertIsDisplayed().performClick()

            verify(exactly = 1) {
                activityNavigator.navigate(
                    match {
                        it.route == AutomotiveRoute.serializer().descriptor.serialName
                    },
                    any<SavedState>(),
                    any(),
                    any(),
                )
            }
        }
    }

    // TODO find a way to test the activity result since setResult is not exposed.
}
