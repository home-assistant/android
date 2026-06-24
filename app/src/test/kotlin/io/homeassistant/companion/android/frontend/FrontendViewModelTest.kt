package io.homeassistant.companion.android.frontend

import android.net.Uri
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import app.cash.turbine.test
import com.google.zxing.BarcodeFormat
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckRepository
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckResult
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.prefs.ScreenOrientation
import io.homeassistant.companion.android.common.data.prefs.ZoomSettings
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.database.authentication.Authentication
import io.homeassistant.companion.android.database.authentication.AuthenticationDao
import io.homeassistant.companion.android.frontend.auth.FrontendHttpAuthHandler
import io.homeassistant.companion.android.frontend.barcode.FrontendBarcodeScannerHandler
import io.homeassistant.companion.android.frontend.dialog.FrontendDialog
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.frontend.download.DownloadResult
import io.homeassistant.companion.android.frontend.download.FrontendDownloadManager
import io.homeassistant.companion.android.frontend.error.ErrorActionIntent
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.error.errorActions
import io.homeassistant.companion.android.frontend.exoplayer.ExoPlayerUiState
import io.homeassistant.companion.android.frontend.exoplayer.FrontendExoPlayerManager
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticType
import io.homeassistant.companion.android.frontend.externalbus.outgoing.SuccessResultMessage
import io.homeassistant.companion.android.frontend.filechooser.FileChooserManager
import io.homeassistant.companion.android.frontend.gesture.FrontendGestureManager
import io.homeassistant.companion.android.frontend.gesture.GestureResult
import io.homeassistant.companion.android.frontend.handler.FrontendBusObserver
import io.homeassistant.companion.android.frontend.handler.FrontendHandlerEvent
import io.homeassistant.companion.android.frontend.improv.FrontendImprovHandler
import io.homeassistant.companion.android.frontend.improv.ImprovUIState
import io.homeassistant.companion.android.frontend.js.FrontendJsBridgeFactory
import io.homeassistant.companion.android.frontend.matterthread.FrontendMatterThreadHandler
import io.homeassistant.companion.android.frontend.navigation.FrontendEvent
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import io.homeassistant.companion.android.frontend.url.FrontendUrlManager
import io.homeassistant.companion.android.frontend.url.UrlLoadResult
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.util.HAWebViewClientFactory
import io.homeassistant.companion.android.util.LifecycleHandler
import io.homeassistant.companion.android.util.hasSameOrigin
import io.homeassistant.companion.android.util.mockServer
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
class FrontendViewModelTest {
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension(UnconfinedTestDispatcher())

    private val webViewClientFactory: HAWebViewClientFactory = mockk(relaxed = true)
    private val frontendBusObserver: FrontendBusObserver = mockk(relaxed = true)
    private val externalBusRepository: FrontendExternalBusRepository = mockk(relaxed = true)
    private val urlManager: FrontendUrlManager = mockk(relaxed = true)
    private val connectivityCheckRepository: ConnectivityCheckRepository = mockk(relaxed = true)
    private val permissionManager: PermissionManager = mockk(relaxed = true)
    private val frontendJsBridgeFactory: FrontendJsBridgeFactory = mockk(relaxed = true)
    private val downloadManager: FrontendDownloadManager = mockk(relaxed = true)
    private val gestureManager: FrontendGestureManager = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val keyChainRepository: KeyChainRepository = mockk(relaxed = true)
    private val zoomSettingsFlow = MutableStateFlow(ZoomSettings())
    private val autoPlayVideoFlow = MutableStateFlow(false)
    private val screenOrientationFlow = MutableStateFlow(ScreenOrientation.SYSTEM)
    private val keepScreenOnFlow = MutableStateFlow(false)
    private val prefsRepository: PrefsRepository = mockk(relaxed = true) {
        coEvery { this@mockk.zoomSettingsFlow() } returns this@FrontendViewModelTest.zoomSettingsFlow
        coEvery { this@mockk.autoPlayVideoFlow() } returns this@FrontendViewModelTest.autoPlayVideoFlow
        coEvery { this@mockk.screenOrientationFlow() } returns this@FrontendViewModelTest.screenOrientationFlow
        coEvery { this@mockk.keepScreenOnFlow() } returns this@FrontendViewModelTest.keepScreenOnFlow
    }

    private val serverId = 1
    private val testUrlWithAuth = "https://example.com?external_auth=1"

    @BeforeEach
    fun setUp() {
        every { frontendBusObserver.messageResults() } returns emptyFlow()
        every { frontendBusObserver.webViewActions() } returns emptyFlow()
        every { connectivityCheckRepository.runChecks(any()) } returns flowOf(ConnectivityCheckState())
        // Default: storage permission available so download tests don't bail out at the permission gate.
        // Tests that exercise the deny path override this explicitly.
        coEvery { permissionManager.checkStoragePermissionForDownload() } returns true
    }

    private val exoPlayerManager: FrontendExoPlayerManager = mockk(relaxed = true) {
        every { state } returns MutableStateFlow(null)
    }
    private val matterThreadHandler: FrontendMatterThreadHandler = mockk(relaxed = true) {
        every { events } returns MutableSharedFlow()
    }

    private val improvUiStateFlow = MutableStateFlow<ImprovUIState?>(null)
    private val improvEventsFlow = MutableSharedFlow<FrontendImprovHandler.Event>(extraBufferCapacity = 1)
    private val improvScanRequestedFlow = MutableStateFlow(false)
    private val improvHandler: FrontendImprovHandler = mockk(relaxed = true) {
        every { uiState } returns improvUiStateFlow
        every { events } returns improvEventsFlow
        every { scanRequested } returns improvScanRequestedFlow
    }

    private fun createViewModel(
        serverId: Int = this.serverId,
        path: String? = null,
        dialogManager: FrontendDialogManager = FrontendDialogManager(),
        fileChooserManager: FileChooserManager = FileChooserManager(),
        httpAuthHandler: FrontendHttpAuthHandler = FrontendHttpAuthHandler(
            authenticationDao = mockk(relaxed = true),
            clock = FakeClock(),
            dialogManager = dialogManager,
        ),
        improvHandler: FrontendImprovHandler = this.improvHandler,
    ): FrontendViewModel {
        return FrontendViewModel(
            initialServerId = serverId,
            initialTarget = FrontendTarget.fromRawPath(path),
            webViewClientFactory = webViewClientFactory,
            frontendBusObserver = frontendBusObserver,
            externalBusRepository = externalBusRepository,
            serverManager = serverManager,
            urlManager = urlManager,
            connectivityCheckRepository = connectivityCheckRepository,
            permissionManager = permissionManager,
            frontendJsBridgeFactory = frontendJsBridgeFactory,
            downloadManager = downloadManager,
            gestureManager = gestureManager,
            prefsRepository = prefsRepository,
            dialogManager = dialogManager,
            fileChooserManager = fileChooserManager,
            httpAuthHandler = httpAuthHandler,
            exoPlayerManager = exoPlayerManager,
            improvHandler = improvHandler,
            barcodeScannerHandler = FrontendBarcodeScannerHandler(externalBusRepository, dialogManager),
            matterThreadHandler = matterThreadHandler,
            keyChainRepository = keyChainRepository,
        )
    }

    @Nested
    inner class UrlInterception {

        /**
         * Captures the `onUrlIntercepted` callback the ViewModel wires into the WebView client and returns a
         * lambda that drives it (the `false` TLS-client-auth flag is irrelevant to scheme handling).
         *
         * The external/same-origin branch relies on [hasSameOrigin], which parses URLs via real
         * `android.net.Uri` (unavailable on the plain JVM these tests run on); tests covering that branch
         * mock [hasSameOrigin] directly. The origin parsing itself is covered by `UrlUtilTest`.
         */
        private fun createViewModelWithUrlInterceptCapture(): Pair<FrontendViewModel, (Uri) -> Boolean> {
            var capturedCallback: ((Uri, Boolean) -> Boolean)? = null
            every {
                webViewClientFactory.create(
                    currentUrlFlow = any(),
                    onFrontendError = any(),
                    onCrash = any(),
                    onUrlIntercepted = any(),
                    onPageFinished = any(),
                    onReceivedHttpAuthRequest = any(),
                    onCanGoBackChanged = any(),
                )
            } answers {
                // onUrlIntercepted is at parameter index 3 in HAWebViewClientFactory.create
                capturedCallback = arg(3)
                mockk(relaxed = true)
            }

            val viewModel = createViewModel()
            return viewModel to { uri ->
                val callback = capturedCallback
                assertNotNull(callback)
                callback.invoke(uri, false)
            }
        }

        private fun uri(value: String): Uri = mockk {
            every { this@mockk.toString() } returns value
        }

        @Test
        fun `Given app scheme url when intercepted then emits LaunchApp event and intercepts`() = runTest {
            val (viewModel, intercept) = createViewModelWithUrlInterceptCapture()

            viewModel.events.test {
                val handled = intercept(uri("app://com.example.app"))

                assertTrue(handled)
                assertEquals(FrontendEvent.LaunchApp("com.example.app"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given intent scheme url when intercepted then emits LaunchIntent event and intercepts`() = runTest {
            val intentUri = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing;end"
            val (viewModel, intercept) = createViewModelWithUrlInterceptCapture()

            viewModel.events.test {
                val handled = intercept(uri(intentUri))

                assertTrue(handled)
                assertEquals(FrontendEvent.LaunchIntent(intentUri), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given external url not matching server origin when intercepted then emits OpenExternalLink and intercepts`() = runTest {
            mockkStatic("io.homeassistant.companion.android.util.UrlUtilKt")
            try {
                every { any<Uri>().hasSameOrigin(any<String>()) } returns false
                val externalUri = uri("https://external.example.org/page")
                val (viewModel, intercept) = createViewModelWithUrlInterceptCapture()

                viewModel.events.test {
                    val handled = intercept(externalUri)

                    assertTrue(handled)
                    assertEquals(FrontendEvent.OpenExternalLink(externalUri), awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                unmockkStatic("io.homeassistant.companion.android.util.UrlUtilKt")
            }
        }

        @Test
        fun `Given url matching server origin when intercepted then emits no event and lets WebView load`() = runTest {
            mockkStatic("io.homeassistant.companion.android.util.UrlUtilKt")
            try {
                every { any<Uri>().hasSameOrigin(any<String>()) } returns true
                val (viewModel, intercept) = createViewModelWithUrlInterceptCapture()

                viewModel.events.test {
                    val handled = intercept(uri("https://example.com/lovelace/0"))

                    assertFalse(handled)
                    expectNoEvents()
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                unmockkStatic("io.homeassistant.companion.android.util.UrlUtilKt")
            }
        }
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
            assertTrue((state as FrontendViewState.Error).error is FrontendConnectionError.AuthRevoked)
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
            assertTrue((state as FrontendViewState.Error).error is FrontendConnectionError.Unreachable)
        }

        @Test
        fun `Given url manager returns success with path when initialized then loading state includes path`() = runTest {
            val urlWithPath = "https://example.com/dashboard?external_auth=1"
            every { urlManager.serverUrlFlow(serverId, FrontendTarget.Path("/dashboard")) } returns flowOf(
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
            assertTrue((state as FrontendViewState.Error).error is FrontendConnectionError.Unreachable)
            assertEquals(errorActions(state.error, isInternalConnection = false), state.actions)
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
    inner class DerivedFlows {

        @Test
        fun `Given url manager returns success then urlFlow value matches viewState url without subscribers`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // No collector on urlFlow — value must still be up to date (SharingStarted.Eagerly)
            assertEquals(testUrlWithAuth, viewModel.urlFlow.value)
        }

        @Test
        fun `Given error state then errorFlow value matches viewState error without subscribers`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.ServerNotFound(serverId),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.errorFlow.value is FrontendConnectionError.Unreachable)
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
            advanceUntilIdle()

            assertTrue(viewModel.errorFlow.value is FrontendConnectionError.Unreachable)
        }

        @Test
        fun `Given error state when onRetry called then error flow is cleared if no error`() = runTest {
            val urlResults = MutableStateFlow<UrlLoadResult>(UrlLoadResult.ServerNotFound(serverId))
            every { urlManager.serverUrlFlow(any(), any()) } returns urlResults

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Verify error exists
            assertTrue(viewModel.errorFlow.value != null)

            // Setup successful response for retry
            urlResults.value = UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId)

            viewModel.onRetry()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Error should be cleared
            assertEquals(null, viewModel.errorFlow.value)
        }

        @Test
        fun `Given loading state when onWebViewCreationFailed called then state transitions to Error with WebViewCreationError`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Verify initial loading state
            assertTrue(viewModel.viewState.value is FrontendViewState.Loading)

            // When
            val exception = UnsatisfiedLinkError("dlopen failed: libwebviewchromium.so is 32-bit")
            viewModel.onWebViewCreationFailed(exception)
            advanceUntilIdle()

            // Then
            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Error, "Expected Error state but got $state")
            val error = (state as FrontendViewState.Error).error
            assertTrue(error is FrontendConnectionError.Unrecoverable.WebViewCreationError)
            assertEquals(io.homeassistant.companion.android.common.R.string.webview_creation_failed, error.message)
            assertEquals("dlopen failed: libwebviewchromium.so is 32-bit", error.errorDetails)
            assertEquals("class java.lang.UnsatisfiedLinkError", error.rawErrorType)
        }

        @Test
        fun `Given WebViewCreationError state when url flow emits new url then error state is preserved`() = runTest {
            val urlFlow = MutableSharedFlow<UrlLoadResult>(replay = 1)
            every { urlManager.serverUrlFlow(any(), any()) } returns urlFlow

            // Emit initial URL
            urlFlow.emit(UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId))

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Verify initial loading state
            assertTrue(
                viewModel.viewState.value is FrontendViewState.Loading,
                "Expected Loading but got ${viewModel.viewState.value}",
            )

            // Simulate WebView creation failure
            viewModel.onWebViewCreationFailed(RuntimeException("WebView broken"))
            advanceUntilIdle()

            // Verify error state
            val errorState = viewModel.viewState.value
            assertTrue(errorState is FrontendViewState.Error)
            assertTrue((errorState as FrontendViewState.Error).error is FrontendConnectionError.Unrecoverable.WebViewCreationError)

            // Now emit a new URL (e.g., switching from external to internal network)
            urlFlow.emit(UrlLoadResult.Success(url = "https://internal.example.com?external_auth=1", serverId = serverId))
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // The error state should be preserved — new URL cannot fix a broken WebView
            val finalState = viewModel.viewState.value
            assertTrue(
                finalState is FrontendViewState.Error,
                "Expected Error to be preserved but got $finalState",
            )
            assertTrue((finalState as FrontendViewState.Error).error is FrontendConnectionError.Unrecoverable.WebViewCreationError)
        }
    }

    @Nested
    inner class MessageHandling {

        @Test
        fun `Given connected message result when collected then state transitions to Content`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
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
        fun `Given content when ShowBarcodeScanner then Content barcodeScanner is set`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()

            messageFlow.emit(
                FrontendHandlerEvent.ShowBarcodeScanner(
                    messageId = 7,
                    title = "Scan",
                    description = "Point camera",
                    alternativeOptionLabel = "Manual",
                ),
            )
            advanceUntilIdle()

            val barcode = (viewModel.viewState.value as FrontendViewState.Content).barcodeScanner
            assertEquals(7, barcode?.messageId)
            assertEquals("Scan", barcode?.title)
            assertEquals("Point camera", barcode?.description)
            assertEquals("Manual", barcode?.alternativeOptionLabel)
        }

        @Test
        fun `Given an active barcode scan when NotifyBarcodeScanner then an information dialog is shown`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.Connected)
            messageFlow.emit(
                FrontendHandlerEvent.ShowBarcodeScanner(1, "Scan", "Point", alternativeOptionLabel = null),
            )
            messageFlow.emit(FrontendHandlerEvent.NotifyBarcodeScanner("Already paired"))
            advanceUntilIdle()

            val dialog = assertInstanceOf(FrontendDialog.Information::class.java, viewModel.pendingDialog.value)
            assertEquals("Already paired", dialog.message)
        }

        @Test
        fun `Given an active barcode scan when CloseBarcodeScanner then barcodeScanner is cleared`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.Connected)
            messageFlow.emit(
                FrontendHandlerEvent.ShowBarcodeScanner(1, "Scan", "Point", alternativeOptionLabel = null),
            )
            messageFlow.emit(FrontendHandlerEvent.CloseBarcodeScanner)
            advanceUntilIdle()

            assertNull((viewModel.viewState.value as FrontendViewState.Content).barcodeScanner)
        }

        @Test
        fun `Given an active barcode scan when onBarcodeScanned then result is sent and scanner stays open`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.Connected)
            messageFlow.emit(
                FrontendHandlerEvent.ShowBarcodeScanner(7, "Scan", "Point", alternativeOptionLabel = null),
            )
            advanceUntilIdle()

            viewModel.onBarcodeScanned(rawValue = "HA-12345", format = BarcodeFormat.QR_CODE)
            advanceUntilIdle()

            coVerify { externalBusRepository.send(any()) }
            // The scanner stays open until the frontend sends bar_code/close.
            val barcode = (viewModel.viewState.value as FrontendViewState.Content).barcodeScanner
            assertEquals(7, barcode?.messageId)
        }

        @Test
        fun `Given auth error message result when collected then state transitions to Error`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Verify initial loading state
            assertTrue(viewModel.viewState.value is FrontendViewState.Loading)

            // Emit auth error message
            val authError = FrontendConnectionError.AuthRevoked(
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
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            // Collect events
            val events = mutableListOf<FrontendEvent>()
            val job = backgroundScope.launch { viewModel.events.collect { events.add(it) } }

            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Emit show assist message
            messageFlow.emit(FrontendHandlerEvent.ShowAssist(pipelineId = "abc", startListening = false))
            advanceUntilIdle()

            val event = events.filterIsInstance<FrontendEvent.NavigateToAssist>().firstOrNull()
            assertTrue(event != null, "Expected NavigateToAssist event")
            assertEquals(serverId, event!!.serverId)
            assertEquals("abc", event.pipelineId)
            assertEquals(false, event.startListening)
            job.cancel()
        }

        @Test
        fun `Given open settings message result when collected then NavigateToSettings event is emitted`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            // Collect events
            val events = mutableListOf<FrontendEvent>()
            val job = backgroundScope.launch { viewModel.events.collect { events.add(it) } }

            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Emit open settings message
            messageFlow.emit(FrontendHandlerEvent.OpenSettings)
            advanceUntilIdle()

            assertTrue(events.any { it is FrontendEvent.NavigateToSettings })
            job.cancel()
        }

        @Test
        fun `Given haptic message when collected then webViewActions emits Haptic action`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            viewModel.webViewActions.test {
                messageFlow.emit(FrontendHandlerEvent.PerformHaptic(HapticType.Success))
                messageFlow.emit(FrontendHandlerEvent.PerformHaptic(HapticType.Light))
                messageFlow.emit(FrontendHandlerEvent.PerformHaptic(HapticType.Heavy))

                assertEquals(HapticType.Success, (awaitItem() as WebViewAction.Haptic).type)
                assertEquals(HapticType.Light, (awaitItem() as WebViewAction.Haptic).type)
                assertEquals(HapticType.Heavy, (awaitItem() as WebViewAction.Haptic).type)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given gesture returns SwitchServer when handled then viewState transitions to new server`() = runTest {
            every { frontendBusObserver.messageResults() } returns emptyFlow()
            every { urlManager.serverUrlFlow(1, any()) } returns flowOf(
                UrlLoadResult.Success(url = "https://server1.com?external_auth=1", serverId = 1),
            )
            every { urlManager.serverUrlFlow(2, any()) } returns flowOf(
                UrlLoadResult.Success(url = "https://server2.com?external_auth=1", serverId = 2),
            )
            coEvery {
                gestureManager.handleGesture(serverId = any(), direction = any(), pointerCount = any())
            } returns GestureResult.SwitchServer(2)

            val viewModel = createViewModel(serverId = 1)
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            assertEquals(1, viewModel.viewState.value.serverId)

            viewModel.onGesture(GestureDirection.LEFT, pointerCount = 2)
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            assertEquals(2, viewModel.viewState.value.serverId)
            assertTrue(viewModel.viewState.value.url.contains("server2.com"))
        }

        @Test
        fun `Given NAVIGATE_DASHBOARD gesture on 2025_6 server then clears history and sends navigate`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery {
                gestureManager.handleGesture(serverId = any(), direction = any(), pointerCount = any())
            } returns GestureResult.NavigateToDefaultDashboard
            coEvery { serverManager.getServer(serverId) } returns mockServer(
                url = "https://ha.test",
                name = "t",
                haVersion = HomeAssistantVersion(2025, 6, 0),
                serverId = serverId,
            )

            val viewModel = createViewModel()
            val actions = mutableListOf<WebViewAction>()
            val job = backgroundScope.launch {
                viewModel.webViewActions.collect {
                    actions.add(it)
                    if (it is WebViewAction.ClearHistory) it.result.complete(Unit)
                }
            }
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onGesture(GestureDirection.UP, pointerCount = 2)
            advanceUntilIdle()

            assertEquals(1, actions.size)
            assertInstanceOf(WebViewAction.ClearHistory::class.java, actions[0])
            coVerify { externalBusRepository.send(any()) }
            job.cancel()
        }

        @Test
        fun `Given open assist settings message result when collected then NavigateToAssistSettings event is emitted`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            // Collect events
            val events = mutableListOf<FrontendEvent>()
            val job = backgroundScope.launch { viewModel.events.collect { events.add(it) } }

            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            messageFlow.emit(FrontendHandlerEvent.OpenAssistSettings)
            advanceUntilIdle()

            assertTrue(events.any { it is FrontendEvent.NavigateToAssistSettings })
            job.cancel()
        }

        @Test
        fun `Given WriteNfcTag handler event when collected then NavigateToNfcWrite is emitted`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            viewModel.events.test {
                advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

                messageFlow.emit(FrontendHandlerEvent.WriteNfcTag(messageId = 42, tagId = "tag-abc"))

                assertEquals(FrontendEvent.NavigateToNfcWrite(messageId = 42, tagId = "tag-abc"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given EntityAddToExecuted with event when collected then event is forwarded`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            viewModel.events.test {
                advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

                messageFlow.emit(
                    FrontendHandlerEvent.EntityAddToExecuted(
                        FrontendEvent.NavigateToWidgetConfig(
                            entityId = "light.test",
                            widgetType = io.homeassistant.companion.android.frontend.navigation.WidgetType.Entity,
                        ),
                    ),
                )

                val event = awaitItem()
                assertTrue(event is FrontendEvent.NavigateToWidgetConfig)
                assertEquals("light.test", (event as FrontendEvent.NavigateToWidgetConfig).entityId)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given EntityAddToExecuted with null event when collected then no event is emitted`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            viewModel.events.test {
                advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

                messageFlow.emit(FrontendHandlerEvent.EntityAddToExecuted(event = null))
                advanceUntilIdle()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given EntityAddToActionsSent when collected then no event is emitted`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            viewModel.events.test {
                advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

                messageFlow.emit(FrontendHandlerEvent.EntityAddToActionsSent)
                advanceUntilIdle()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given onNfcWriteCompleted when called then sends empty-result ResultMessage back to frontend`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            viewModel.onNfcWriteCompleted(messageId = 42)
            advanceUntilIdle()

            coVerify {
                externalBusRepository.send(SuccessResultMessage(id = 42))
            }
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
    inner class NotificationPermission {

        @Test
        fun `Given connected then checks notification permission via permission manager`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()

            coVerify { permissionManager.checkNotificationPermission(serverId) }
        }
    }

    @Nested
    inner class Permission {

        @Test
        fun `Given pending permission then viewModel exposes it from permission manager`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(permissionManager.pendingPermissionRequest, viewModel.pendingPermissionRequest)
        }
    }

    @Nested
    inner class Zoom {

        private fun createViewModelWithPageFinishedCapture(): Pair<FrontendViewModel, () -> Unit> {
            var capturedPageFinished: ((String?) -> Unit)? = null
            every {
                webViewClientFactory.create(
                    currentUrlFlow = any(),
                    onFrontendError = any(),
                    onCrash = any(),
                    onUrlIntercepted = any(),
                    onPageFinished = any(),
                    onReceivedHttpAuthRequest = any(),
                    onCanGoBackChanged = any(),
                )
            } answers {
                // onPageFinished is at parameter index 4 in HAWebViewClientFactory.create
                capturedPageFinished = arg(4)
                mockk(relaxed = true)
            }

            val viewModel = createViewModel()
            return viewModel to { capturedPageFinished!!.invoke(null) }
        }

        @Test
        fun `Given page finishes then ApplyZoom action is emitted with current settings`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            zoomSettingsFlow.value = ZoomSettings(zoomLevel = 150, pinchToZoomEnabled = true)

            val (viewModel, triggerPageFinished) = createViewModelWithPageFinishedCapture()

            viewModel.webViewActions.test {
                triggerPageFinished()

                val action = awaitItem() as WebViewAction.ApplyZoom
                assertEquals(150, action.zoomLevel)
                assertEquals(true, action.pinchToZoomEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given older-server more-info deep link when page finishes then OpenMoreInfo is dispatched`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId, moreInfoEntityId = "light.kitchen"),
            )

            val (viewModel, triggerPageFinished) = createViewModelWithPageFinishedCapture()

            viewModel.webViewActions.test {
                triggerPageFinished()

                assertEquals(WebViewAction.OpenMoreInfo("light.kitchen"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given more-info dispatched when page finishes again then OpenMoreInfo is not repeated`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId, moreInfoEntityId = "light.kitchen"),
            )

            val (viewModel, triggerPageFinished) = createViewModelWithPageFinishedCapture()

            viewModel.webViewActions.test {
                triggerPageFinished()
                assertEquals(WebViewAction.OpenMoreInfo("light.kitchen"), awaitItem())
                awaitItem() // ApplyZoom from the first page load

                triggerPageFinished()
                // Only the zoom action repeats; the more-info dialog must not be reopened.
                assertTrue(awaitItem() is WebViewAction.ApplyZoom)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given page loaded when settings change then ApplyZoom action is emitted without page finish`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val (viewModel, triggerPageFinished) = createViewModelWithPageFinishedCapture()

            viewModel.webViewActions.test {
                triggerPageFinished()
                awaitItem() // consume initial emission

                zoomSettingsFlow.value = ZoomSettings(zoomLevel = 150, pinchToZoomEnabled = true)

                val action = awaitItem() as WebViewAction.ApplyZoom
                assertEquals(150, action.zoomLevel)
                assertEquals(true, action.pinchToZoomEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given page finishes again then observer restarts with fresh values`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            zoomSettingsFlow.value = ZoomSettings(zoomLevel = 100, pinchToZoomEnabled = false)

            val (viewModel, triggerPageFinished) = createViewModelWithPageFinishedCapture()

            viewModel.webViewActions.test {
                triggerPageFinished()
                val first = awaitItem() as WebViewAction.ApplyZoom
                assertEquals(100, first.zoomLevel)

                // Settings changed between page loads
                zoomSettingsFlow.value = ZoomSettings(zoomLevel = 200, pinchToZoomEnabled = true)

                triggerPageFinished()
                val second = awaitItem() as WebViewAction.ApplyZoom
                assertEquals(200, second.zoomLevel)
                assertEquals(true, second.pinchToZoomEnabled)

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class HttpAuth {

        private val authenticationDao: AuthenticationDao = mockk(relaxed = true)
        private val dialogManager = FrontendDialogManager()
        private val httpAuthHandler = FrontendHttpAuthHandler(
            authenticationDao = authenticationDao,
            clock = FakeClock(),
            dialogManager = dialogManager,
        )

        private fun createViewModelWithAuthCapture(): Pair<FrontendViewModel, (HttpAuthHandler, String, String, String) -> Unit> {
            var capturedCallback: ((HttpAuthHandler, String, String, String) -> Unit)? = null
            every {
                webViewClientFactory.create(
                    currentUrlFlow = any(),
                    onFrontendError = any(),
                    onCrash = any(),
                    onUrlIntercepted = any(),
                    onPageFinished = any(),
                    onReceivedHttpAuthRequest = any(),
                    onCanGoBackChanged = any(),
                )
            } answers {
                capturedCallback = arg(5)
                mockk(relaxed = true)
            }

            val viewModel = createViewModel(httpAuthHandler = httpAuthHandler, dialogManager = dialogManager)
            val callback = capturedCallback
            assertNotNull(callback)
            return viewModel to callback
        }

        @Test
        fun `Given stored credentials when auth requested then auto-proceeds without dialog`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { authenticationDao.get(any()) } returns Authentication("key", "user", "pass")

            val (viewModel, triggerAuth) = createViewModelWithAuthCapture()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Transition to Content
            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()

            val handler = mockk<HttpAuthHandler>(relaxed = true)
            triggerAuth(handler, "example.com", "https://example.com/", "realm")
            advanceUntilIdle()

            verify { handler.proceed("user", "pass") }
            assertEquals(null, viewModel.pendingDialog.value)
        }

        @Test
        fun `Given no stored credentials when auth requested then dialog is shown`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { authenticationDao.get(any()) } returns null

            val (viewModel, triggerAuth) = createViewModelWithAuthCapture()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()

            triggerAuth(mockk(relaxed = true), "example.com", "https://example.com/", "realm")
            advanceUntilIdle()

            assertInstanceOf(FrontendDialog.HttpAuth::class.java, viewModel.pendingDialog.value)
        }

        @Test
        fun `Given auth dialog shown when cancel then snackbar event emitted`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { authenticationDao.get(any()) } returns null

            val (viewModel, triggerAuth) = createViewModelWithAuthCapture()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()

            val handler = mockk<HttpAuthHandler>(relaxed = true)
            triggerAuth(handler, "example.com", "https://example.com/", "realm")
            advanceUntilIdle()

            viewModel.events.test {
                val dialog = viewModel.pendingDialog.value as FrontendDialog.HttpAuth
                dialog.onCancel()
                advanceUntilIdle()

                verify { handler.cancel() }
                val event = awaitItem()
                assertTrue(event is FrontendEvent.ShowSnackbar)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class BackNavigation {

        private fun createViewModelWithCanGoBackCapture(): Pair<FrontendViewModel, (Boolean) -> Unit> {
            var capturedCanGoBackChanged: ((Boolean) -> Unit)? = null
            every {
                webViewClientFactory.create(
                    currentUrlFlow = any(),
                    onFrontendError = any(),
                    onCrash = any(),
                    onUrlIntercepted = any(),
                    onPageFinished = any(),
                    onReceivedHttpAuthRequest = any(),
                    onCanGoBackChanged = any(),
                )
            } answers {
                // onCanGoBackChanged is at parameter index 6 in HAWebViewClientFactory.create
                capturedCanGoBackChanged = arg(6)
                mockk(relaxed = true)
            }

            val viewModel = createViewModel()
            return viewModel to {
                val callback = capturedCanGoBackChanged
                assertNotNull(callback)
                callback.invoke(it)
            }
        }

        /** Reads the back-navigation flag from the current state (only the dashboard carries it). */
        private fun FrontendViewModel.canGoBack(): Boolean = (viewState.value as? FrontendViewState.Content)?.canGoBack == true

        @Test
        fun `Given Content state when WebView reports back availability then canGoBack reflects it`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val (viewModel, reportCanGoBack) = createViewModelWithCanGoBackCapture()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()

            assertFalse(viewModel.canGoBack())

            reportCanGoBack(true)
            assertTrue(viewModel.canGoBack())

            reportCanGoBack(false)
            assertFalse(viewModel.canGoBack())
        }

        @Test
        fun `Given WebView covered by an overlay when it can go back then canGoBack is false`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val (viewModel, reportCanGoBack) = createViewModelWithCanGoBackCapture()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()
            reportCanGoBack(true)
            assertTrue(viewModel.canGoBack())

            // An overlay (here the unrecoverable error screen) now covers the WebView: back must not
            // drive the hidden WebView's history.
            viewModel.onWebViewCreationFailed(RuntimeException("WebView unavailable"))
            advanceUntilIdle()

            assertFalse(viewModel.canGoBack())
        }

        @Test
        fun `Given the frontend connects when content is shown then webViewActions emits ClearHistory`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val (viewModel, _) = createViewModelWithCanGoBackCapture()

            viewModel.webViewActions.test {
                advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
                // Connecting makes the dashboard the current page; clearing history drops the
                // intermediate about:blank placeholder so it is not a reachable back entry.
                messageFlow.emit(FrontendHandlerEvent.Connected)
                advanceUntilIdle()

                assertInstanceOf(WebViewAction.ClearHistory::class.java, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given a connected server with back history when switching servers then history is cleared and canGoBack is false`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(1, any()) } returns flowOf(
                UrlLoadResult.Success(url = "https://server1.com?external_auth=1", serverId = 1),
            )
            every { urlManager.serverUrlFlow(2, any()) } returns flowOf(
                UrlLoadResult.Success(url = "https://server2.com?external_auth=1", serverId = 2),
            )

            val (viewModel, reportCanGoBack) = createViewModelWithCanGoBackCapture()

            // Connect to server 1 and let it accrue in-dashboard back history.
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()
            reportCanGoBack(true)
            assertTrue(viewModel.canGoBack())

            viewModel.webViewActions.test {
                viewModel.switchServer(2)
                advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
                messageFlow.emit(FrontendHandlerEvent.Connected)
                advanceUntilIdle()

                // The new server's history is cleared so back cannot return to the previous server.
                assertInstanceOf(WebViewAction.ClearHistory::class.java, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(2, viewModel.viewState.value.serverId)
            assertFalse(viewModel.canGoBack())
        }
    }

    @Nested
    inner class JsConfirm {

        private fun captureJsConfirmCallback(): Pair<FrontendViewModel, (String, JsResult) -> Boolean> {
            val viewModel = createViewModel()
            val client = viewModel.createWebChromeClient(onShowCustomView = {}, onHideCustomView = {})
            val callback: (String, JsResult) -> Boolean = { message, result ->
                // view and url are unused by HAWebChromeClient when message and result are non-null
                client.onJsConfirm(null, null, message, result)
            }
            return viewModel to callback
        }

        @Test
        fun `Given JS confirm received then dialog is exposed via pendingDialog`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val (viewModel, triggerJsConfirm) = captureJsConfirmCallback()
            advanceUntilIdle()

            triggerJsConfirm("Are you sure?", mockk(relaxed = true))
            advanceUntilIdle()

            val dialog = assertInstanceOf(FrontendDialog.Confirm::class.java, viewModel.pendingDialog.value)
            assertEquals("Are you sure?", dialog.message)
        }

        @Test
        fun `Given dialog confirmed then JsResult is confirmed and slot clears`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val (viewModel, triggerJsConfirm) = captureJsConfirmCallback()
            advanceUntilIdle()
            val jsResult: JsResult = mockk(relaxed = true)

            triggerJsConfirm("Are you sure?", jsResult)
            advanceUntilIdle()
            (viewModel.pendingDialog.value as FrontendDialog.Confirm).onConfirm()
            advanceUntilIdle()

            verify { jsResult.confirm() }
            assertEquals(null, viewModel.pendingDialog.value)
        }

        @Test
        fun `Given dialog cancelled then JsResult is cancelled and slot clears`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val (viewModel, triggerJsConfirm) = captureJsConfirmCallback()
            advanceUntilIdle()
            val jsResult: JsResult = mockk(relaxed = true)

            triggerJsConfirm("Are you sure?", jsResult)
            advanceUntilIdle()
            (viewModel.pendingDialog.value as FrontendDialog.Confirm).onCancel()
            advanceUntilIdle()

            verify { jsResult.cancel() }
            assertEquals(null, viewModel.pendingDialog.value)
        }

        @Test
        fun `Given a dialog already shown when second JS confirm arrives then it queues until first is resolved`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val (viewModel, triggerJsConfirm) = captureJsConfirmCallback()
            advanceUntilIdle()
            val firstResult: JsResult = mockk(relaxed = true)
            val secondResult: JsResult = mockk(relaxed = true)

            triggerJsConfirm("first", firstResult)
            advanceUntilIdle()
            triggerJsConfirm("second", secondResult)
            advanceUntilIdle()

            // Slot is still holding the first dialog; the second has not overwritten it.
            assertEquals("first", (viewModel.pendingDialog.value as FrontendDialog.Confirm).message)

            (viewModel.pendingDialog.value as FrontendDialog.Confirm).onConfirm()
            advanceUntilIdle()
            verify { firstResult.confirm() }

            assertEquals("second", (viewModel.pendingDialog.value as FrontendDialog.Confirm).message)
            verify(exactly = 0) { secondResult.confirm() }
            verify(exactly = 0) { secondResult.cancel() }
        }
    }

    @Nested
    inner class FileChooser {

        @Test
        fun `Given file chooser triggered then pendingFileChooser exposes the params`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()

            val filePathCallback = mockk<ValueCallback<Array<Uri>>>(relaxed = true)
            val fileChooserParams = mockk<WebChromeClient.FileChooserParams>(relaxed = true)

            val client = viewModel.createWebChromeClient(onShowCustomView = {}, onHideCustomView = {})

            val handled = client.onShowFileChooser(
                mockk(relaxed = true),
                filePathCallback,
                fileChooserParams,
            )
            advanceUntilIdle()

            assertTrue(handled)
            val pending = viewModel.pendingFileChooser.value
            assertNotNull(pending)
            assertTrue(pending.fileChooserParams === fileChooserParams)
        }

        @Test
        fun `Given pending file chooser when result delivered then filePathCallback receives uris and slot clears`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            val filePathCallback = mockk<ValueCallback<Array<Uri>>>(relaxed = true)

            val client = viewModel.createWebChromeClient(onShowCustomView = {}, onHideCustomView = {})

            client.onShowFileChooser(
                mockk(relaxed = true),
                filePathCallback,
                mockk(relaxed = true),
            )
            advanceUntilIdle()

            val pending = viewModel.pendingFileChooser.value
            assertNotNull(pending)

            val uris = arrayOf(mockk<Uri>())
            pending.onResult(uris)
            advanceUntilIdle()

            verify { filePathCallback.onReceiveValue(uris) }
            assertNull(viewModel.pendingFileChooser.value)
        }

        @Test
        fun `Given pending file chooser when user cancels then filePathCallback receives null and slot clears`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            val filePathCallback = mockk<ValueCallback<Array<Uri>>>(relaxed = true)

            val client = viewModel.createWebChromeClient(onShowCustomView = {}, onHideCustomView = {})

            client.onShowFileChooser(
                mockk(relaxed = true),
                filePathCallback,
                mockk(relaxed = true),
            )
            advanceUntilIdle()
            val request = viewModel.pendingFileChooser.value
            assertNotNull(request)
            request.onResult(null)
            advanceUntilIdle()

            verify { filePathCallback.onReceiveValue(null) }
            assertNull(viewModel.pendingFileChooser.value)
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
            assertTrue((state as FrontendViewState.Error).error is FrontendConnectionError.ExternalBusTimeout)
        }

        @Test
        fun `Given loading state when connected before timeout then no timeout error`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
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

    @Nested
    inner class DownloadHandling {

        @Test
        fun `Given download error result when download requested then emits ShowSnackbar UI event`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery {
                downloadManager.downloadFile(any(), any(), any(), any())
            } returns DownloadResult.Error(messageResId = commonR.string.downloads_failed)

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.events.test {
                viewModel.onDownloadRequested(
                    url = "https://example.com/file.pdf",
                    contentDisposition = "attachment",
                    mimetype = "application/pdf",
                )
                advanceUntilIdle()

                val event = awaitItem()
                assertTrue(event is FrontendEvent.ShowSnackbar)
                assertEquals(commonR.string.downloads_failed, (event as FrontendEvent.ShowSnackbar).messageResId)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given open with system result when download requested then emits OpenExternalLink event`() = runTest {
            val testUri = mockk<Uri> {
                every { this@mockk.toString() } returns "ftp://example.com/file.txt"
            }
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery {
                downloadManager.downloadFile(any(), any(), any(), any())
            } returns DownloadResult.OpenWithSystem(uri = testUri)

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.events.test {
                viewModel.onDownloadRequested(
                    url = "ftp://example.com/file.txt",
                    contentDisposition = "attachment",
                    mimetype = "application/octet-stream",
                )
                advanceUntilIdle()

                val event = awaitItem()
                assertTrue(event is FrontendEvent.OpenExternalLink)
                assertEquals(testUri, (event as FrontendEvent.OpenExternalLink).uri)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given forwarded result when download requested then no UI event emitted`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery {
                downloadManager.downloadFile(any(), any(), any(), any())
            } returns DownloadResult.Forwarded

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.events.test {
                viewModel.onDownloadRequested(
                    url = "https://example.com/file.pdf",
                    contentDisposition = "attachment",
                    mimetype = "application/pdf",
                )
                advanceUntilIdle()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given download requested when downloadManager called then passes current serverId`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery {
                downloadManager.downloadFile(any(), any(), any(), any())
            } returns DownloadResult.Forwarded

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onDownloadRequested(
                url = "https://example.com/file.pdf",
                contentDisposition = "attachment",
                mimetype = "application/pdf",
            )
            advanceUntilIdle()

            coVerify {
                downloadManager.downloadFile(
                    url = "https://example.com/file.pdf",
                    contentDisposition = "attachment",
                    mimetype = "application/pdf",
                    serverId = serverId,
                )
            }
        }

        @Test
        fun `Given storage permission denied when download requested then does not call downloadManager`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { permissionManager.checkStoragePermissionForDownload() } returns false

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onDownloadRequested(
                url = "https://example.com/file.pdf",
                contentDisposition = "attachment",
                mimetype = "application/pdf",
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { downloadManager.downloadFile(any(), any(), any(), any()) }
        }

        @Test
        fun `Given storage permission granted when download requested then proceeds with download`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { permissionManager.checkStoragePermissionForDownload() } returns true
            coEvery { downloadManager.downloadFile(any(), any(), any(), any()) } returns DownloadResult.Forwarded

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onDownloadRequested(
                url = "https://example.com/file.pdf",
                contentDisposition = "attachment",
                mimetype = "application/pdf",
            )
            advanceUntilIdle()

            coVerify {
                downloadManager.downloadFile(
                    url = "https://example.com/file.pdf",
                    contentDisposition = "attachment",
                    mimetype = "application/pdf",
                    serverId = serverId,
                )
            }
        }
    }

    @Nested
    inner class ExoPlayer {

        @Test
        fun `Given fullscreen true when onExoPlayerFullscreenChanged then manager is notified and RequestFullscreen true emitted`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onExoPlayerFullscreenChanged(isFullScreen = true)
                assertEquals(FrontendEvent.RequestFullscreen(fullscreen = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify { exoPlayerManager.onFullscreenChanged(isFullScreen = true) }
        }

        @Test
        fun `Given fullscreen false when onExoPlayerFullscreenChanged then manager is notified and RequestFullscreen false emitted`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()

            viewModel.events.test {
                viewModel.onExoPlayerFullscreenChanged(isFullScreen = false)
                assertEquals(FrontendEvent.RequestFullscreen(fullscreen = false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify { exoPlayerManager.onFullscreenChanged(isFullScreen = false) }
        }

        @Test
        fun `Given ExoPlayerAction message when handled then manager handle is called`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            createViewModel()

            val action = FrontendHandlerEvent.ExoPlayerAction.Stop
            messageFlow.emit(action)
            advanceUntilIdle()

            coVerify { exoPlayerManager.handle(action) }
        }

        @Test
        fun `Given player is in fullscreen when player state becomes null then RequestFullscreen false is emitted`() = runTest {
            val playerState = MutableStateFlow<ExoPlayerUiState?>(null)
            every { exoPlayerManager.state } returns playerState
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                // Simulate the player entering fullscreen, then being torn down (e.g. via
                // ExoPlayerAction.Stop or a server switch closing the manager).
                playerState.value = ExoPlayerUiState(player = mockk(relaxed = true), isFullScreen = true)
                playerState.value = null

                assertEquals(FrontendEvent.RequestFullscreen(fullscreen = false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given player never entered fullscreen when player state becomes null then no RequestFullscreen is emitted`() = runTest {
            val playerState = MutableStateFlow<ExoPlayerUiState?>(null)
            every { exoPlayerManager.state } returns playerState
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                playerState.value = ExoPlayerUiState(player = mockk<Player>(relaxed = true), isFullScreen = false)
                playerState.value = null
                advanceUntilIdle()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given Content state when state transitions out of Content then player is closed`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            every { urlManager.serverUrlFlow(2, any()) } returns flowOf(
                UrlLoadResult.Success(url = "https://example.com/2?external_auth=1", serverId = 2),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceUntilIdle()
            assertTrue(viewModel.viewState.value is FrontendViewState.Content)

            // Reset the recorded calls (without clearing the `state` stub) so we only verify
            // the close() invoked by the transition out of Content.
            clearMocks(exoPlayerManager, answers = false, childMocks = false)

            viewModel.switchServer(serverId = 2)
            advanceUntilIdle()

            verify(atLeast = 1) { exoPlayerManager.close() }
        }

        @Test
        fun `Given ViewModel is cleared when onCleared then manager is closed`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            advanceUntilIdle()

            // onCleared is protected on ViewModel; invoke it via reflection to simulate
            // ViewModel lifecycle teardown without pulling in the full ViewModelStore machinery.
            val onCleared = ViewModel::class.java.getDeclaredMethod("onCleared")
            onCleared.isAccessible = true
            onCleared.invoke(viewModel)

            verify { exoPlayerManager.close() }
        }
    }

    @Nested
    inner class CustomView {

        @Test
        fun `Given factory client when onShowCustomView then provided show callback receives the View`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            var capturedView: View? = null
            val client = viewModel.createWebChromeClient(
                onShowCustomView = { capturedView = it },
                onHideCustomView = {},
            )
            val customView = mockk<View>(relaxed = true)

            client.onShowCustomView(customView, mockk(relaxed = true))

            assertSame(customView, capturedView)
        }

        @Test
        fun `Given factory client when onHideCustomView then provided hide callback is invoked`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            var hideInvoked = false
            val client = viewModel.createWebChromeClient(
                onShowCustomView = {},
                onHideCustomView = { hideInvoked = true },
            )

            client.onHideCustomView()

            assertTrue(hideInvoked)
        }

        @Test
        fun `Given factory client when onShowCustomView then RequestFullscreen true emitted`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            val client = viewModel.createWebChromeClient(onShowCustomView = {}, onHideCustomView = {})

            viewModel.events.test {
                client.onShowCustomView(mockk<View>(relaxed = true), mockk(relaxed = true))
                assertEquals(FrontendEvent.RequestFullscreen(fullscreen = true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given factory client when onHideCustomView then RequestFullscreen false emitted`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            val viewModel = createViewModel()
            val client = viewModel.createWebChromeClient(onShowCustomView = {}, onHideCustomView = {})

            viewModel.events.test {
                client.onShowCustomView(mockk<View>(relaxed = true), mockk(relaxed = true))
                assertEquals(FrontendEvent.RequestFullscreen(fullscreen = true), awaitItem())
                client.onHideCustomView()
                assertEquals(FrontendEvent.RequestFullscreen(fullscreen = false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class AutoPlayVideoSetting {

        @Test
        fun `Given pref flow emits new value when collected then exposed StateFlow reflects it`() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(false, viewModel.autoPlayVideoEnabled.value)

            autoPlayVideoFlow.value = true
            advanceUntilIdle()

            assertEquals(true, viewModel.autoPlayVideoEnabled.value)
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `Given pref flow seeded with value when ViewModel constructed then exposed StateFlow has that value`(
            value: Boolean,
        ) = runTest {
            autoPlayVideoFlow.value = value

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(value, viewModel.autoPlayVideoEnabled.value)
        }
    }

    @Nested
    inner class ScreenOrientationSetting {

        @Test
        fun `Given pref flow emits new value when collected then exposed StateFlow reflects it`() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(ScreenOrientation.SYSTEM, viewModel.screenOrientation.value)

            screenOrientationFlow.value = ScreenOrientation.LANDSCAPE
            advanceUntilIdle()

            assertEquals(ScreenOrientation.LANDSCAPE, viewModel.screenOrientation.value)
        }

        @Test
        fun `Given pref flow seeded with portrait when ViewModel constructed then exposed StateFlow has portrait`() = runTest {
            screenOrientationFlow.value = ScreenOrientation.PORTRAIT

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(ScreenOrientation.PORTRAIT, viewModel.screenOrientation.value)
        }
    }

    @Nested
    inner class KeepScreenOnSetting {

        @Test
        fun `Given pref flow emits new value when collected then exposed StateFlow reflects it`() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.keepScreenOnEnabled.value)

            keepScreenOnFlow.value = true
            advanceUntilIdle()

            assertTrue(viewModel.keepScreenOnEnabled.value)
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `Given pref flow seeded with value when ViewModel constructed then exposed StateFlow has that value`(
            value: Boolean,
        ) = runTest {
            keepScreenOnFlow.value = value

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(value, viewModel.keepScreenOnEnabled.value)
        }
    }

    @Nested
    inner class Improv {

        @Test
        fun `Given StartImprovScan event when received then handler is invoked`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.StartImprovScan)
            advanceTimeBy(1.seconds)

            coVerify { improvHandler.onStartImprovScan() }
        }

        @Test
        fun `Given ConfigureImprovDevice event when received then handler is invoked with name`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.ConfigureImprovDevice(deviceName = "Smart Plug"))
            advanceTimeBy(1.seconds)

            coVerify { improvHandler.onConfigureImprovDevice("Smart Plug") }
        }

        @Test
        fun `Given handler emits uiState when collected then Content improvUiState is updated`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
            messageFlow.emit(FrontendHandlerEvent.Connected)
            advanceTimeBy(1.seconds)

            improvUiStateFlow.value = ImprovUIState.SearchingDevice(deviceName = "Smart Plug")
            advanceTimeBy(1.seconds)

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.Content)
            assertNotNull((state as FrontendViewState.Content).improvUiState)
        }

        @Test
        fun `Given handler emits ReloadAtPath event when collected then state transitions to LoadServer`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            improvEventsFlow.emit(
                FrontendImprovHandler.Event.ReloadAtPath(
                    path = "/_my_redirect/config_flow_start?domain=acme",
                    serverId = serverId,
                ),
            )
            advanceTimeBy(1.seconds)

            val state = viewModel.viewState.value
            assertTrue(state is FrontendViewState.LoadServer)
            assertEquals(
                FrontendTarget.Path("/_my_redirect/config_flow_start?domain=acme"),
                (state as FrontendViewState.LoadServer).target,
            )
        }

        @Test
        fun `Given onImprovSheetDismissed when called then handler onDismissed is invoked`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onImprovSheetDismissed()
            advanceTimeBy(1.seconds)

            coVerify { improvHandler.onDismissed(serverId = any()) }
        }

        @Test
        fun `Given onImprovConnectDevice when called then forwards to handler`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onImprovConnectDevice("wifi", "pwd")
            advanceTimeBy(1.seconds)

            coVerify { improvHandler.onConnectDevice(any(), "wifi", "pwd") }
        }

        @Test
        fun `Given onImprovRestart when called then forwards to handler`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onImprovRestart()
            advanceTimeBy(1.seconds)

            coVerify { improvHandler.onRestart() }
        }

        @Test
        fun `Given improvScanRequested exposed when collected then mirrors handler scanRequested`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            improvScanRequestedFlow.value = true
            assertEquals(true, viewModel.improvScanRequested.value)
            improvScanRequestedFlow.value = false
            assertEquals(false, viewModel.improvScanRequested.value)
        }

        @Test
        fun `Given processImprovScanRequests when called then forwards to handler`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            val viewModel = createViewModel()
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.processImprovScanRequests()

            coVerify { improvHandler.processImprovScanRequests() }
        }
    }

    @Nested
    inner class FirstViewOnStart {

        @Test
        fun `Given pref on and allowed url on 2025_6 server when onLeavingApp then clears history and sends navigate`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled() } returns true
            coEvery { serverManager.getServer(serverId) } returns mockServer(
                url = "https://ha.test",
                name = "t",
                haVersion = HomeAssistantVersion(2025, 6, 0),
                serverId = serverId,
            )

            val viewModel = createViewModel()
            val actions = mutableListOf<WebViewAction>()
            val job = backgroundScope.launch {
                viewModel.webViewActions.collect {
                    actions.add(it)
                    if (it is WebViewAction.ClearHistory) it.result.complete(Unit)
                }
            }
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onLeavingApp("https://ha.test/lovelace/0")
            advanceUntilIdle()

            assertEquals(1, actions.size)
            assertInstanceOf(WebViewAction.ClearHistory::class.java, actions[0])
            coVerify { externalBusRepository.send(any()) }
            job.cancel()
        }

        @Suppress("DEPRECATION")
        @Test
        fun `Given pref on and allowed url on old server when onLeavingApp then clears history and emits sidebar fallback`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled() } returns true
            coEvery { serverManager.getServer(serverId) } returns mockServer(
                url = "https://ha.test",
                name = "t",
                haVersion = HomeAssistantVersion(2025, 5, 0),
                serverId = serverId,
            )

            val viewModel = createViewModel()
            val actions = mutableListOf<WebViewAction>()
            val job = backgroundScope.launch {
                viewModel.webViewActions.collect {
                    actions.add(it)
                    if (it is WebViewAction.ClearHistory) it.result.complete(Unit)
                }
            }
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onLeavingApp("https://ha.test/lovelace/0")
            advanceUntilIdle()

            assertEquals(2, actions.size)
            assertInstanceOf(WebViewAction.ClearHistory::class.java, actions[0])
            assertInstanceOf(WebViewAction.NavigateToDefaultPanelViaSidebar::class.java, actions[1])
            coVerify(exactly = 0) { externalBusRepository.send(any()) }
            job.cancel()
        }

        @Test
        fun `Given pref off when onLeavingApp then does nothing`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled() } returns false

            val viewModel = createViewModel()
            val actions = mutableListOf<WebViewAction>()
            val job = backgroundScope.launch { viewModel.webViewActions.collect { actions.add(it) } }
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onLeavingApp("https://ha.test/lovelace/0")
            advanceUntilIdle()

            assertTrue(actions.isEmpty())
            coVerify(exactly = 0) { externalBusRepository.send(any()) }
            job.cancel()
        }

        @Test
        fun `Given excluded config url when onLeavingApp then does nothing`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled() } returns true

            val viewModel = createViewModel()
            val actions = mutableListOf<WebViewAction>()
            val job = backgroundScope.launch { viewModel.webViewActions.collect { actions.add(it) } }
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onLeavingApp("https://ha.test/config/general")
            advanceUntilIdle()

            assertTrue(actions.isEmpty())
            coVerify(exactly = 0) { externalBusRepository.send(any()) }
            job.cancel()
        }

        @Test
        fun `Given excluded hassio url when onLeavingApp then does nothing`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled() } returns true

            val viewModel = createViewModel()
            val actions = mutableListOf<WebViewAction>()
            val job = backgroundScope.launch { viewModel.webViewActions.collect { actions.add(it) } }
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onLeavingApp("https://ha.test/hassio/dashboard")
            advanceUntilIdle()

            assertTrue(actions.isEmpty())
            coVerify(exactly = 0) { externalBusRepository.send(any()) }
            job.cancel()
        }

        @Test
        fun `Given config dashboard url when onLeavingApp then navigates`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled() } returns true
            coEvery { serverManager.getServer(serverId) } returns mockServer(
                url = "https://ha.test",
                name = "t",
                haVersion = HomeAssistantVersion(2025, 6, 0),
                serverId = serverId,
            )

            val viewModel = createViewModel()
            val actions = mutableListOf<WebViewAction>()
            val job = backgroundScope.launch {
                viewModel.webViewActions.collect {
                    actions.add(it)
                    if (it is WebViewAction.ClearHistory) it.result.complete(Unit)
                }
            }
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            viewModel.onLeavingApp("https://ha.test/config/dashboard")
            advanceUntilIdle()

            assertEquals(1, actions.size)
            assertInstanceOf(WebViewAction.ClearHistory::class.java, actions[0])
            coVerify { externalBusRepository.send(any()) }
            job.cancel()
        }

        @Test
        fun `Given app not in background when onLeavingApp then does nothing`() = runTest {
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            coEvery { prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled() } returns true

            val viewModel = createViewModel()
            val actions = mutableListOf<WebViewAction>()
            val job = backgroundScope.launch { viewModel.webViewActions.collect { actions.add(it) } }
            advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)

            // Simulate a started activity so LifecycleHandler reports the app is in the foreground.
            val activity = mockk<android.app.Activity>(relaxed = true)
            LifecycleHandler.onActivityStarted(activity)
            try {
                viewModel.onLeavingApp("https://ha.test/lovelace/0")
                advanceUntilIdle()
                assertTrue(actions.isEmpty())
            } finally {
                LifecycleHandler.onActivityStopped(activity) // reset global counter for other tests
            }
            job.cancel()
        }
    }

    @Nested
    inner class MatterThreadRouting {

        @Test
        fun `Given StartMatterCommissioning handler event when collected then handler is called`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            createViewModel()
            advanceUntilIdle()

            messageFlow.emit(FrontendHandlerEvent.StartMatterCommissioning)
            advanceUntilIdle()

            coVerify { matterThreadHandler.onStartMatterCommissioning() }
        }

        @Test
        fun `Given ImportThreadCredentials handler event when collected then handler is called with current serverId`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )

            createViewModel()
            advanceUntilIdle()

            messageFlow.emit(FrontendHandlerEvent.ImportThreadCredentials)
            advanceUntilIdle()

            coVerify { matterThreadHandler.onImportThreadCredentials(serverId = serverId) }
        }

        @Test
        fun `Given onMatterThreadIntentResult when called then forwards to handler`() = runTest {
            val viewModel = createViewModel()
            val result = androidx.activity.result.ActivityResult(android.app.Activity.RESULT_OK, null)

            viewModel.onMatterThreadIntentResult(result)
            advanceUntilIdle()

            coVerify { matterThreadHandler.onMatterThreadIntentResult(result) }
        }
    }

    @Nested
    inner class ErrorActions {

        @Test
        fun `Given RemoveServerAndRelaunch when onErrorAction then removes server and emits Relaunch`() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onErrorAction(ErrorActionIntent.RemoveServerAndRelaunch)
                assertEquals(FrontendEvent.Relaunch, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { serverManager.removeServer(serverId) }
        }

        @Test
        fun `Given ClearKeychainAndRelaunch when onErrorAction then clears keychain and emits Relaunch`() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onErrorAction(ErrorActionIntent.ClearKeychainAndRelaunch)
                assertEquals(FrontendEvent.Relaunch, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { keyChainRepository.clear() }
        }

        @Test
        fun `Given GoToSettings when onErrorAction then emits NavigateToSettings`() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onErrorAction(ErrorActionIntent.GoToSettings)
                assertEquals(FrontendEvent.NavigateToSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given OpenSecuritySettings when onErrorAction then emits OpenSecuritySettings`() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onErrorAction(ErrorActionIntent.OpenSecuritySettings)
                assertEquals(FrontendEvent.OpenSecuritySettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given UpdateWebView when onErrorAction then emits UpdateWebView`() = runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.onErrorAction(ErrorActionIntent.UpdateWebView)
                assertEquals(FrontendEvent.UpdateWebView, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class SecurityVersionWarning {

        private fun stubIntegrationRepository(versionAtLeast: Boolean, shouldNotify: Boolean) {
            val integrationRepository = mockk<IntegrationRepository> {
                coEvery { isHomeAssistantVersionAtLeast(2021, 1, 5) } returns versionAtLeast
                coEvery { shouldNotifySecurityWarning() } returns shouldNotify
            }
            coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        }

        @Test
        fun `Given outdated server when connected then shows security warning snackbar`() = runTest {
            // android.net.Uri is unavailable on the plain JVM; stub parsing for the snackbar link.
            mockkStatic(Uri::class)
            every { Uri.parse(any()) } returns mockk(relaxed = true)
            try {
                val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
                every { frontendBusObserver.messageResults() } returns messageFlow
                every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                    UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
                )
                stubIntegrationRepository(versionAtLeast = false, shouldNotify = true)

                val viewModel = createViewModel()
                viewModel.events.test {
                    advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
                    messageFlow.emit(FrontendHandlerEvent.Connected)

                    val event = awaitItem()
                    assertTrue(event is FrontendEvent.ShowSnackbar)
                    assertEquals(
                        commonR.string.security_vulnerably_message,
                        (event as FrontendEvent.ShowSnackbar).messageResId,
                    )
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                unmockkStatic(Uri::class)
            }
        }

        @Test
        fun `Given up-to-date server when connected then no security warning snackbar`() = runTest {
            val messageFlow = MutableSharedFlow<FrontendHandlerEvent>()
            every { frontendBusObserver.messageResults() } returns messageFlow
            every { urlManager.serverUrlFlow(any(), any()) } returns flowOf(
                UrlLoadResult.Success(url = testUrlWithAuth, serverId = serverId),
            )
            stubIntegrationRepository(versionAtLeast = true, shouldNotify = true)

            val viewModel = createViewModel()
            viewModel.events.test {
                advanceTimeBy(CONNECTION_TIMEOUT - 1.seconds)
                messageFlow.emit(FrontendHandlerEvent.Connected)
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
