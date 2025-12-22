package io.homeassistant.companion.android.webview

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.net.http.SslError
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.improv.ImprovRepository
import io.homeassistant.companion.android.matter.MatterManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.thread.ThreadManager
import io.homeassistant.companion.android.webview.externalbus.ExternalBusMessage
import io.homeassistant.companion.android.webview.externalbus.ExternalBusRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import java.net.URL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * A context wrapper that also implements [WebView] for testing [WebViewPresenterImpl].
 */
private class FakeWebViewContext(
    base: Context,
    private val webViewDelegate: WebView,
) : ContextWrapper(base),
    WebView {
    override fun loadUrl(url: Uri, keepHistory: Boolean, openInApp: Boolean, serverHandleInsets: Boolean) {
        webViewDelegate.loadUrl(url, keepHistory, openInApp, serverHandleInsets)
    }

    override fun setStatusBarAndBackgroundColor(statusBarColor: Int, backgroundColor: Int) {
        webViewDelegate.setStatusBarAndBackgroundColor(statusBarColor, backgroundColor)
    }

    override fun setExternalAuth(script: String) {
        webViewDelegate.setExternalAuth(script)
    }

    override fun sendExternalBusMessage(message: ExternalBusMessage) {
        webViewDelegate.sendExternalBusMessage(message)
    }

    override fun relaunchApp() {
        webViewDelegate.relaunchApp()
    }

    override suspend fun unlockAppIfNeeded() {
        webViewDelegate.unlockAppIfNeeded()
    }

    override fun showError(errorType: WebView.ErrorType, error: SslError?, description: String?) {
        webViewDelegate.showError(errorType, error, description)
    }

    override fun showBlockInsecure(serverId: Int) {
        webViewDelegate.showBlockInsecure(serverId)
    }

    override fun showConnectionSecurityLevel(serverId: Int) {
        webViewDelegate.showConnectionSecurityLevel(serverId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class WebViewPresenterImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension(testDispatcher)

    private val webView: WebView = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val externalBusRepository: ExternalBusRepository = mockk(relaxed = true)
    private val improvRepository: ImprovRepository = mockk(relaxed = true)
    private val prefsRepository: PrefsRepository = mockk(relaxed = true)
    private val matterManager: MatterManager = mockk(relaxed = true)
    private val threadManager: ThreadManager = mockk(relaxed = true)
    private val settingsDao: SettingsDao = mockk(relaxed = true)
    private val authenticationRepository: AuthenticationRepository = mockk(relaxed = true)
    private val connectionStateProvider: ServerConnectionStateProvider = mockk(relaxed = true)

    private lateinit var fakeContext: FakeWebViewContext
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var lifecycle: LifecycleRegistry

    private lateinit var presenter: WebViewPresenterImpl

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        mockUriParse()
        fakeContext = FakeWebViewContext(mockk<Context>(), webView)

        lifecycleOwner = object : LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = this@WebViewPresenterImplTest.lifecycle
        }
        lifecycle = LifecycleRegistry.createUnsafe(lifecycleOwner)

        every { externalBusRepository.getSentFlow() } returns MutableSharedFlow()
        coEvery { serverManager.authenticationRepository(any()) } returns authenticationRepository
        coEvery { serverManager.connectionStateProvider(any()) } returns connectionStateProvider
    }

    private fun createPresenter(): WebViewPresenterImpl {
        presenter = WebViewPresenterImpl(
            context = fakeContext,
            serverManager = serverManager,
            externalBusRepository = externalBusRepository,
            improvRepository = improvRepository,
            prefsRepository = prefsRepository,
            matterUseCase = matterManager,
            threadUseCase = threadManager,
            settingsDao = settingsDao,
        )
        return presenter
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

    @Test
    fun `Given server exists and session connected when load called then collects url flow`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("https://example.com")))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        coVerify { connectionStateProvider.urlFlow(null) }
    }

    @Test
    fun `Given server exists and session anonymous when load called then does not collect url flow`() = runTest {
        val server = mockk<Server>(relaxed = true)

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.ANONYMOUS

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()
        advanceUntilIdle()

        presenter.load(lifecycle, path = null, isInternalOverride = null)
        advanceUntilIdle()

        coVerify(exactly = 0) { connectionStateProvider.urlFlow(any()) }
    }

    @Test
    fun `Given server does not exist when load called then falls back to active server`() = runTest {
        val activeServer = mockk<Server>(relaxed = true)
        every { activeServer.id } returns 1

        coEvery { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) } returns activeServer
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns flowOf(UrlState.HasUrl(URL("https://example.com")))

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()
        advanceUntilIdle()

        presenter.load(lifecycle, path = null, isInternalOverride = null)
        advanceUntilIdle()

        coVerify { serverManager.getServer(ServerManager.SERVER_ID_ACTIVE) }
    }

    @Test
    fun `Given url state is HasUrl when load called then loads url in webview`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val testUrl = URL("https://example.com")
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(testUrl))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        val urlSlot = slot<Uri>()
        verify { webView.loadUrl(capture(urlSlot), any(), any(), any()) }

        assertTrue(urlSlot.captured.toString().startsWith("https://example.com"))
        assertTrue(urlSlot.captured.toString().contains("external_auth=1"))
    }

    @Test
    fun `Given url state is HasUrl with path when load called then loads url with path`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val testUrl = URL("https://example.com")
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(testUrl))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = "/dashboard", isInternalOverride = null)
        }

        val urlSlot = slot<Uri>()
        verify { webView.loadUrl(capture(urlSlot), any(), any(), any()) }

        assertTrue(urlSlot.captured.toString().contains("/dashboard"))
    }

    @Test
    fun `Given url state is HasUrl with entityId path when load called then ignores path`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val testUrl = URL("https://example.com")
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(testUrl))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = "entityId:light.living_room", isInternalOverride = null)
        }

        val urlSlot = slot<Uri>()
        verify { webView.loadUrl(capture(urlSlot), any(), any(), any()) }

        // entityId paths should be ignored and base URL should be loaded
        assertTrue(urlSlot.captured.toString().startsWith("https://example.com"))
        assertFalse(urlSlot.captured.toString().contains("entityId"))
    }

    @Test
    fun `Given url state is InsecureState when load called then shows block insecure screen`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val urlFlow = MutableStateFlow<UrlState>(UrlState.InsecureState)

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        verify { webView.showBlockInsecure(serverId = any()) }
        verify(exactly = 0) { webView.loadUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `Given url state changes from HasUrl to InsecureState when collecting then shows block insecure`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("https://example.com")))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        // Verify initial URL was loaded
        verify { webView.loadUrl(any(), any(), any(), any()) }

        // Emit insecure state
        urlFlow.emit(UrlState.InsecureState)

        verify { webView.showBlockInsecure(serverId = any()) }
        // was not called a second time
        verify(exactly = 1) { webView.loadUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `Given previous load in progress when load called again then second flow is used`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val firstUrlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("https://first.com")))
        val secondUrlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("https://second.com")))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returnsMany listOf(firstUrlFlow, secondUrlFlow)

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        // First load
        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        // Second load (should use second flow)
        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        // Emit to second flow - should be processed since second job is active
        secondUrlFlow.emit(UrlState.HasUrl(URL("https://second-updated.com")))

        val urlSlot = mutableListOf<Uri>()
        verify(exactly = 3) { webView.loadUrl(capture(urlSlot), any(), any(), any()) }

        // Verify both initial URLs were loaded
        assertEquals(3, urlSlot.size)
        assertEquals("https://first.com?external_auth=1", urlSlot[0].toString())
        assertEquals("https://second.com?external_auth=1", urlSlot[1].toString())
        assertEquals("https://second-updated.com?external_auth=1", urlSlot[2].toString())
    }

    @Test
    fun `Given IllegalArgumentException when getting session state then does not collect url flow`() = runTest {
        val server = mockk<Server>(relaxed = true)

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } throws IllegalArgumentException("Server not found")

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()
        advanceUntilIdle()

        presenter.load(lifecycle, path = null, isInternalOverride = null)
        advanceUntilIdle()

        coVerify(exactly = 0) { connectionStateProvider.urlFlow(any()) }
    }

    @Test
    fun `Given lifecycle stops when collecting url flow then job is cancelled`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("https://example.com")))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        // Verify initial load
        verify(exactly = 1) { webView.loadUrl(any(), any(), any(), any()) }

        // Stop lifecycle
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        // Emit new URL - should be ignored since job is cancelled
        urlFlow.emit(UrlState.HasUrl(URL("https://updated.com")))

        // Should still be only 1 call (no additional calls after stop)
        verify(exactly = 1) { webView.loadUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `Given path consumed when url changes then path is not applied again`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("https://example.com")))

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = "/dashboard", isInternalOverride = null)
        }

        // First URL should have path
        val urlSlots = mutableListOf<Uri>()
        verify { webView.loadUrl(capture(urlSlots), any(), any(), any()) }
        assertEquals(1, urlSlots.size)
        assertTrue(urlSlots[0].toString().contains("/dashboard"))

        // Emit URL change (e.g., switching from internal to external)
        urlFlow.emit(UrlState.HasUrl(URL("https://external.example.com")))

        // Second URL should NOT have path (already consumed)
        verify(atLeast = 2) { webView.loadUrl(capture(urlSlots), any(), any(), any()) }
        val secondUrl = urlSlots.last().toString()
        assertTrue(secondUrl.contains("external.example.com"))
        assertFalse(secondUrl.contains("/dashboard"))
    }

    @Test
    fun `Given presenter initialized when getActiveServer called then returns current server id`() = runTest {
        val server = mockk<Server>(relaxed = true)
        every { server.id } returns 42
        coEvery { serverManager.getServer() } returns server
        coEvery { serverManager.isRegistered() } returns true

        createPresenter()
        advanceUntilIdle()

        assertEquals(42, presenter.getActiveServer())
    }

    @Test
    fun `Given security level not set when load called then shows security level fragment`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val connection = mockk<ServerConnectionInfo>(relaxed = true)
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("http://example.com")))

        every { server.connection } returns connection
        every { connection.hasPlainTextUrl } returns true
        every { connection.allowInsecureConnection } returns null

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        verify { webView.showConnectionSecurityLevel(serverId = any()) }
        verify(exactly = 0) { webView.loadUrl(any(), any(), any(), any()) }
    }

    @Test
    fun `Given security level already shown when load called again then loads url`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val connection = mockk<ServerConnectionInfo>(relaxed = true)
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("http://example.com")))

        every { server.connection } returns connection
        every { connection.hasPlainTextUrl } returns true
        every { connection.allowInsecureConnection } returns null

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        // First load should show security level fragment
        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        verify(exactly = 1) { webView.showConnectionSecurityLevel(serverId = any()) }

        // Simulate user dismissing the fragment (which calls onConnectionSecurityLevelShown)
        presenter.onConnectionSecurityLevelShown()

        // Second load should go directly to loadUrl (fragment already shown for this server)
        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        verify { webView.loadUrl(any(), any(), any(), any()) }
        // Still only 1 call to showConnectionSecurityLevel
        verify(exactly = 1) { webView.showConnectionSecurityLevel(serverId = any()) }
    }

    @Test
    fun `Given security level already set when load called then loads url directly`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val connection = mockk<ServerConnectionInfo>(relaxed = true)
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("http://example.com")))

        every { server.connection } returns connection
        every { connection.hasPlainTextUrl } returns true
        every { connection.allowInsecureConnection } returns true // Already set

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        verify { webView.loadUrl(any(), any(), any(), any()) }
        verify(exactly = 0) { webView.showConnectionSecurityLevel(serverId = any()) }
    }

    @Test
    fun `Given https url when load called then loads url without security level prompt`() = runTest(testDispatcher) {
        val server = mockk<Server>(relaxed = true)
        val connection = mockk<ServerConnectionInfo>(relaxed = true)
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(URL("https://example.com")))

        every { server.connection } returns connection
        every { connection.hasPlainTextUrl } returns false // HTTPS - no plain text URL

        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { authenticationRepository.getSessionState() } returns SessionState.CONNECTED
        coEvery { connectionStateProvider.urlFlow(any()) } returns urlFlow

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        createPresenter()

        backgroundScope.launch {
            presenter.load(lifecycle, path = null, isInternalOverride = null)
        }

        verify { webView.loadUrl(any(), any(), any(), any()) }
        verify(exactly = 0) { webView.showConnectionSecurityLevel(serverId = any()) }
    }
}
