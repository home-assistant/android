package io.homeassistant.companion.android.frontend

import android.net.Uri
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.frontend.CONNECTION_TIMEOUT
import io.homeassistant.companion.android.frontend.error.FrontendError
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.net.URL
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class FrontendViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension(testDispatcher)

    private val serverManager: ServerManager = mockk(relaxed = true)
    private val keyChainRepository: KeyChainRepository = mockk(relaxed = true)
    private val authenticationRepository: AuthenticationRepository = mockk(relaxed = true)
    private val connectionStateProvider: ServerConnectionStateProvider = mockk(relaxed = true)

    private val serverId = 1
    private val testUrl = URL("https://example.com")

    /**
     * Creates a test Server with HTTPS URL (hasPlainTextUrl = false).
     */
    private fun createServer(
        id: Int = serverId,
        externalUrl: String = "https://example.com",
    ) = Server(
        id = id,
        _name = "Test Server",
        connection = ServerConnectionInfo(
            externalUrl = externalUrl,
        ),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        mockUriParse()
        coEvery { serverManager.authenticationRepository(any()) } returns authenticationRepository
        coEvery { serverManager.connectionStateProvider(any()) } returns connectionStateProvider
    }

    private fun mockUriParse() {
        every { Uri.parse(any()) } answers {
            val uriString = firstArg<String>()
            createMockUri(uriString)
        }
    }

    private fun createMockUri(uriString: String): Uri {
        return mockk<Uri> {
            every { this@mockk.toString() } returns uriString
            every { buildUpon() } answers {
                createMockUriBuilder(uriString)
            }
        }
    }

    private fun createMockUriBuilder(baseUri: String): Uri.Builder {
        var currentUri = baseUri
        return mockk<Uri.Builder> {
            every { appendQueryParameter(any(), any()) } answers {
                val key = firstArg<String>()
                val value = secondArg<String>()
                val separator = if (currentUri.contains("?")) "&" else "?"
                currentUri = "$currentUri$separator$key=$value"
                this@mockk
            }
            every { build() } answers {
                createMockUri(currentUri)
            }
        }
    }

    private fun createViewModel(
        serverId: Int = this.serverId,
        path: String? = null,
    ): FrontendViewModel {
        return FrontendViewModel(
            initialServerId = serverId,
            initialPath = path,
            serverManager = serverManager,
            keyChainRepository = keyChainRepository,
        )
    }

    @Test
    fun `Given server exists and session connected when initialized then collects url flow`() = runTest(testDispatcher) {
        val server = createServer()
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(testUrl))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        createViewModel()
        advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

        coVerify { connectionStateProvider.urlFlow(null) }
    }

    @Test
    fun `Given server exists and session connected when initialized then state is Loading with url`() = runTest(testDispatcher) {
        val server = createServer()
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(testUrl))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        val viewModel = createViewModel()
        advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

        val state = viewModel.viewState.value
        assertTrue(state is FrontendViewState.Loading)
        assertEquals(serverId, state.serverId)
        assertTrue(state.url.startsWith("https://example.com"))
    }

    @Test
    fun `Given server exists and session anonymous when initialized then error state`() = runTest(testDispatcher) {
        val server = createServer()

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.ANONYMOUS

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.viewState.value
        assertTrue(state is FrontendViewState.Error, "Expected Error state but got $state")
        assertTrue((state as FrontendViewState.Error).error is FrontendError.AuthenticationError)

        coVerify(exactly = 0) { connectionStateProvider.urlFlow(any()) }
    }

    @Test
    fun `Given server does not exist when initialized then error state`() = runTest(testDispatcher) {
        coEvery { serverManager.getServer(any<Int>()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.viewState.value
        assertTrue(state is FrontendViewState.Error)
        assertTrue((state as FrontendViewState.Error).error is FrontendError.UnreachableError)
    }

    @Test
    fun `Given url state is HasUrl with path when initialized then loading state includes path`() = runTest(testDispatcher) {
        val server = createServer()
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(testUrl))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        val viewModel = createViewModel(path = "/dashboard")
        advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

        val state = viewModel.viewState.value
        assertTrue(state is FrontendViewState.Loading)
        assertTrue(state.url.contains("/dashboard"))
    }

    @Test
    fun `Given url state is InsecureState when initialized then insecure state`() = runTest(testDispatcher) {
        val server = createServer()
        val urlFlow = MutableStateFlow<UrlState>(UrlState.InsecureState)

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.viewState.value
        assertTrue(state is FrontendViewState.Insecure)
        assertEquals(serverId, state.serverId)
    }

    @Test
    fun `Given url state changes from HasUrl to InsecureState when collecting then insecure state`() = runTest(testDispatcher) {
        val server = createServer()
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(testUrl))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        val viewModel = createViewModel()
        advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

        // Verify initial loading state
        assertTrue(viewModel.viewState.value is FrontendViewState.Loading)

        // Emit insecure state
        urlFlow.emit(UrlState.InsecureState)
        advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

        val state = viewModel.viewState.value
        assertTrue(state is FrontendViewState.Insecure)
    }

    @Test
    fun `Given path consumed when url changes then path is not applied again`() = runTest(testDispatcher) {
        val server = createServer()
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(testUrl))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        val viewModel = createViewModel(path = "/dashboard")
        advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

        // First URL should have path
        val firstState = viewModel.viewState.value
        assertTrue(firstState is FrontendViewState.Loading)
        assertTrue(firstState.url.contains("/dashboard"))

        // Emit URL change (e.g., switching from internal to external)
        val externalUrl = URL("https://external.example.com")
        urlFlow.emit(UrlState.HasUrl(externalUrl))
        advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

        // Second URL should NOT have path (already consumed)
        val secondState = viewModel.viewState.value
        assertTrue(secondState is FrontendViewState.Loading)
        assertTrue(secondState.url.contains("external.example.com"))
        assertTrue(!secondState.url.contains("/dashboard"))
    }

    @Test
    fun `Given IllegalArgumentException when getting session state then error state`() = runTest(testDispatcher) {
        val server = createServer()

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } throws IllegalArgumentException("Server not found")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.viewState.value
        assertTrue(state is FrontendViewState.Error)

        coVerify(exactly = 0) { connectionStateProvider.urlFlow(any()) }
    }

    @Test
    fun `Given url state is HasUrl with null url when collecting then error state`() = runTest(testDispatcher) {
        val server = createServer()
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(null))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.viewState.value
        assertTrue(state is FrontendViewState.Error)
        assertTrue((state as FrontendViewState.Error).error is FrontendError.UnreachableError)
    }

    @Test
    fun `Given error state when onRetry called then state transitions to loading`() = runTest(testDispatcher) {
        val server = createServer()
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(null))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Verify error state
        assertTrue(viewModel.viewState.value is FrontendViewState.Error)

        // Fix the URL and retry
        urlFlow.value = UrlState.HasUrl(testUrl)
        viewModel.onRetry()
        advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

        val state = viewModel.viewState.value
        assertTrue(state is FrontendViewState.Loading)
        assertTrue(state.url.startsWith("https://example.com"))
    }

    @Test
    fun `Given multiple servers when switchServer called then state updates to new server`() = runTest(testDispatcher) {
        val server1 = createServer(id = 1, externalUrl = "https://server1.com")
        val server2 = createServer(id = 2, externalUrl = "https://server2.com")

        val urlFlow1 = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("https://server1.com")))
        val urlFlow2 = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("https://server2.com")))

        coEvery { serverManager.getServer(1) } returns server1
        coEvery { serverManager.getServer(2) } returns server2
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returnsMany listOf(urlFlow1, urlFlow2)

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

    @Test
    fun `Given loading state when error occurs then error flow is updated`() = runTest(testDispatcher) {
        coEvery { serverManager.getServer(any<Int>()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        val error = viewModel.errorFlow.value
        assertTrue(error is FrontendError.UnreachableError)
    }

    @Test
    fun `Given error state when onRetry called then error flow is cleared`() = runTest(testDispatcher) {
        val server = createServer()

        coEvery { serverManager.getServer(any<Int>()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Verify error exists
        assertTrue(viewModel.errorFlow.value != null)

        // Setup successful response for retry
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns MutableStateFlow(UrlState.HasUrl(testUrl))

        viewModel.onRetry()
        advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

        // Error should be cleared
        assertNull(viewModel.errorFlow.value)
    }
}
