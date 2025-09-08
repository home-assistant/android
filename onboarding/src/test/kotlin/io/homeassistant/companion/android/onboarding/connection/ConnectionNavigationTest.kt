package io.homeassistant.companion.android.onboarding.connection

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.HiltComponentActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.connection.navigation.HandleConnectionNavigationEvents
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ConnectionNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @get:Rule(order = 2)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    @Before
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    @Test
    fun `Given HandleConnectionNavigationEvents when viewModel emits Authenticated then invoke onAuthenticated`() = runTest {
        val sharedFlow = MutableSharedFlow<ConnectionNavigationEvent>()

        val viewModel = mockk<ConnectionViewModel> {
            every { navigationEventsFlow } returns sharedFlow
        }

        var onAuthenticated = false

        composeTestRule.setContent {
            HandleConnectionNavigationEvents(
                viewModel,
                onAuthenticated = {
                    onAuthenticated = true
                },
                onShowSnackbar = { _, _ ->
                    true
                },
                onBackPressed = {
                },
            )
        }

        sharedFlow.emit(ConnectionNavigationEvent.Authenticated("code"))

        assertTrue(onAuthenticated)
    }

    @Test
    fun `Given HandleConnectionNavigationEvents when viewModel emits Error then invoke onShowSnackbar and onBackPressed`() = runTest {
        val sharedFlow = MutableSharedFlow<ConnectionNavigationEvent>()

        val viewModel = mockk<ConnectionViewModel> {
            every { navigationEventsFlow } returns sharedFlow
        }

        var errorMessage: String? = null
        var backPressed = false

        composeTestRule.setContent {
            HandleConnectionNavigationEvents(
                viewModel,
                onAuthenticated = {},
                onShowSnackbar = { message, _ ->
                    errorMessage = message
                    true
                },
                onBackPressed = {
                    backPressed = true
                },
            )
        }

        sharedFlow.emit(ConnectionNavigationEvent.Error(commonR.string.error_http_generic, 404, ""))

        assertEquals(
            """There was an error loading Home Assistant. Please review the connection settings and try again.

Error Code: 404 
Description: """,
            errorMessage,
        )
        assertTrue(backPressed)
    }
}
