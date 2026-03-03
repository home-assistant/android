package io.homeassistant.companion.android.frontend

import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckRepository
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckResult
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.handler.FrontendHandlerEvent
import io.homeassistant.companion.android.frontend.handler.FrontendMessageHandler
import io.homeassistant.companion.android.frontend.navigation.FrontendNavigationEvent
import io.homeassistant.companion.android.frontend.url.FrontendUrlManager
import io.homeassistant.companion.android.frontend.url.UrlLoadResult
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.util.HAWebViewClient
import io.homeassistant.companion.android.util.HAWebViewClientFactory
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class FrontendViewModelTest {
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension(UnconfinedTestDispatcher())

    private val webViewClient: HAWebViewClient = mockk(relaxed = true)
    private val webViewClientFactory: HAWebViewClientFactory = mockk(relaxed = true) {
        every { create(any(), any(), any(), any(), any(), any()) } returns webViewClient
    }
    private val externalBusHandler: FrontendMessageHandler = mockk(relaxed = true)
    private val urlManager: FrontendUrlManager = mockk(relaxed = true)
    private val connectivityCheckRepository: ConnectivityCheckRepository = mockk(relaxed = true)

    private val serverId = 1
    private val testUrlWithAuth = "https://example.com?external_auth=1"

    @BeforeEach
    fun setUp() {
        every { externalBusHandler.messageResults() } returns emptyFlow()
        every { externalBusHandler.scriptsToEvaluate() } returns emptyFlow()
        every { connectivityCheckRepository.runChecks(any()) } returns flowOf(ConnectivityCheckState())
    }

    private fun createViewModel(
        serverId: Int = this.serverId,
        path: String? = null,
    ): FrontendViewModel {
        return FrontendViewModel(
            initialServerId = serverId,
            initialPath = path,
            webViewClientFactory = webViewClientFactory,
            frontendMessageHandler = externalBusHandler,
            urlManager = urlManager,
            connectivityCheckRepository = connectivityCheckRepository,
        )
    }

    @Nested
    inner class UrlLoading {

        @Test
        fun `Given url manager returns success when initialized then state is Loading with url`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Loading)
            assertEquals(serverId, state.serverId)
            assertEquals(testUrlWithAuth, state.url)
        }

        @Test
        fun `Given url manager returns session not connected when initialized then error state`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.SessionNotConnected(serverId),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Error, "Expected Error state but got $state")
            assertTrue((state as FrontendViewState.Error).error is FrontendConnectionError.AuthenticationError)
        }

        @Test
        fun `Given url manager returns server not found when initialized then error state`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.ServerNotFound(serverId),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Error)
            assertTrue((state as FrontendViewState.Error).error is FrontendConnectionError.UnreachableError)
        }

        @Test
        fun `Given url manager returns success with path when initialized then loading state includes path`() = runTest {
            val urlWithPath = "https://example.com/dashboard?external_auth=1"
            every { urlManager.serverUrlFlow(serverId, "/dashboard") } returns flowOf(
                UrlLoadResult.Success(url = urlWithPath, serverId = serverId),
            )

            val viewModel = createViewModel(path = "/dashboard")
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Loading)
            assertEquals(urlWithPath, state.url)
        }

        @Test
        fun `Given url manager returns insecure blocked when initialized then insecure state`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.InsecureBlocked(
                    serverId = serverId,
                    missingHomeSetup = false,
                    missingLocation = false,
                ),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Insecure)
            assertEquals(serverId, state.serverId)
        }

        @Test
        fun `Given url manager returns no url available when collecting then error state`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.NoUrlAvailable(serverId),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Error)
            assertTrue((state as FrontendViewState.Error).error is FrontendConnectionError.UnreachableError)
        }
    }

    @Nested
    inner class StateTransitions {

        @Test
        fun `Given url state changes from success to insecure when collecting then insecure state`() = runTest {
            val urlFlow = MutableSharedFlow<UrlLoadResult>(replay = 1)
            every { urlManager.serverUrlFlow(any(), any()) } returns urlFlow

            // Emit initial success before creating ViewModel so it's available when collection starts
            urlFlow.emit(UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId))

            val viewModel = createViewModel()
            // Advance time but not past the timeout
            advanceTimeBy(CONNECTION_TIMEOUT - 2.seconds)

            // Verify initial loading state
            val initialState = viewModel.viewState.value
            assertTrue(
                initialState is FrontendViewState.Loading,
                "Expected Loading but got $initialState",
            )

            // Emit insecure state - this simulates switching from internal to external network
            urlFlow.emit(
                UrlLoadResult.InsecureBlocked(
                    serverId = serverId,
                    missingHomeSetup = false,
                    missingLocation = false,
                ),
            )
            // Advance a bit more but still not past the original timeout
            advanceTimeBy(1.seconds)

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Insecure, "Expected Insecure but got $state")
        }

        @Test
        fun `Given error state when onRetry called then state transitions to LoadServer then Loading`() = runTest {
            val urlFlow = MutableSharedFlow<UrlLoadResult>()
            every { urlManager.serverUrlFlow(any(), any()) } returns urlFlow

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Initial state is LoadServer while waiting for URL
            assertTrue(
                viewModel.viewState.value is FrontendViewState.LoadServer,
                "Expected LoadServer but got ${viewModel.viewState.value}",
            )

            // Emit error result
            urlFlow.emit(UrlLoadResult.NoUrlAvailable(serverId))
            advanceUntilIdle()

            // Verify error state
            assertTrue(viewModel.viewState.value is FrontendViewState.Error)

            // Retry - this should first go to LoadServer
            viewModel.onRetry()
            advanceUntilIdle()

            // After retry, state should be LoadServer again while waiting for URL
            assertTrue(
                viewModel.viewState.value is FrontendViewState.LoadServer,
                "Expected LoadServer after retry but got ${viewModel.viewState.value}",
            )

            // Now emit success
            urlFlow.emit(UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId))
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            val loadingState = viewModel.viewState.value
            assertTrue(loadingState is FrontendViewState.Loading)
        }

        @Test
        fun `Given multiple servers when switchServer called then state updates to new server`() = runTest {
            val urlFlow1 = flowOf(UrlLoadResult.Success(url = "https://server1.com?external_auth=1", serverId = 1))
            val urlFlow2 = flowOf(UrlLoadResult.Success(url = "https://server2.com?external_auth=1", serverId = 2))

            every { urlManager.serverUrlFlow(1, any()) } returns urlFlow1
            every { urlManager.serverUrlFlow(2, any()) } returns urlFlow2

            val viewModel = createViewModel(serverId = 1)
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Verify initial server
            assertEquals(1, viewModel.viewState.value.serverId)
            assertTrue(viewModel.viewState.value.url.contains("server1.com"))

            // Switch to server 2
            viewModel.switchServer(2)
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            assertEquals(2, viewModel.viewState.value.serverId)
            assertTrue(viewModel.viewState.value.url.contains("server2.com"))
        }
    }

    @Nested
    inner class ErrorFlow {

        @Test
        fun `Given loading state when error occurs then error flow is updated`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.ServerNotFound(serverId),
            )

            val viewModel = createViewModel()

            // Subscribe to errorFlow to activate the stateIn with WhileSubscribed
            val errors = mutableListOf<FrontendConnectionError?>()
            val job = backgroundScope.launch { viewModel.errorFlow.collect { errors.add(it) } }

            advanceUntilIdle()

            assertTrue(errors.any { it is FrontendConnectionError.UnreachableError })
            job.cancel()
        }

        @Test
        fun `Given error state when onRetry called then error flow is cleared if no error`() = runTest {
            val urlResults = MutableStateFlow<UrlLoadResult>(UrlLoadResult.ServerNotFound(serverId))
            every { urlManager.serverUrlFlow(any(), any()) } returns urlResults

            val viewModel = createViewModel()

            // Subscribe to errorFlow to activate the stateIn with WhileSubscribed
            val errors = mutableListOf<FrontendConnectionError?>()
            val job = backgroundScope.launch { viewModel.errorFlow.collect { errors.add(it) } }

            advanceUntilIdle()

            // Verify error exists
            assertTrue(errors.any { it != null })

            // Setup successful response for retry
            urlResults.value = UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId)

            viewModel.onRetry()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Error should be cleared - the last emitted error should be null
            assertTrue(errors.last() == null)
            job.cancel()
        }
    }

    @Nested
    inner class MessageHandling {

        @Test
        fun `Given connected message result when collected then state transitions to Content`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { externalBusHandler.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Verify initial loading state
            assertTrue(viewModel.viewState.value is FrontendViewState.Loading)

            // Emit connected message
            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Content)
            assertEquals(serverId, state.serverId)
        }

        @Test
        fun `Given auth error message result when collected then state transitions to Error`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { externalBusHandler.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Verify initial loading state
            assertTrue(viewModel.viewState.value is FrontendViewState.Loading)

            // Emit auth error message
            val authError = FrontendConnectionError.AuthenticationError(
                message = io.homeassistant.companion.android.common.R.string.error_connection_failed,
                errorDetails = "Token expired",
                rawErrorType = "AuthError",
            )
            messageFlow.emit(FrontendHandlerEvent.AuthError(authError))
            advanceUntilIdle()

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Error)
            assertEquals(authError, (state as FrontendViewState.Error).error)
        }

        @Test
        fun `Given show assist message result when collected then NavigateToAssist event is emitted`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { externalBusHandler.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            // Collect navigation events
            val navigationEvents = mutableListOf<FrontendNavigationEvent>()
            val job = backgroundScope.launch { viewModel.navigationEvents.collect { navigationEvents.add(it) } }

            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Emit show assist message
            messageFlow.emit(FrontendHandlerEvent.ShowAssist(pipelineId = "abc", startListening = false))
            advanceUntilIdle()

            val event = navigationEvents.filterIsInstance<FrontendNavigationEvent.NavigateToAssist>().firstOrNull()
            assertTrue(event != null, "Expected NavigateToAssist event")
            assertEquals(serverId, event!!.serverId)
            assertEquals("abc", event.pipelineId)
            assertEquals(false, event.startListening)
            job.cancel()
        }

        @Test
        fun `Given open settings message result when collected then navigation event is emitted`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { externalBusHandler.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            // Collect navigation events
            val navigationEvents = mutableListOf<FrontendNavigationEvent>()
            val job = backgroundScope.launch { viewModel.navigationEvents.collect { navigationEvents.add(it) } }

            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Emit open settings message
            messageFlow.emit(FrontendHandlerEvent.OpenSettings)
            advanceUntilIdle()

            assertTrue(navigationEvents.any { it is FrontendNavigationEvent.NavigateToSettings })
            job.cancel()
        }
    }

    @Nested
    inner class SecurityLevel {

        @Test
        fun `Given security level required when url result received then show security level state`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.SecurityLevelRequired(serverId),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.SecurityLevelRequired)
            assertEquals(serverId, state.serverId)
        }

        @Test
        fun `Given security level configured when called then url manager is notified and server reloads`() = runTest {
            val urlResults = MutableStateFlow<UrlLoadResult>(UrlLoadResult.SecurityLevelRequired(serverId))
            every { urlManager.serverUrlFlow(any(), any()) } returns urlResults
            every { urlManager.onSecurityLevelShown(any()) } just runs

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Verify security level required state
            assertTrue(viewModel.viewState.value is FrontendViewState.SecurityLevelRequired)

            // Configure security level
            urlResults.value = UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId)
            viewModel.onSecurityLevelDone()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            verify { urlManager.onSecurityLevelShown(serverId) }
            assertTrue(viewModel.viewState.value is FrontendViewState.Loading)
        }

        @Test
        fun `Given insecure state when onShowSecurityLevelScreen called then state transitions to SecurityLevelRequired`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.InsecureBlocked(
                    serverId = serverId,
                    missingHomeSetup = true,
                    missingLocation = false,
                ),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Verify insecure state
            assertTrue(viewModel.viewState.value is FrontendViewState.Insecure)

            // Call onShowSecurityLevelScreen
            viewModel.onShowSecurityLevelScreen()
            advanceUntilIdle()

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.SecurityLevelRequired)
            assertEquals(serverId, state.serverId)
        }
    }

    @Nested
    inner class ConnectivityChecks {

        @Test
        fun `Given connectivity checks requested when error occurs then repository is called`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.ServerNotFound(serverId),
            )

            createViewModel()
            advanceUntilIdle()

            verify { connectivityCheckRepository.runChecks(any()) }
        }

        @Test
        fun `Given connectivity checks running when repository emits state then viewModel updates state`() = runTest {
            val inProgressState = ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.InProgress,
            )
            val completedState = ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.Success(
                    messageResId = io.homeassistant.companion.android.common.R.string.connection_check_dns,
                    details = "192.168.1.1",
                ),
                portReachability = ConnectivityCheckResult.Success(
                    messageResId = io.homeassistant.companion.android.common.R.string.connection_check_port,
                    details = "8123",
                ),
                tlsCertificate = ConnectivityCheckResult.NotApplicable(
                    messageResId = io.homeassistant.companion.android.common.R.string.connection_check_tls_not_applicable,
                ),
                serverConnection = ConnectivityCheckResult.Success(
                    messageResId = io.homeassistant.companion.android.common.R.string.connection_check_server_success,
                ),
                homeAssistantVerification = ConnectivityCheckResult.Success(
                    messageResId = io.homeassistant.companion.android.common.R.string.connection_check_home_assistant_success,
                ),
            )
            every { connectivityCheckRepository.runChecks(any()) } returns flowOf(inProgressState, completedState)
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.ServerNotFound(serverId),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Verify connectivity check state is updated to completed state
            assertEquals(completedState, viewModel.connectivityCheckState.value)
        }

        @Test
        fun `Given connectivity checks with failure when repository emits state then viewModel has failure state`() = runTest {
            val failedState = ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.Failure(
                    messageResId = io.homeassistant.companion.android.common.R.string.connection_check_error_dns,
                ),
                portReachability = ConnectivityCheckResult.Failure(
                    messageResId = io.homeassistant.companion.android.common.R.string.connection_check_skipped,
                ),
                tlsCertificate = ConnectivityCheckResult.Failure(
                    messageResId = io.homeassistant.companion.android.common.R.string.connection_check_skipped,
                ),
                serverConnection = ConnectivityCheckResult.Failure(
                    messageResId = io.homeassistant.companion.android.common.R.string.connection_check_skipped,
                ),
                homeAssistantVerification = ConnectivityCheckResult.Failure(
                    messageResId = io.homeassistant.companion.android.common.R.string.connection_check_skipped,
                ),
            )
            every { connectivityCheckRepository.runChecks(any()) } returns flowOf(failedState)
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.ServerNotFound(serverId),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Verify connectivity check state has failure
            assertEquals(failedState, viewModel.connectivityCheckState.value)
            assertTrue(viewModel.connectivityCheckState.value.hasFailure)
        }
    }

    @Nested
    inner class ConnectionTimeout {

        @Test
        fun `Given loading state when connection times out then error state with timeout error`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            // Verify loading state before timeout
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            assertTrue(viewModel.viewState.value is FrontendViewState.Loading)

            // Advance past timeout
            advanceTimeBy(2.seconds)

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Error, "Expected Error state but got $state")
            assertTrue((state as FrontendViewState.Error).error is FrontendConnectionError.UnreachableError)
        }

        @Test
        fun `Given loading state when connected before timeout then no timeout error`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { externalBusHandler.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 5.seconds)

            // Verify loading state
            assertTrue(viewModel.viewState.value is FrontendViewState.Loading)

            // Connect before timeout
            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()

            // Verify content state
            assertTrue(viewModel.viewState.value is FrontendViewState.Content)

            // Advance past when timeout would have fired
            advanceTimeBy(10.seconds)

            // Should still be content state, not error
            assertTrue(viewModel.viewState.value is FrontendViewState.Content)
        }
    }
}
