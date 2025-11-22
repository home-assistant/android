package io.homeassistant.companion.android.onboarding.nameyourdevice

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.HandleNameYourDeviceNavigationEvents
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class NameYourDeviceNavigationTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @get:Rule(order = 3)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    @Test
    fun `Given HandleNameYourDeviceNavigationEvents when viewModel emits DeviceNameSaved then invoke onDeviceNamed with serverId`() = runTest {
        val sharedFlow = MutableSharedFlow<NameYourDeviceNavigationEvent>()
        val viewModel = mockk<NameYourDeviceViewModel> {
            every { navigationEventsFlow } returns sharedFlow
        }

        var serverIdSet: Int? = null
        var hasPlainTextAccessSet: Boolean? = null
        var isPubliclyAccessibleSet: Boolean? = null

        composeTestRule.setContent {
            HandleNameYourDeviceNavigationEvents(
                viewModel,
                onDeviceNamed = { serverId, hasPlainTextAccess, isPubliclyAccessible ->
                    serverIdSet = serverId
                    hasPlainTextAccessSet = hasPlainTextAccess
                    isPubliclyAccessibleSet = isPubliclyAccessible
                },
                onShowSnackbar = { message, _ ->
                    true
                },
                onBackClick = {},
            )
        }

        val event = NameYourDeviceNavigationEvent.DeviceNameSaved(42, hasPlainTextAccess = true, isPubliclyAccessible = true).apply {
            sharedFlow.emit(this)
        }

        assertEquals(event.serverId, serverIdSet)
        assertEquals(event.hasPlainTextAccess, hasPlainTextAccessSet)
        assertEquals(event.isPubliclyAccessible, isPubliclyAccessibleSet)
    }

    @Test
    fun `Given HandleNameYourDeviceNavigationEvents when viewModel emits Error then invoke onShowSnackbar and onBackClick`() = runTest {
        val sharedFlow = MutableSharedFlow<NameYourDeviceNavigationEvent>()
        val viewModel = mockk<NameYourDeviceViewModel> {
            every { navigationEventsFlow } returns sharedFlow
        }

        var errorMessage: String? = null
        var backPressed = false

        composeTestRule.setContent {
            HandleNameYourDeviceNavigationEvents(
                viewModel,
                onDeviceNamed = { _, _, _ -> },
                onShowSnackbar = { message, _ ->
                    errorMessage = message
                    true
                },
                onBackClick = {
                    backPressed = true
                },
            )
        }

        sharedFlow.emit(NameYourDeviceNavigationEvent.Error(commonR.string.webview_error))

        assertEquals(
            "There was an error loading Home Assistant, please review the connection settings and try again. We will attempt to try another provided URL when you select Refresh.",
            errorMessage,
        )
        assertTrue(backPressed)
    }
}
