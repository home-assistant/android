package io.homeassistant.companion.android.onboarding.nameyourdevice.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavDestination.Companion.hasRoute
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.BaseOnboardingNavigationTest
import io.homeassistant.companion.android.onboarding.localfirst.navigation.LocalFirstRoute
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.LocationForSecureConnectionRoute
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.LocationSharingRoute
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceNavigationEvent
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceViewModel
import io.homeassistant.companion.android.testing.unit.stringResource
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Navigation tests for the Name Your Device screen in the onboarding flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
internal class NameYourDeviceNavigationTest : BaseOnboardingNavigationTest() {
    val nameYourDeviceNavigationFlow = MutableSharedFlow<NameYourDeviceNavigationEvent>()

    @BindValue
    @JvmField
    val nameYourDeviceViewModel: NameYourDeviceViewModel = mockk(relaxed = true) {
        every { navigationEventsFlow } returns nameYourDeviceNavigationFlow
        every { onSaveClick() } coAnswers {
            nameYourDeviceNavigationFlow.emit(
                NameYourDeviceNavigationEvent.DeviceNameSaved(
                    serverId = 42,
                    hasPlainTextAccess = false,
                    isPubliclyAccessible = false,
                ),
            )
        }
        every { deviceNameFlow } returns MutableStateFlow("Test")
        every { isValidNameFlow } returns MutableStateFlow(true)
        every { isSaveClickableFlow } returns MutableStateFlow(true)
        every { isSavingFlow } returns MutableStateFlow(false)
    }

    @Test
    fun `Given device named and skip welcome with url when pressing next then show LocalFirst then goes back stop the app`() {
        localFirstTest(skipWelcome = true, urlToOnboard = "http://ha.local")
    }

    @Test
    fun `Given device named and skip welcome without url when pressing next then show LocalFirst then goes back stop the app`() {
        localFirstTest(skipWelcome = true, urlToOnboard = null)
    }

    @Test
    fun `Given device named when pressing next then show LocalFirst then goes back stop the app`() {
        localFirstTest(skipWelcome = false, urlToOnboard = null)
    }

    private fun localFirstTest(skipWelcome: Boolean, urlToOnboard: String?) {
        testNavigation(skipWelcome = skipWelcome, urlToOnboard = urlToOnboard) {
            navController.navigateToNameYourDevice("http://dummy.local", "code")
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)

            onNodeWithText(stringResource(commonR.string.name_your_device_save))
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)

            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()

            // The back stack is unchanged in this situation, but in reality the app is in background
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocalFirstRoute>() == true)
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

            onNodeWithText(stringResource(commonR.string.name_your_device_save))
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .performClick()
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<LocationSharingRoute>() == true)
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

            onNodeWithText(stringResource(commonR.string.name_your_device_save))
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .performClick()

            assertTrue(onboardingDone)
        }
    }

    @Test
    fun `Given no location tracking with HTTP public server when device named then show LocationForSecureConnection`() {
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
            navController.navigateToNameYourDevice("http://homeassistant.local", "code")
            assertTrue(navController.currentBackStackEntry?.destination?.hasRoute<NameYourDeviceRoute>() == true)

            onNodeWithText(stringResource(commonR.string.name_your_device_save))
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
                .performClick()

            assertTrue(
                navController.currentBackStackEntry?.destination?.hasRoute<LocationForSecureConnectionRoute>() == true,
            )
        }
    }
}
