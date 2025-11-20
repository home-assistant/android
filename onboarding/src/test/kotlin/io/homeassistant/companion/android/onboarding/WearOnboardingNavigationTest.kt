package io.homeassistant.companion.android.onboarding

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import io.homeassistant.companion.android.compose.composable.HA_WEBVIEW_TAG
import io.homeassistant.companion.android.compose.navigateToUri
import io.homeassistant.companion.android.onboarding.connection.CONNECTION_SCREEN_TAG
import io.homeassistant.companion.android.onboarding.connection.ConnectionNavigationEvent
import io.homeassistant.companion.android.onboarding.connection.ConnectionViewModel
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.onboarding.nameyourweardevice.navigation.NameYourWearDeviceRoute
import io.homeassistant.companion.android.onboarding.nameyourweardevice.navigation.navigateToNameYourWearDevice
import io.homeassistant.companion.android.onboarding.serverdiscovery.DELAY_BEFORE_DISPLAY_DISCOVERY
import io.homeassistant.companion.android.onboarding.serverdiscovery.HomeAssistantInstance
import io.homeassistant.companion.android.onboarding.serverdiscovery.HomeAssistantSearcher
import io.homeassistant.companion.android.onboarding.serverdiscovery.ONE_SERVER_FOUND_MODAL_TAG
import io.homeassistant.companion.android.onboarding.serverdiscovery.ServerDiscoveryModule
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryMode
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryRoute
import io.homeassistant.companion.android.onboarding.wearmtls.WearMTLSUiState
import io.homeassistant.companion.android.onboarding.wearmtls.WearMTLSViewModel
import io.homeassistant.companion.android.onboarding.wearmtls.navigation.WearMTLSRoute
import io.homeassistant.companion.android.onboarding.wearmtls.navigation.navigateToWearMTLS
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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val WEAR_NAME = "super_ha_wear"
private const val VALID_PASSWORD = "1234"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(ServerDiscoveryModule::class)
@HiltAndroidTest
internal class WearOnboardingNavigationTest {
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

    private val selectedUri = mockk<Uri>()

    @BindValue
    @JvmField
    val wearMTLSViewModel: WearMTLSViewModel = mockk(relaxed = true) {
        // Bypass the validation of the cert
        every { uiState } returns MutableStateFlow(
            WearMTLSUiState(
                selectedUri = selectedUri,
                selectedFileName = "file",
                currentPassword = VALID_PASSWORD,
                isCertValidated = true,
                showError = false,
            ),
        )
    }

    private val instanceChannel = Channel<HomeAssistantInstance>()

    private lateinit var navController: TestNavHostController

    private var onboardingDone = false
    private var deviceName: String? = null
    private var serverUrl: String? = null
    private var authCode: String? = null
    private var certUri: Uri? = null
    private var certPassword: String? = null

    @Before
    fun setup() {
        mockkStatic(NavController::navigateToUri)
        every { any<NavController>().navigateToUri(any()) } just Runs
    }

    private fun setContent(urlToOnboard: String? = null) {
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
                    startDestination = WearOnboardingRoute(wearName = WEAR_NAME, urlToOnboard = urlToOnboard),
                ) {
                    wearOnboarding(
                        navController,
                        onOnboardingDone = { deviceName, serverUrl, authCode, certUri, certPassword ->
                            onboardingDone = true
                            this@WearOnboardingNavigationTest.deviceName = deviceName
                            this@WearOnboardingNavigationTest.serverUrl = serverUrl
                            this@WearOnboardingNavigationTest.authCode = authCode
                            this@WearOnboardingNavigationTest.certUri = certUri
                            this@WearOnboardingNavigationTest.certPassword = certPassword
                        },
                        urlToOnboard = urlToOnboard,
                        wearNameToOnboard = WEAR_NAME,
                    )
                }
            }
        }
    }

    private fun testNavigation(urlToOnboard: String? = null, testContent: suspend AndroidComposeTestRule<*, *>.() -> Unit) {
        setContent(urlToOnboard)
        runTest {
            composeTestRule.testContent()
        }
    }

    @Test
    fun `Given no server to onboard when starting the navigation then opens ServerDiscoveryScreen in ADD_EXISTING mode`() {
        testNavigation {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            assertTrue(navController.currentBackStackEntry?.toRoute<ServerDiscoveryRoute>()?.discoveryMode == ServerDiscoveryMode.ADD_EXISTING)
            onNodeWithText(stringResource(commonR.string.searching_home_network)).assertIsDisplayed()
        }
    }

    @Test
    fun `Given server onboard when starting the navigation then opens ConnectionScreen`() {
        testNavigation("http://ha") {
            Assertions.assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
        }
    }

    // This test is similar to the classic onboarding but it is just to test the behavior of the shared screen.
    // We are skipping the test of the manual setup since it is the same as the classic onboarding.
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Given a server discovered when clicking on it then show ConnectScreen then back goes to ServerDiscovery`() {
        val instanceUrl = "http://ha.local"
        testNavigation {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ServerDiscoveryRoute>() == true)
            onNodeWithText(stringResource(commonR.string.searching_home_network)).assertIsDisplayed()

            instanceChannel.trySend(HomeAssistantInstance("Test", URL(instanceUrl), HomeAssistantVersion(2025, 9, 1)))
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
    fun `Given authenticating when authenticated then show NameYourWearDevice once named it finishes the onboarding with proper parameters`() {
        val instanceUrl = "http://ha.local"
        val authCode = "super_code"
        testNavigation("http://ha") {
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<ConnectionRoute>() == true)
            onNodeWithTag(HA_WEBVIEW_TAG).assertIsDisplayed()
            assertTrue(connectionNavigationEventFlow.subscriptionCount.value == 1)

            connectionNavigationEventFlow.emit(ConnectionNavigationEvent.Authenticated(instanceUrl, authCode, false))
            waitUntilAtLeastOneExists(hasText(stringResource(commonR.string.name_your_device_title)))

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourWearDeviceRoute>() == true)
            val route = navController.currentBackStackEntry?.toRoute<NameYourWearDeviceRoute>()
            assertEquals(WEAR_NAME, route?.defaultDeviceName)
            assertEquals(instanceUrl, route?.url)
            assertEquals(authCode, route?.authCode)
            assertEquals(false, route?.requiredMTLS)

            // Verify that the default name is picked on the screen
            onNodeWithText(WEAR_NAME).assertIsDisplayed()

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri("https://www.home-assistant.io/installation/") }

            onNodeWithText(stringResource(commonR.string.name_your_device_save)).performScrollTo().assertIsDisplayed().performClick()

            assertTrue(onboardingDone)
            assertEquals(WEAR_NAME, deviceName)
            assertEquals(instanceUrl, serverUrl)
            assertEquals(authCode, authCode)
            assertEquals(null, certUri)
            assertEquals(null, certPassword)
        }
    }

    @Test
    fun `Given NameYourWearDevice with mTLS required when device is named then it opens WearMTLSScreen and then back goes to NameYourDevice`() {
        val instanceUrl = "http://ha.local"
        val authCode = "super_code"
        testNavigation("http://ha") {
            val defaultDeviceName = "wear"
            navController.navigateToNameYourWearDevice(defaultDeviceName, instanceUrl, authCode, true)

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourWearDeviceRoute>() == true)
            val newDeviceName = "new wear"
            onNodeWithText(defaultDeviceName).performScrollTo().assertIsDisplayed().performTextReplacement(newDeviceName)

            onNodeWithText(stringResource(commonR.string.name_your_device_save)).performScrollTo().assertIsDisplayed().performClick()

            assertFalse(onboardingDone)

            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<WearMTLSRoute>() == true)
            onNodeWithText(stringResource(commonR.string.wear_mtls_content)).assertIsDisplayed()

            onNodeWithContentDescription(stringResource(commonR.string.navigate_up)).performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourWearDeviceRoute>() == true)

            onNodeWithText(newDeviceName).performScrollTo().assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `Given valid mTLS when clicking next then finishes the onboarding with proper parameters`() {
        val instanceUrl = "http://ha.local"
        val authCode = "super_code"
        testNavigation("http://ha") {
            navController.navigateToWearMTLS(WEAR_NAME, instanceUrl, authCode)
            onNodeWithText(stringResource(commonR.string.wear_mtls_content)).assertIsDisplayed()

            onNodeWithContentDescription(stringResource(commonR.string.get_help)).performClick()
            verify { any<NavController>().navigateToUri("https://companion.home-assistant.io/docs/getting_started/#tls-client-authentication") }

            onNodeWithText(stringResource(commonR.string.wear_mtls_next)).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            assertTrue(onboardingDone)
            assertEquals(WEAR_NAME, deviceName)
            assertEquals(instanceUrl, serverUrl)
            assertEquals(authCode, authCode)
            assertEquals(selectedUri, certUri)
            assertEquals(VALID_PASSWORD, certPassword)
        }
    }
}
