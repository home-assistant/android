package io.homeassistant.companion.android.onboarding.serverdiscovery.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.onboarding.URL_GETTING_STARTED_DOCUMENTATION
import io.homeassistant.companion.android.onboarding.connection.CONNECTION_SCREEN_TAG
import io.homeassistant.companion.android.onboarding.connection.ConnectionNavigationEvent
import io.homeassistant.companion.android.onboarding.connection.ConnectionViewModel
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.onboarding.manualserver.navigation.ManualServerRoute
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.NameYourDeviceRoute
import io.homeassistant.companion.android.onboarding.serverdiscovery.DELAY_BEFORE_DISPLAY_DISCOVERY
import io.homeassistant.companion.android.onboarding.serverdiscovery.HomeAssistantInstance
import io.homeassistant.companion.android.onboarding.serverdiscovery.HomeAssistantSearcher
import io.homeassistant.companion.android.onboarding.serverdiscovery.ONE_SERVER_FOUND_MODAL_TAG
import io.homeassistant.companion.android.onboarding.serverdiscovery.ServerDiscoveryModule
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.testing.unit.stringResource
import io.homeassistant.companion.android.util.compose.navigateToUri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URL
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the Server Discovery screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(ServerDiscoveryModule::class)
@HiltAndroidTest
internal class ServerDiscoveryNavigationTest : BaseOnboardingNavigationTest() {

    @BindValue
    @JvmField
    val searcher: HomeAssistantSearcher = object : HomeAssistantSearcher {
        override fun discoveredInstanceFlow(): Flow<HomeAssistantInstance> {
            return instanceChannel.consumeAsFlow()
        }
    }

    val instanceChannel = Channel<HomeAssistantInstance>()

    val connectionNavigationEventFlow = MutableSharedFlow<ConnectionNavigationEvent>()

    @BindValue
    @JvmField
    val connectionViewModel: ConnectionViewModel = mockk(relaxed = true) {
        every { urlFlow } returns MutableStateFlow("http://homeassistant.local:8123")
        every { isLoadingFlow } returns MutableStateFlow(false)
        every { navigationEventsFlow } returns connectionNavigationEventFlow
        every { errorFlow } returns MutableStateFlow(null)
        every { connectivityCheckState } returns MutableStateFlow(ConnectivityCheckState())
    }

    @Test
    fun `Given skipWelcome without urlToOnboard when starting then show ServerDiscovery`() {
        testNavigation(skipWelcome = true) {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }

    @Test
    fun `Given clicking on connect button when starting the onboarding then show ServerDiscovery then back goes to Welcome`() {
        testNavigation {
            onNodeWithText(stringResource(commonR.string.welcome_connect_to_ha))
                .assertIsDisplayed()
                .performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri(URL_GETTING_STARTED_DOCUMENTATION) }

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up))
                .assertIsDisplayed()
                .performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given clicking on connect button with hide existing server and no server to onboard when starting the onboarding then show Discovery screen with existing server hidden then back goes to Welcome`() {
        testNavigation(hideExistingServers = true) {
            onNodeWithText(stringResource(commonR.string.welcome_connect_to_ha))
                .assertIsDisplayed()
                .performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            assertTrue(
                navController.currentBackStackEntry?.toRoute<ServerDiscoveryRoute>()?.discoveryMode ==
                    ServerDiscoveryMode.HIDE_EXISTING,
            )

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up))
                .assertIsDisplayed()
                .performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given clicking enter manual address button when discovering server then show ManualServer then back goes to ServerDiscovery`() {
        testNavigation {
            navController.navigateToServerDiscovery()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.manual_setup))
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ManualServerRoute>() == true)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri(URL_GETTING_STARTED_DOCUMENTATION) }

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up))
                .assertIsDisplayed()
                .performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Given a server discovered when clicking on it then show ConnectScreen then back goes to ServerDiscovery`() {
        val instanceUrl = "http://ha.local"
        testNavigation {
            navController.navigateToServerDiscovery()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.manual_setup))
                .performScrollTo()
                .assertIsDisplayed()

            instanceChannel.trySend(
                HomeAssistantInstance("Test", URL(instanceUrl), HomeAssistantVersion(2025, 9, 1)),
            )

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri(URL_GETTING_STARTED_DOCUMENTATION) }

            waitUntilAtLeastOneExists(
                hasText(instanceUrl),
                timeoutMillis = DELAY_BEFORE_DISPLAY_DISCOVERY.inWholeMilliseconds,
            )

            onNodeWithTag(ONE_SERVER_FOUND_MODAL_TAG).performTouchInput {
                swipeUp(startY = bottom * 0.9f, endY = centerY, durationMillis = 200)
            }

            waitForIdle()

            onNodeWithText(instanceUrl).assertIsDisplayed()

            onNodeWithText(stringResource(commonR.string.server_discovery_connect))
                .assertIsDisplayed()
                .performClick()

            waitForIdle()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true)

            onNodeWithTag(CONNECTION_SCREEN_TAG).assertIsDisplayed()

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Given a server discovered and connecting when authenticated then show NameYourDevice then back goes to ServerDiscovery not ConnectionScreen`() {
        val instanceUrl = "http://ha.local"
        testNavigation {
            navController.navigateToServerDiscovery()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.manual_setup))
                .performScrollTo()
                .assertIsDisplayed()

            instanceChannel.trySend(
                HomeAssistantInstance("Test", URL(instanceUrl), HomeAssistantVersion(2025, 9, 1)),
            )

            waitUntilAtLeastOneExists(
                hasText(instanceUrl),
                timeoutMillis = DELAY_BEFORE_DISPLAY_DISCOVERY.inWholeMilliseconds,
            )

            onNodeWithText(instanceUrl).assertIsDisplayed()

            onNodeWithText(stringResource(commonR.string.server_discovery_connect)).performClick()

            onNodeWithTag(CONNECTION_SCREEN_TAG).assertIsDisplayed()

            assertTrue(connectionNavigationEventFlow.subscriptionCount.value == 1)
            connectionNavigationEventFlow.emit(
                ConnectionNavigationEvent.Authenticated(instanceUrl, "super_code", false),
            )

            waitUntilAtLeastOneExists(hasText(stringResource(commonR.string.name_your_device_title)))
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)
            val route = navController.currentBackStackEntry?.toRoute<NameYourDeviceRoute>()
            assertEquals(instanceUrl, route?.url)
            assertEquals("super_code", route?.authCode)

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }
}
