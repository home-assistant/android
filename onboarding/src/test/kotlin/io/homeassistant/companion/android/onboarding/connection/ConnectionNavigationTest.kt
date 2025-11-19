package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.onboarding.connection.navigation.HandleConnectionNavigationEvents
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ConnectionNavigationTest {
    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @get:Rule(order = 3)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    @Test
    fun `Given HandleConnectionNavigationEvents when viewModel emits Authenticated then invoke onAuthenticated`() = runTest {
        val sharedFlow = MutableSharedFlow<ConnectionNavigationEvent>()

        val viewModel = mockk<ConnectionViewModel> {
            every { navigationEventsFlow } returns sharedFlow
        }

        var onAuthenticatedUrl: String? = null
        var onAuthenticatedCode: String? = null
        var onRequiredMTLS: Boolean? = null

        composeTestRule.setContent {
            HandleConnectionNavigationEvents(
                viewModel,
                onAuthenticated = { url, authCode, requiredMTLS ->
                    onAuthenticatedUrl = url
                    onAuthenticatedCode = authCode
                    onRequiredMTLS = requiredMTLS
                },
                onOpenExternalLink = {},
            )
        }

        val event = ConnectionNavigationEvent.Authenticated("url", "code", true)
            .apply {
                sharedFlow.emit(this)
            }

        assertEquals(event.url, onAuthenticatedUrl)
        assertEquals(event.authCode, onAuthenticatedCode)
        assertEquals(event.requiredMTLS, onRequiredMTLS)
    }

    @Test
    fun `Given HandleConnectionNavigationEvents when viewModel emits OpenExternalLink then invoke onOpenExternalLink`() = runTest {
        val sharedFlow = MutableSharedFlow<ConnectionNavigationEvent>()

        val viewModel = mockk<ConnectionViewModel> {
            every { navigationEventsFlow } returns sharedFlow
        }
        val uri = mockk<Uri>()

        var onOpenExternalLink: Uri? = null

        composeTestRule.setContent {
            HandleConnectionNavigationEvents(
                viewModel,
                onAuthenticated = { _, _, _ ->
                },
                onOpenExternalLink = {
                    onOpenExternalLink = it
                },
            )
        }

        val event = ConnectionNavigationEvent.OpenExternalLink(uri)
            .apply {
                sharedFlow.emit(this)
            }

        assertEquals(event.url, onOpenExternalLink)
    }
}
