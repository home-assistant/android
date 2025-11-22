package io.homeassistant.companion.android.onboarding.connection

import android.net.Uri
import android.net.http.SslError
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient.ERROR_AUTHENTICATION
import android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
import android.webkit.WebViewClient.ERROR_HOST_LOOKUP
import android.webkit.WebViewClient.ERROR_PROXY_AUTHENTICATION
import android.webkit.WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME
import androidx.annotation.StringRes
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import java.net.URL
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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

@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val keyChainRepository: KeyChainRepository = mockk()

    @BeforeEach
    fun setup() {
        mockkStatic(Uri::class)
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://homeassistant.local:8123", "https://cloud.ui.nabu.casa"])
    fun `Given a valid http url when buildAuthUrl then urlFlow emits correct auth url and isLoading is false`(baseUrl: String) = runTest {
        val viewModel = ConnectionViewModel(baseUrl, keyChainRepository)

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
        val viewModel = ConnectionViewModel("$baseUrl$suffix", keyChainRepository)

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
        val viewModel = ConnectionViewModel(malformedUrl, keyChainRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val urlFlow = viewModel.urlFlow.testIn(backgroundScope)
            val errorFlow = viewModel.errorFlow.testIn(backgroundScope)

            assertNull(errorFlow.awaitItem())

            advanceUntilIdle()
            assertNull(urlFlow.awaitItem())

            errorFlow.awaitConnectionError<ConnectionError.UnreachableError>(commonR.string.connection_screen_malformed_url, "Expected URL scheme 'http' or 'https' but no scheme was found for not_a_...", IllegalArgumentException::class)

            urlFlow.expectNoEvents()
            navigationEventsFlow.expectNoEvents()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given auth callback uri with code when shouldRedirect then emits Authenticated event with mTLS status and returns true`(requireMTLS: Boolean) = runTest {
        val authCode = "test_auth_code"
        val stringUri = mockAuthCodeUri(scheme = "homeassistant", host = "auth-callback", authCode = authCode)

        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", keyChainRepository)

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

        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", keyChainRepository)

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
        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", keyChainRepository)

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

    @Test
    fun `Given SSL errors when onReceivedSslError is invoked then errorFlow emits AuthenticationError with message`() = runTest {
        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", keyChainRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val errorFlow = viewModel.errorFlow.testIn(backgroundScope)

            assertNull(errorFlow.awaitItem())

            suspend fun testError(primaryError: Int?, @StringRes messageRes: Int) {
                val details = "SSL Error: $primaryError".takeIf { primaryError != null }.toString()
                viewModel.webViewClient.onReceivedSslError(
                    null,
                    null,
                    primaryError?.let {
                        mockk<SslError> {
                            every { this@mockk.primaryError } returns primaryError
                            every { this@mockk.toString() } returns details
                        }
                    },
                )
                errorFlow.awaitConnectionError<ConnectionError.AuthenticationError>(messageRes, details, SslError::class)
            }

            testError(SslError.SSL_DATE_INVALID, commonR.string.webview_error_SSL_DATE_INVALID)
            testError(SslError.SSL_EXPIRED, commonR.string.webview_error_SSL_EXPIRED)
            testError(SslError.SSL_IDMISMATCH, commonR.string.webview_error_SSL_IDMISMATCH)
            testError(SslError.SSL_INVALID, commonR.string.webview_error_SSL_INVALID)
            testError(SslError.SSL_NOTYETVALID, commonR.string.webview_error_SSL_NOTYETVALID)
            testError(SslError.SSL_UNTRUSTED, commonR.string.webview_error_SSL_UNTRUSTED)
            testError(null, commonR.string.error_ssl)

            navigationEventsFlow.expectNoEvents()
        }
    }

    @Test
    fun `Given HTTP errors when onReceivedHttpError is invoked then errorFlow emits appropriate error`() = runTest {
        val rawUrl = "http://homeassistant.local:8123"
        val viewModel = ConnectionViewModel(rawUrl, keyChainRepository)
        val webView = mockWebView()

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val errorFlow = viewModel.errorFlow.testIn(backgroundScope)
            val urlFlow = viewModel.urlFlow.testIn(backgroundScope)

            assertNull(errorFlow.awaitItem())

            assertEquals(null, urlFlow.awaitItem())
            assertNotNull(urlFlow.awaitItem())

            val request = mockk<WebResourceRequest> {
                every { url } returns mockk<Uri> {
                    every { this@mockk.toString() } returns "http://homeassistant.local:8123/auth/authorize?response_type=code&client_id=${AuthenticationService.CLIENT_ID}&redirect_uri=homeassistant://auth-callback"
                }
            }

            val webViewClient = viewModel.webViewClient

            // Expired cert
            webViewClient.isTLSClientAuthNeeded = true
            webViewClient.isCertificateChainValid = false
            webViewClient.onReceivedHttpError(webView, request, null)
            errorFlow.awaitConnectionError<ConnectionError.AuthenticationError>(commonR.string.tls_cert_expired_message, errorDetails(null, "No description"), WebResourceResponse::class)

            // Cert not found
            webViewClient.isTLSClientAuthNeeded = true
            webViewClient.isCertificateChainValid = true
            webViewClient.onReceivedHttpError(
                webView,
                request,
                mockk<WebResourceResponse> {
                    every { statusCode } returns 400
                    every { reasonPhrase } returns "reason"
                },
            )
            errorFlow.awaitConnectionError<ConnectionError.AuthenticationError>(commonR.string.tls_cert_not_found_message, errorDetails(400, "reason"), WebResourceResponse::class)

            // Generic error
            webViewClient.isTLSClientAuthNeeded = false
            webViewClient.isCertificateChainValid = false
            webViewClient.onReceivedHttpError(
                webView,
                request,
                mockk<WebResourceResponse> {
                    every { statusCode } returns 418
                    every { reasonPhrase } returns "I'm a teapot"
                },
            )
            errorFlow.awaitConnectionError<ConnectionError.UnknownError>(commonR.string.connection_error_unknown_error, errorDetails(418, "I'm a teapot"), WebResourceResponse::class)

            // Generic error without reason
            webViewClient.isTLSClientAuthNeeded = false
            webViewClient.isCertificateChainValid = false
            webViewClient.onReceivedHttpError(
                webView,
                request,
                mockk<WebResourceResponse> {
                    every { statusCode } returns 418
                    every { reasonPhrase } returns ""
                },
            )
            errorFlow.awaitConnectionError<ConnectionError.UnknownError>(commonR.string.connection_error_unknown_error, errorDetails(418, "No description"), WebResourceResponse::class)
            navigationEventsFlow.expectNoEvents()
        }
    }

    @Test
    fun `Given received error when onReceivedError is invoked then errorFlow emits appropriate error`() = runTest {
        val rawUrl = "http://homeassistant.local:8123"
        val viewModel = ConnectionViewModel(rawUrl, keyChainRepository)

        val webView = mockWebView()

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val urlFlow = viewModel.urlFlow.testIn(backgroundScope)
            val errorFlow = viewModel.errorFlow.testIn(backgroundScope)

            assertNull(errorFlow.awaitItem())
            assertEquals(null, urlFlow.awaitItem())
            assertNotNull(urlFlow.awaitItem())

            val request = mockk<WebResourceRequest> {
                every { url } returns mockk<Uri> {
                    every { this@mockk.toString() } returns "http://homeassistant.local:8123/auth/authorize?response_type=code&client_id=https://home-assistant.io/android&redirect_uri=homeassistant://auth-callback"
                }
            }

            suspend fun testAuthError(errorCode: Int, @StringRes messageRes: Int) {
                val description = "Error description"
                viewModel.webViewClient.onReceivedError(
                    webView,
                    request,
                    mockk<WebResourceError> {
                        every { this@mockk.errorCode } returns errorCode
                        every { this@mockk.description } returns description
                    },
                )
                errorFlow.awaitConnectionError<ConnectionError.AuthenticationError>(messageRes, errorDetails(errorCode, description), WebResourceError::class)
            }

            suspend fun testUnreachableError(errorCode: Int, @StringRes messageRes: Int) {
                val description = "Error description"
                viewModel.webViewClient.onReceivedError(
                    webView,
                    request,
                    mockk<WebResourceError> {
                        every { this@mockk.errorCode } returns errorCode
                        every { this@mockk.description } returns description
                    },
                )
                errorFlow.awaitConnectionError<ConnectionError.UnreachableError>(messageRes, errorDetails(errorCode, description), WebResourceError::class)
            }

            testAuthError(ERROR_FAILED_SSL_HANDSHAKE, commonR.string.webview_error_FAILED_SSL_HANDSHAKE)
            testAuthError(ERROR_AUTHENTICATION, commonR.string.webview_error_AUTHENTICATION)
            testAuthError(ERROR_PROXY_AUTHENTICATION, commonR.string.webview_error_PROXY_AUTHENTICATION)
            testAuthError(ERROR_UNSUPPORTED_AUTH_SCHEME, commonR.string.webview_error_AUTH_SCHEME)
            testUnreachableError(ERROR_HOST_LOOKUP, commonR.string.webview_error_HOST_LOOKUP)

            // Generic error with description
            viewModel.webViewClient.onReceivedError(
                webView,
                request,
                mockk<WebResourceError> {
                    every { this@mockk.errorCode } returns -1
                    every { this@mockk.description } returns "description"
                },
            )
            errorFlow.awaitConnectionError<ConnectionError.UnknownError>(commonR.string.connection_error_unknown_error, errorDetails(-1, "description"), WebResourceError::class)

            // Generic error without description
            viewModel.webViewClient.onReceivedError(
                webView,
                request,
                mockk<WebResourceError> {
                    every { this@mockk.errorCode } returns -1
                    every { this@mockk.description } returns ""
                },
            )
            errorFlow.awaitConnectionError<ConnectionError.UnknownError>(commonR.string.connection_error_unknown_error, errorDetails(-1, "No description"), WebResourceError::class)
            navigationEventsFlow.expectNoEvents()
        }
    }

    private fun errorDetails(code: Int?, description: String?): String {
        return "Status Code: ${code}\nDescription: $description"
    }

    private suspend inline fun <reified T : ConnectionError> ReceiveTurbine<ConnectionError?>.awaitConnectionError(messageId: Int, errorDetails: String?, errorClass: KClass<*>) {
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
                    errorDetails(code.captured.toIntOrNull(), detail.captured)
                }
                every { getString(commonR.string.no_description) } returns "No description"
            }
        }
    }
}
