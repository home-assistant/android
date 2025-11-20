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
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
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
import io.homeassistant.companion.android.onboarding.serverdiscovery.ONE_SERVER_FOUND_MODAL_TAG
import io.homeassistant.companion.android.onboarding.serverdiscovery.ServerDiscoveryModule
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryMode
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryRoute
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.navigateToServerDiscovery
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.SetHomeNetworkRoute
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.navigateToSetHomeNetworkRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
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

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(ServerDiscoveryModule::class)
@HiltAndroidTest
internal class OnboardingNavigationTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
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
        every { errorFlow } returns MutableStateFlow(null)
    }

    private val nameYourDeviceNavigationFlow = MutableSharedFlow<NameYourDeviceNavigationEvent>()

    @BindValue
    @JvmField
    val nameYourDeviceViewModel: NameYourDeviceViewModel = mockk(relaxed = true) {
        every { navigationEventsFlow } returns nameYourDeviceNavigationFlow
        every { onSaveClick() } coAnswers {
            nameYourDeviceNavigationFlow.emit(NameYourDeviceNavigationEvent.DeviceNameSaved(42, hasPlainTextAccess = false, isPubliclyAccessible = false))
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
        mockkStatic(NavController::navigateToUri)
        every { any<NavController>().navigateToUri(any()) } just Runs
    }

    private fun setContent(
        urlToOnboard: String? = null,
        hideExistingServers: Boolean = false,
        skipWelcome: Boolean = false,
        hasLocationTracking: Boolean = true,
    ) {
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
                    startDestination = OnboardingRoute(hasLocationTracking = true),
                ) {
                    onboarding(
                        navController,
                        onShowSnackbar = { message, action -> true },
                        onOnboardingDone = {
                            onboardingDone = true
                        },
                        urlToOnboard = urlToOnboard,
                        hideExistingServers = hideExistingServers,
                        skipWelcome = skipWelcome,
                        hasLocationTracking = hasLocationTracking,
                    )
                }
            }
        }
    }

    private fun testNavigation(
        urlToOnboard: String? = null,
        hideExistingServers: Boolean = false,
        skipWelcome: Boolean = false,
        hasLocationTracking: Boolean = true,
        testContent: suspend AndroidComposeTestRule<*, *>.() -> Unit,
    ) {
        setContent(
            urlToOnboard = urlToOnboard,
            hideExistingServers = hideExistingServers,
            skipWelcome = skipWelcome,
            hasLocationTracking = hasLocationTracking,
        )
        runTest {
            composeTestRule.testContent()
        }
    }

    @Test
    fun `Given no action when starting the app then show Welcome`() {
        testNavigation {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
            onNodeWithText(stringResource(commonR.string.welcome_learn_more)).performScrollTo().assertIsDisplayed().performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io") }
        }
    }

    @Test
    fun `Given skipWelcome without urlToOnboard when starting then show ServerDiscovery`() {
        testNavigation(skipWelcome = true) {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }

    @Test
    fun `Given skipWelcome and urlToOnboard when starting then show ServerDiscovery and no back arrow`() {
        val url = "http://ha.org"
        testNavigation(skipWelcome = true, urlToOnboard = url) {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true)
            assertEquals(url, navController.currentBackStackEntry?.toRoute<ConnectionRoute>()?.url)

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsNotDisplayed()
        }
    }

    @Test
    fun `Given clicking on connect button when starting the onboarding then show ServerDiscovery then back goes to Welcome`() {
        testNavigation {
            onNodeWithText(stringResource(commonR.string.welcome_connect_to_ha)).assertIsDisplayed().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io/installation/") }

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given clicking on connect button with server to onboard when starting the onboarding then show Connection screen then back goes to Welcome`() {
        testNavigation("http://homeassistant.local") {
            onNodeWithText(stringResource(commonR.string.welcome_connect_to_ha)).assertIsDisplayed().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true)

            onNodeWithTag(CONNECTION_SCREEN_TAG).assertIsDisplayed()

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given clicking on connect button with hide existing server and no server to onboard when starting the onboarding then show Discovery screen with existing server hidden then back goes to Welcome`() {
        testNavigation(hideExistingServers = true) {
            onNodeWithText(stringResource(commonR.string.welcome_connect_to_ha)).assertIsDisplayed().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            assertTrue(navController.currentBackStackEntry?.toRoute<ServerDiscoveryRoute>()?.discoveryMode == ServerDiscoveryMode.HIDE_EXISTING)

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given clicking enter manual address button when discovering server then show ManualServer then back goes to ServerDiscovery`() {
        testNavigation {
            navController.navigateToServerDiscovery()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.manual_setup)).performScrollTo().assertIsDisplayed().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ManualServerRoute>() == true)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io/installation/") }

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).assertIsDisplayed().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }

    @Test
    fun `Given enter manual address when setting url and clicking connect then show ConnectScreen then back goes to ManualServer`() {
        testNavigation {
            navController.navigateToServerDiscovery()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.manual_setup)).performScrollTo().assertIsDisplayed().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ManualServerRoute>() == true)

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io/installation/") }

            onNodeWithText("http://homeassistant.local:8123").performTextInput("http://ha.local")

            onNodeWithText(stringResource(commonR.string.connect)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true)
            onNodeWithTag(CONNECTION_SCREEN_TAG).assertIsDisplayed()

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ManualServerRoute>() == true)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Given a server discovered when clicking on it then show ConnectScreen then back goes to ServerDiscovery`() {
        val instanceUrl = "http://ha.local"
        testNavigation {
            navController.navigateToServerDiscovery()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.manual_setup)).performScrollTo().assertIsDisplayed()

            instanceChannel.trySend(HomeAssistantInstance("Test", URL(instanceUrl), HomeAssistantVersion(2025, 9, 1)))

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io/installation/") }

            // Wait for the screen to update based on the instance given in instanceChannel
            waitUntilAtLeastOneExists(hasText(instanceUrl), timeoutMillis = DELAY_BEFORE_DISPLAY_DISCOVERY.inWholeMilliseconds)

            onNodeWithTag(ONE_SERVER_FOUND_MODAL_TAG).performTouchInput {
                swipeUp(startY = bottom * 0.9f, endY = centerY, durationMillis = 200)
            }

            waitForIdle()

            onNodeWithText(instanceUrl).assertIsDisplayed()

            onNodeWithText(stringResource(commonR.string.server_discovery_connect)).assertIsDisplayed().performClick()

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
            onNodeWithText(stringResource(commonR.string.manual_setup)).performScrollTo().assertIsDisplayed()

            instanceChannel.trySend(HomeAssistantInstance("Test", URL(instanceUrl), HomeAssistantVersion(2025, 9, 1)))

            // Wait for the screen to update based on the instance given in instanceChannel
            waitUntilAtLeastOneExists(hasText(instanceUrl), timeoutMillis = DELAY_BEFORE_DISPLAY_DISCOVERY.inWholeMilliseconds)

            onNodeWithText(instanceUrl).assertIsDisplayed()

            onNodeWithText(stringResource(commonR.string.server_discovery_connect)).performClick()

            onNodeWithTag(CONNECTION_SCREEN_TAG).assertIsDisplayed()

            assertTrue(connectionNavigationEventFlow.subscriptionCount.value == 1)
            connectionNavigationEventFlow.emit(ConnectionNavigationEvent.Authenticated(instanceUrl, "super_code", false))

            waitUntilAtLeastOneExists(hasText(stringResource(commonR.string.name_your_device_title)))
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)
            val route = navController.currentBackStackEntry?.toRoute<NameYourDeviceRoute>()
            assertEquals(instanceUrl, route?.url)
            assertEquals("super_code", route?.authCode)

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
        }
    }

    @Test
    fun `Given device named and skip welcome with url when pressing next then show LocalFirst then goes back stop the app`() {
        localFirstTest(true, "http://ha.local")
    }

    @Test
    fun `Given device named and skip welcome without url when pressing next then show LocalFirst then goes back stop the app`() {
        localFirstTest(true, null)
    }

    @Test
    fun `Given device named when pressing next then show LocalFirst then goes back stop the app`() {
        localFirstTest(false, null)
    }

    private fun localFirstTest(skipWelcome: Boolean, urlToOnboard: String?) {
        testNavigation(skipWelcome = skipWelcome, urlToOnboard = urlToOnboard) {
            navController.navigateToNameYourDevice("http://dummy.local", "code")
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)

            onNodeWithText(stringResource(commonR.string.name_your_device_save)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            // The back stack is unchanged in this situation, but in reality the app is in background
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)
        }
    }

    @Test
    fun `Given LocalFirst when pressing next then show LocationSharing then goes back stop the app`() {
        testNavigation {
            navController.navigateToLocalFirst(42, true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)
            onNodeWithText(stringResource(commonR.string.local_first_next)).performScrollTo().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            // In the test scenario since we never opened NameYourDevice the stack still contains Welcome
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given device named with public HTTPS url when pressing next then show LocationSharing`() {
        testDeviceNamedWithPublicUrl(hasPlainTextAccess = true)
    }

    @Test
    fun `Given device named with public HTTP url when pressing next then show LocationSharing`() {
        testDeviceNamedWithPublicUrl(hasPlainTextAccess = false)
    }

    private fun testDeviceNamedWithPublicUrl(hasPlainTextAccess: Boolean) {
        every { nameYourDeviceViewModel.onSaveClick() } coAnswers {
            nameYourDeviceNavigationFlow.emit(
                NameYourDeviceNavigationEvent.DeviceNameSaved(
                    serverId = 42,
                    hasPlainTextAccess = hasPlainTextAccess,
                    isPubliclyAccessible = true,
                ),
            )
        }
        testNavigation {
            navController.navigateToNameYourDevice("http://homeassistant.local", "code")
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)

            onNodeWithText(stringResource(commonR.string.name_your_device_save)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)
        }
    }

    @Test
    fun `Given LocationSharing when agreeing with plain text access to share then show SetHomeNetwork`() {
        testNavigation {
            navController.navigateToLocationSharing(42, true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)
            mockCheckPermission(true)

            onNodeWithText(stringResource(commonR.string.location_sharing_share)).performScrollTo().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<SetHomeNetworkRoute>() == true)
        }
    }

    @Test
    fun `Given LocationSharing when agreeing without plain text access to share then onboarding is done`() {
        testNavigation {
            navController.navigateToLocationSharing(42, false)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            mockCheckPermission(true)
            onNodeWithText(stringResource(commonR.string.location_sharing_share)).performScrollTo().performClick()

            assertTrue(onboardingDone)
        }
    }

    @Test
    fun `Given LocationSharing when denying to share with plain text access then goes to LocationForSecureConnection then goes back stop the app`() {
        testNavigation {
            navController.navigateToLocationSharing(42, true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            mockCheckPermission(false)

            onNodeWithText(stringResource(commonR.string.location_sharing_no_share)).performScrollTo().performClick()
            assertFalse(onboardingDone)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true)

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            // In the test scenario since we never opened NameYourDevice the stack still contains Welcome
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WelcomeRoute>() == true)
        }
    }

    @Test
    fun `Given LocationSharing when denying to share without plain text access then onboarding is done`() {
        testNavigation {
            navController.navigateToLocationSharing(42, false)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)

            mockCheckPermission(false)
            onNodeWithText(stringResource(commonR.string.location_sharing_no_share)).performScrollTo().performClick()
            assertTrue(onboardingDone)
        }
    }

    @Test
    fun `Given LocationForSecureConnection when agreeing to share then show SetHomeNetwork`() {
        testNavigation {
            navController.navigateToLocationForSecureConnection(42)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true)

            onNodeWithText(stringResource(commonR.string.connection_security_most_secure)).performScrollTo().performClick()
            onNodeWithText(stringResource(commonR.string.location_secure_connection_next)).performScrollTo().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<SetHomeNetworkRoute>() == true)
        }
    }

    @Test
    fun `Given SetHomeNetwork when going back then stop the app`() {
        testNavigation {
            navController.navigateToSetHomeNetworkRoute(42)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<SetHomeNetworkRoute>() == true)

            onNodeWithText(stringResource(commonR.string.set_home_network_next)).performScrollTo().performClick()
            assertTrue(onboardingDone)
        }
    }

    @Test
    fun `Given LocationForSecureConnection when choosing less secure option then onboarding completes`() {
        testNavigation {
            navController.navigateToLocationForSecureConnection(42)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true)

            onNodeWithText(stringResource(commonR.string.connection_security_less_secure)).performScrollTo().performClick()
            onNodeWithText(stringResource(commonR.string.location_secure_connection_next)).performScrollTo().performClick()

            assertTrue(onboardingDone)
        }
    }

    @Test
    fun `Given no location tracking with HTTPS public server when device named then onboarding completes`() {
        every { nameYourDeviceViewModel.onSaveClick() } coAnswers {
            nameYourDeviceNavigationFlow.emit(
                NameYourDeviceNavigationEvent.DeviceNameSaved(
                    serverId = 42,
                    hasPlainTextAccess = false,
                    isPubliclyAccessible = true,
                ),
            )
        }
        testNavigation(hasLocationTracking = false) {
            navController.navigateToNameYourDevice("https://www.home-assistant.io", "code")
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)

            onNodeWithText(stringResource(commonR.string.name_your_device_save)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            assertTrue(onboardingDone)
        }
    }

    @Test
    fun `Given no location tracking with HTTP public server and no location permission when device named then show LocationForSecureConnection`() {
        every { nameYourDeviceViewModel.onSaveClick() } coAnswers {
            nameYourDeviceNavigationFlow.emit(
                NameYourDeviceNavigationEvent.DeviceNameSaved(
                    serverId = 42,
                    hasPlainTextAccess = true,
                    isPubliclyAccessible = true,
                ),
            )
        }
        testNavigation(hasLocationTracking = false) {
            mockCheckPermission(false)
            navController.navigateToNameYourDevice("http://homeassistant.local", "code")
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)

            onNodeWithText(stringResource(commonR.string.name_your_device_save)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true)
        }
    }

    @Test
    fun `Given no location tracking with HTTP public server and has location permission when device named then show SetHomeNetwork`() {
        every { nameYourDeviceViewModel.onSaveClick() } coAnswers {
            nameYourDeviceNavigationFlow.emit(
                NameYourDeviceNavigationEvent.DeviceNameSaved(
                    serverId = 42,
                    hasPlainTextAccess = true,
                    isPubliclyAccessible = true,
                ),
            )
        }
        testNavigation(hasLocationTracking = false) {
            mockCheckPermission(true)
            navController.navigateToNameYourDevice("http://homeassistant.local", "code")
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)

            onNodeWithText(stringResource(commonR.string.name_your_device_save)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<SetHomeNetworkRoute>() == true)
        }
    }

    @Test
    fun `Given no location tracking from LocalFirst with HTTP and no permission when next clicked then show LocationForSecureConnection`() {
        testNavigation(hasLocationTracking = false) {
            mockCheckPermission(false)
            navController.navigateToLocalFirst(serverId = 42, hasPlainTextAccess = true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)

            onNodeWithText(stringResource(commonR.string.local_first_next)).performScrollTo().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true)
        }
    }

    @Test
    fun `Given no location tracking from LocalFirst with HTTP and has permission when next clicked then show SetHomeNetwork`() {
        testNavigation(hasLocationTracking = false) {
            mockCheckPermission(true)
            navController.navigateToLocalFirst(serverId = 42, hasPlainTextAccess = true)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)

            onNodeWithText(stringResource(commonR.string.local_first_next)).performScrollTo().performClick()

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<SetHomeNetworkRoute>() == true)
        }
    }

    @Test
    fun `Given no location tracking from LocalFirst with HTTPS when next clicked then onboarding completes`() {
        testNavigation(hasLocationTracking = false) {
            navController.navigateToLocalFirst(serverId = 42, hasPlainTextAccess = false)
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)

            onNodeWithText(stringResource(commonR.string.local_first_next)).performScrollTo().performClick()

            assertTrue(onboardingDone)
        }
    }
    // TODO maybe split this file into multiples one dedicated to each screen
}

private fun mockCheckPermission(grant: Boolean) {
    mockkStatic(ContextCompat::class)
    every { ContextCompat.checkSelfPermission(any(), any()) } returns if (grant) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
}
