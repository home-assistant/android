package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient.ERROR_HOST_LOOKUP
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckRepository
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.frontend.error.FrontendError
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.util.HAWebViewClient
import io.homeassistant.companion.android.util.HAWebViewClientFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import java.net.URL
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests for [ConnectionViewModel].
 *
 * Note: WebView error handling (SSL errors, HTTP errors, WebResource errors) is tested
 * in [io.homeassistant.companion.android.util.HAWebViewClientTest] to avoid duplication.
 */
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val keyChainRepository: KeyChainRepository = mockk(relaxed = true)
    private val webViewClientFactory: HAWebViewClientFactory = mockk {
        every {
            create(
                currentUrlFlow = any<StateFlow<String?>>(),
                onFrontendError = any(),
                frontendJsCallback = any(),
                onCrash = any(),
                onUrlIntercepted = any(),
                onPageFinished = any(),
            )
        } answers {
            HAWebViewClient(
                keyChainRepository = keyChainRepository,
                currentUrlFlow = firstArg(),
                onFrontendError = secondArg(),
                frontendJsCallback = thirdArg(),
                onCrash = arg(3),
                onUrlIntercepted = arg(4),
                onPageFinished = arg(5),
            )
        }
    }
    private val connectivityCheckRepository: ConnectivityCheckRepository = mockk()

    @BeforeEach
    fun setup() {
        mockkStatic(Uri::class)
        every { connectivityCheckRepository.runChecks(any()) } returns emptyFlow()
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://homeassistant.local:8123", "https://cloud.ui.nabu.casa"])
    fun `Given a valid http url when buildAuthUrl then urlFlow emits correct auth url and isLoading is false`(baseUrl: String) = runTest {
        val viewModel = ConnectionViewModel(baseUrl, webViewClientFactory, connectivityCheckRepository)

        turbineScope {
            val urlFlow = viewModel.urlFlow.testIn(backgroundScope)
            val isLoadingFlow = viewModel.isLoadingFlow.testIn(backgroundScope)
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val errorFlow = viewModel.errorFlow.testIn(backgroundScope)

            // Initial state
            assertNull(errorFlow.awaitItem())
            assertTrue(isLoadingFlow.awaitItem())
            assertEquals(null, urlFlow.awaitItem())

            val expectedAuthUrl = "$baseUrl/auth/authorize?response_type=code&client_id=${AuthenticationService.CLIENT_ID}&redirect_uri=homeassistant://auth-callback"
            advanceUntilIdle()

            assertEquals(expectedAuthUrl, urlFlow.awaitItem())

            viewModel.webViewClient.onPageFinished(mockk(), null)

            assertFalse(isLoadingFlow.awaitItem())
            errorFlow.expectNoEvents()

            navigationEventsFlow.expectNoEvents() // No authenticated or error events expected
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://homeassistant.local:8123", "https://cloud.ui.nabu.casa"])
    fun `Given a valid http url with suffix when buildAuthUrl then urlFlow emits correct auth url with path stripped`(baseUrl: String) = runTest {
        val suffix = "/hello?query=param&isHA=true#segment"
        val viewModel = ConnectionViewModel("$baseUrl$suffix", webViewClientFactory, connectivityCheckRepository)

        turbineScope {
            val urlFlow = viewModel.urlFlow.testIn(backgroundScope)

            assertEquals(null, urlFlow.awaitItem())

            val expectedAuthUrl = "$baseUrl/auth/authorize?response_type=code&client_id=${AuthenticationService.CLIENT_ID}&redirect_uri=homeassistant://auth-callback"
            advanceUntilIdle()

            assertEquals(expectedAuthUrl, urlFlow.awaitItem())
        }
    }

    @Test
    fun `Given a malformed url when buildAuthUrl then errorFlow emits malformed url error`() = runTest {
        val malformedUrl = "not_a_url"
        val viewModel = ConnectionViewModel(malformedUrl, webViewClientFactory, connectivityCheckRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val urlFlow = viewModel.urlFlow.testIn(backgroundScope)
            val errorFlow = viewModel.errorFlow.testIn(backgroundScope)

            assertNull(errorFlow.awaitItem())

            advanceUntilIdle()
            assertNull(urlFlow.awaitItem())

            errorFlow.awaitFrontendError<FrontendError.UnreachableError>(
                commonR.string.connection_screen_malformed_url,
                "Expected URL scheme 'http' or 'https' but no scheme was found for not_a_...",
                IllegalArgumentException::class,
            )

            urlFlow.expectNoEvents()
            navigationEventsFlow.expectNoEvents()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given auth callback uri with code when shouldRedirect then emits Authenticated event with mTLS status and returns true`(requireMTLS: Boolean) = runTest {
        val authCode = "test_auth_code"
        val stringUri = mockAuthCodeUri(scheme = "homeassistant", host = "auth-callback", authCode = authCode)

        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", webViewClientFactory, connectivityCheckRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val errorFlow = viewModel.errorFlow.testIn(backgroundScope)

            assertNull(errorFlow.awaitItem())

            viewModel.webViewClient.isTLSClientAuthNeeded = requireMTLS

            val result = viewModel.webViewClient.shouldOverrideUrlLoading(
                null,
                stringUri,
            )

            assertTrue(result)
            val event = navigationEventsFlow.awaitItem()
            assertTrue(event is ConnectionNavigationEvent.Authenticated)
            assertEquals(authCode, (event as ConnectionNavigationEvent.Authenticated).authCode)
            assertEquals("http://homeassistant.local:8123", event.url)
            assertEquals(requireMTLS, event.requiredMTLS)
            errorFlow.expectNoEvents()
        }
    }

    @Test
    fun `Given auth callback uri without code when shouldRedirect then no event and returns false`() = runTest {
        val stringUri = mockAuthCodeUri(scheme = "homeassistant", host = "auth-callback", authCode = null)

        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", webViewClientFactory, connectivityCheckRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val errorFlow = viewModel.errorFlow.testIn(backgroundScope)

            assertNull(errorFlow.awaitItem())

            val result = viewModel.webViewClient.shouldOverrideUrlLoading(
                null,
                stringUri,
            )

            assertFalse(result)
            navigationEventsFlow.expectNoEvents()
            errorFlow.expectNoEvents()
        }
    }

    @Test
    fun `Given unmatching uri and webview not null when shouldRedirect is invoked then open in external browser and return true`() = runTest {
        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", webViewClientFactory, connectivityCheckRepository)

        // Used to parse the rawUrl given in the constructor of ConnectionViewModel
        mockUriParse()

        val stringUri = mockAuthCodeUri(scheme = "http", host = "google", authCode = "not_related_code")

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val errorFlow = viewModel.errorFlow.testIn(backgroundScope)

            assertNull(errorFlow.awaitItem())

            val result = viewModel.webViewClient.shouldOverrideUrlLoading(
                null,
                stringUri,
            )

            assertTrue(result)
            val event = navigationEventsFlow.awaitItem()
            assertTrue(event is ConnectionNavigationEvent.OpenExternalLink)
            assertEquals(stringUri, (event as ConnectionNavigationEvent.OpenExternalLink).url.toString())

            navigationEventsFlow.expectNoEvents()
            errorFlow.expectNoEvents()
        }
    }

    private suspend inline fun <reified T : FrontendError> ReceiveTurbine<FrontendError?>.awaitFrontendError(
        messageId: Int,
        errorDetails: String?,
        errorClass: KClass<*>,
    ) {
        val error = awaitItem()
        assertNotNull(error)
        assertTrue(error is T)
        assertEquals(messageId, error.message)
        assertEquals(errorDetails, error.errorDetails)
        assertEquals(errorClass.toString(), error.rawErrorType)
    }

    private fun mockAuthCodeUri(scheme: String, host: String, authCode: String?): String {
        val stringUri = "$scheme://$host${authCode?.let { "?code=$authCode" } ?: ""}"
        every { Uri.parse(stringUri) } answers {
            val uriString = firstArg<String>()
            return@answers mockk<Uri> {
                every { this@mockk.toString() } returns uriString
                every { this@mockk.scheme } returns scheme
                every { this@mockk.host } returns host
                every { getQueryParameter("code") } returns authCode
            }
        }
        return stringUri
    }

    private fun mockUriParse() {
        every { Uri.parse(any()) } answers {
            val uriString = firstArg<String>()
            val javaURL = URL(uriString)
            return@answers mockk<Uri> {
                every { this@mockk.toString() } returns uriString
                every { host } returns javaURL.host
            }
        }
    }

    private fun mockWebView(): WebView {
        return mockk<WebView> {
            every { context } returns mockk {
                val code = slot<String>()
                val detail = slot<String>()
                every { getString(any(), capture(code), capture(detail)) } answers {
                    "Status Code: ${code.captured}\nDescription: ${detail.captured}"
                }
                every { getString(commonR.string.no_description) } returns "No description"
            }
        }
    }

    @Test
    fun `Given an error occurs when onReceivedError is invoked then runChecks is triggered`() = runTest {
        // Given
        val rawUrl = "http://homeassistant.local:8123"
        val connectivityFlow = MutableSharedFlow<ConnectivityCheckState>()
        every { connectivityCheckRepository.runChecks(rawUrl) } returns connectivityFlow

        val viewModel = ConnectionViewModel(rawUrl, webViewClientFactory, connectivityCheckRepository)
        val webView = mockWebView()

        advanceUntilIdle()
        val authUrl = viewModel.urlFlow.value
        assertNotNull(authUrl)

        val request = mockk<WebResourceRequest> {
            every { url } returns mockk<Uri> {
                every { this@mockk.toString() } returns authUrl
            }
        }

        // When
        viewModel.webViewClient.onReceivedError(
            webView,
            request,
            mockk<WebResourceError> {
                every { errorCode } returns ERROR_HOST_LOOKUP
                every { description } returns "Host lookup error"
            },
        )

        advanceUntilIdle()

        // Then
        assertTrue(viewModel.errorFlow.value is FrontendError.UnreachableError)
        verify(exactly = 1) { connectivityCheckRepository.runChecks(rawUrl) }
        assertEquals(1, connectivityFlow.subscriptionCount.value)
    }

    @Test
    fun `Given previous checks running when runConnectivityChecks is called then previous collection is cancelled`() = runTest {
        // Given
        val rawUrl = "http://homeassistant.local:8123"

        val first = MutableSharedFlow<ConnectivityCheckState>()
        val second = MutableSharedFlow<ConnectivityCheckState>()

        every { connectivityCheckRepository.runChecks(rawUrl) } returnsMany listOf(first, second)

        val viewModel = ConnectionViewModel(rawUrl, webViewClientFactory, connectivityCheckRepository)

        // When: first click on "Run checks"
        viewModel.runConnectivityChecks()
        runCurrent()

        // Then: first flow is being collected
        assertEquals(1, first.subscriptionCount.value)
        assertEquals(0, second.subscriptionCount.value)

        // When: second click on "Run checks"
        viewModel.runConnectivityChecks()
        runCurrent()

        // Then: previous collection is cancelled and only the latest one remains active
        assertEquals(0, first.subscriptionCount.value)
        assertEquals(1, second.subscriptionCount.value)

        // Then: repository method was called for each click
        verify(exactly = 2) { connectivityCheckRepository.runChecks(rawUrl) }
    }
}
