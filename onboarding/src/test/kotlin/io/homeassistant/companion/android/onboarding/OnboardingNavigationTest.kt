package io.homeassistant.companion.android.onboarding

import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.compose.LocationPermissionActivityResultRegistry
import io.homeassistant.companion.android.compose.navigateToUri
import io.homeassistant.companion.android.onboarding.connection.CONNECTION_SCREEN_TAG
import io.homeassistant.companion.android.onboarding.connection.ConnectionNavigationEvent
import io.homeassistant.companion.android.onboarding.connection.ConnectionViewModel
import io.homeassistant.companion.android.onboarding.localfirst.navigation.LocalFirstRoute
import io.homeassistant.companion.android.onboarding.localfirst.navigation.navigateToLocalFirst
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.LocationForSecureConnectionRoute
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.navigateToLocationForSecureConnection
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.LocationSharingRoute
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.navigateToLocationSharing
import io.homeassistant.companion.android.onboarding.manualserver.navigation.ManualServerRoute
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceNavigationEvent
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceViewModel
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.NameYourDeviceRoute
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.navigateToNameYourDevice
import io.homeassistant.companion.android.onboarding.serverdiscovery.DELAY_BEFORE_DISPLAY_DISCOVERY
import io.homeassistant.companion.android.onboarding.serverdiscovery.HomeAssistantInstance
import io.homeassistant.companion.android.onboarding.serverdiscovery.HomeAssistantSearcher
import io.homeassistant.companion.android.onboarding.serverdiscovery.ServerDiscoveryModule
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryRoute
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.navigateToServerDiscovery
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.stringResource
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import java.net.URL
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(ServerDiscoveryModule::class)
@HiltAndroidTest
internal class OnboardingNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @BindValue
    @JvmField
    val searcher: HomeAssistantSearcher = object : HomeAssistantSearcher {
        override fun discoveredInstanceFlow(): Flow<HomeAssistantInstance> {
            return instanceChannel.consumeAsFlow()
        }
    }

    private val connectionNavigationEventFlow = MutableSharedFlow<ConnectionNavigationEvent>()

    @BindValue
    @JvmField
    val connectionViewModel: ConnectionViewModel = mockk(relaxed = true) {
        every { urlFlow } returns MutableStateFlow("http://homeassistant.local:8123")
        every { isLoadingFlow } returns MutableStateFlow(false)
        every { navigationEventsFlow } returns connectionNavigationEventFlow
        every { isErrorFlow } returns MutableStateFlow(false)
    }

    @BindValue
    @JvmField
    val nameYourDeviceViewModel: NameYourDeviceViewModel = mockk(relaxed = true) {
        val nameYourDeviceNavigationFlow = MutableSharedFlow<NameYourDeviceNavigationEvent>()
        every { navigationEventsFlow } returns nameYourDeviceNavigationFlow
        every { onSaveClick() } coAnswers {
            nameYourDeviceNavigationFlow.emit(NameYourDeviceNavigationEvent.DeviceNameSaved(42, false))
        }
        every { deviceNameFlow } returns MutableStateFlow("Test")
        every { isValidNameFlow } returns MutableStateFlow(true)
        every { isSaveClickableFlow } returns MutableStateFlow(true)
        every { isSavingFlow } returns MutableStateFlow(false)
    }

    private val instanceChannel = Channel<HomeAssistantInstance>()

    private lateinit var navController: TestNavHostController

    private var onboardingDone = false

    @Before
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true

        mockkStatic(NavController::navigateToUri)
        every { any<NavController>().navigateToUri(any()) } just Runs

        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())

            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                    override val activityResultRegistry: ActivityResultRegistry = LocationPermissionActivityResultRegistry(true)
                },
            ) {
                NavHost(
                    navController = navController,
                    startDestination = OnboardingRoute,
                ) {
                    onboarding(
                        navController,
                        onShowSnackbar = { message, action -> true },
                        onOnboardingDone = {
                            onboardingDone = true
                        },
                    )
                }
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

    @Test
    fun `Given clicking enter manual address button when discovering server then show ManualServer then back goes to ServerDiscovery`() {
        composeTestRule.apply {
            navController.navigateToServerDiscovery()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.manual_setup)).assertIsDisplayed().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ManualServerRoute>() == true)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io/installation/") }

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }

    @Test
    fun `Given enter manual address when setting url and clicking connect then show ConnectScreen then back goes to ManualServer`() {
        composeTestRule.apply {
            navController.navigateToServerDiscovery()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.manual_setup)).assertIsDisplayed().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ManualServerRoute>() == true)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io/installation/") }

            onNodeWithText("http://homeassistant.local:8123").performTextInput("http://ha.local")

            onNodeWithText(stringResource(commonR.string.connect)).assertIsEnabled().performClick()

            onNodeWithTag(CONNECTION_SCREEN_TAG).assertIsDisplayed()

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ManualServerRoute>() == true)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Given a server discovered when clicking on it then show ConnectScreen then back goes to ServerDiscovery`() {
        val instanceUrl = "http://ha.local"
        composeTestRule.apply {
            navController.navigateToServerDiscovery()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.manual_setup)).assertIsDisplayed()

            instanceChannel.trySend(HomeAssistantInstance("Test", URL(instanceUrl), HomeAssistantVersion(2025, 9, 1)))

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io/installation/") }

            // Wait for the screen to update based on the instance given in instanceChannel
            waitUntilAtLeastOneExists(hasText(instanceUrl), timeoutMillis = DELAY_BEFORE_DISPLAY_DISCOVERY.inWholeMilliseconds)

            onNodeWithText(instanceUrl).assertIsDisplayed()

            onNodeWithText(stringResource(R.string.server_discovery_connect)).performClick()

            onNodeWithTag(CONNECTION_SCREEN_TAG).assertIsDisplayed()

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Given a server discovered and connecting when authenticated then show NameYourDevice then back goes to ServerDiscovery not ConnectionScreen`() = runTest {
        val instanceUrl = "http://ha.local"
        composeTestRule.apply {
            navController.navigateToServerDiscovery()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.manual_setup)).assertIsDisplayed()

            instanceChannel.trySend(HomeAssistantInstance("Test", URL(instanceUrl), HomeAssistantVersion(2025, 9, 1)))

            // Wait for the screen to update based on the instance given in instanceChannel
            waitUntilAtLeastOneExists(hasText(instanceUrl), timeoutMillis = DELAY_BEFORE_DISPLAY_DISCOVERY.inWholeMilliseconds)

            onNodeWithText(instanceUrl).assertIsDisplayed()

            onNodeWithText(stringResource(R.string.server_discovery_connect)).performClick()

            onNodeWithTag(CONNECTION_SCREEN_TAG).assertIsDisplayed()

            assertTrue(connectionNavigationEventFlow.subscriptionCount.value == 1)
            connectionNavigationEventFlow.emit(ConnectionNavigationEvent.Authenticated(instanceUrl, "super_code"))

            waitUntilAtLeastOneExists(hasText(stringResource(R.string.name_your_device_title)))
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)
            val route = navController.currentBackStackEntry?.toRoute<NameYourDeviceRoute>()
            assertEquals(instanceUrl, route?.url)
            assertEquals("super_code", route?.authCode)

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }

    @Test
    fun `Given device named when pressing next then show LocalFirst then goes back stop the app`() = runTest {
        val instanceUrl = "http://ha.local"
        composeTestRule.apply {
            navController.navigateToNameYourDevice(instanceUrl, "code")
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)

            onNodeWithText(stringResource(R.string.name_your_device_save)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            // The back stack is unchanged in this situation, but in reality the app is in background
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)
        }
    }

    @Test
    fun `Given LocalFirst when pressing next then show LocationSharing then goes back stop the app`() {
        composeTestRule.apply {
            navController.navigateToLocalFirst(42, true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)
            onNodeWithText(stringResource(R.string.local_first_next)).performScrollTo().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            // In the test scenario since we never opened NameYourDevice the stack still contains Welcome
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given LocationSharing when agreeing with plain text access to share then onboarding is done`() {
        composeTestRule.apply {
            navController.navigateToLocationSharing(42, true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            mockkStatic(ContextCompat::class)
            every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

            onNodeWithText(stringResource(R.string.location_sharing_share)).performScrollTo().performClick()

            assertTrue(onboardingDone)
        }
    }

    @Test
    fun `Given LocationSharing when agreeing without plain text access to share then onboarding is done`() {
        composeTestRule.apply {
            navController.navigateToLocationSharing(42, false)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            mockkStatic(ContextCompat::class)
            every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

            onNodeWithText(stringResource(R.string.location_sharing_share)).performScrollTo().performClick()

            assertTrue(onboardingDone)
        }
    }

    @Test
    fun `Given LocationSharing when denying to share with plain text access then goes to LocationForSecureConnection then goes back stop the app`() {
        composeTestRule.apply {
            navController.navigateToLocationSharing(42, true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            mockkStatic(ContextCompat::class)
            every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED

            onNodeWithText(stringResource(R.string.location_sharing_no_share)).performScrollTo().performClick()
            assertFalse(onboardingDone)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true)

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            // In the test scenario since we never opened NameYourDevice the stack still contains Welcome
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given LocationSharing when denying to share without plain text access then onboarding is done`() {
        composeTestRule.apply {
            navController.navigateToLocationSharing(42, false)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            mockkStatic(ContextCompat::class)
            every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED

            onNodeWithText(stringResource(R.string.location_sharing_no_share)).performScrollTo().performClick()
            assertTrue(onboardingDone)
        }
    }

    @Test
    fun `Given LocationForSecureConnection when agreeing to share then onboarding is done`() {
        composeTestRule.apply {
            navController.navigateToLocationForSecureConnection(42)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true)

            onNodeWithText(stringResource(R.string.location_secure_connection_most_secure)).performScrollTo().performClick()
            onNodeWithText(stringResource(R.string.location_secure_connection_next)).performScrollTo().performClick()

            assertTrue(onboardingDone)
        }
    }

    // TODO maybe split this file into multiples one dedicated to each screen
}
