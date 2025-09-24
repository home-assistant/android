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
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.onboarding.R
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.util.TLSWebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.net.URL
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
import timber.log.Timber

@ExtendWith(MainDispatcherJUnit5Extension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val keyChainRepository: KeyChainRepository = mockk()

    @BeforeEach
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://homeassistant.local:8123", "https://cloud.ui.nabu.casa"])
    fun `Given a valid http url when buildAuthUrl then urlFlow emits correct auth url and isLoading is false`(baseUrl: String) = runTest {
        val viewModel = ConnectionViewModel(baseUrl, keyChainRepository)

        turbineScope {
            val urlFlow = viewModel.urlFlow.testIn(backgroundScope)
            val isLoadingFlow = viewModel.isLoadingFlow.testIn(backgroundScope)
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)

            // Initial state
            assertFalse(viewModel.isErrorFlow.value)
            assertTrue(isLoadingFlow.awaitItem())
            assertEquals(null, urlFlow.awaitItem())

            val expectedAuthUrl = "$baseUrl/auth/authorize?response_type=code&client_id=${AuthenticationService.CLIENT_ID}&redirect_uri=homeassistant://auth-callback"
            advanceUntilIdle()

            assertEquals(expectedAuthUrl, urlFlow.awaitItem())

            viewModel.webViewClient.onPageFinished(mockk(), null)

            assertFalse(isLoadingFlow.awaitItem())
            assertFalse(viewModel.isErrorFlow.value)

            navigationEventsFlow.expectNoEvents() // No authenticated or error events expected
        }
    }

    @Test
    fun `Given a malformed url when buildAuthUrl then navigationEventsFlow emits malformed url error`() = runTest {
        val malformedUrl = "not_a_url"
        val viewModel = ConnectionViewModel(malformedUrl, keyChainRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val urlFlow = viewModel.urlFlow.testIn(backgroundScope)

            advanceUntilIdle()
            assertNull(urlFlow.awaitItem())

            assertError(navigationEventsFlow.awaitItem(), R.string.connection_screen_malformed_url)
            assertTrue(viewModel.isErrorFlow.value)

            urlFlow.expectNoEvents()
        }
    }

    @Test
    fun `Given auth callback uri with code when shouldRedirect then emits Authenticated event and returns true`() = runTest {
        val authCode = "test_auth_code"
        val callbackUri = mockk<Uri> {
            every { scheme } returns "homeassistant"
            every { host } returns "auth-callback"
            every { getQueryParameter("code") } returns authCode
        }

        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", keyChainRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)

            val result = viewModel.webViewClient.shouldOverrideUrlLoading(
                null,
                mockk<WebResourceRequest> {
                    every { url } returns callbackUri
                },
            )

            assertTrue(result)
            val event = navigationEventsFlow.awaitItem()
            assertTrue(event is ConnectionNavigationEvent.Authenticated)
            assertEquals(authCode, (event as ConnectionNavigationEvent.Authenticated).authCode)
            assertEquals("http://homeassistant.local:8123", event.url)
            assertFalse(viewModel.isErrorFlow.value)
        }
    }

    @Test
    fun `Given auth callback uri without code when shouldRedirect then no event and returns false`() = runTest {
        val callbackUri = mockk<Uri> {
            every { scheme } returns "homeassistant"
            every { host } returns "auth-callback"
            every { getQueryParameter("code") } returns null
        }

        mockUriParse()

        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", keyChainRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)

            val result = viewModel.webViewClient.shouldOverrideUrlLoading(
                null,
                mockk<WebResourceRequest> {
                    every { url } returns callbackUri
                },
            )

            assertFalse(result)
            navigationEventsFlow.expectNoEvents()
            assertFalse(viewModel.isErrorFlow.value)
        }
    }

    @Test
    fun `Given unmatching uri and webview not null when shouldRedirect is invoked then open in external browser and return true`() = runTest {
        val callbackUri = mockk<Uri> {
            every { scheme } returns "http"
            every { host } returns "google"
            every { getQueryParameter("code") } returns "not_related_code"
        }

        mockUriParse()

        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", keyChainRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)

            val result = viewModel.webViewClient.shouldOverrideUrlLoading(
                null,
                mockk<WebResourceRequest> {
                    every { url } returns callbackUri
                },
            )

            assertTrue(result)
            val event = navigationEventsFlow.awaitItem()
            assertTrue(event is ConnectionNavigationEvent.OpenExternalLink)
            assertEquals(callbackUri, (event as ConnectionNavigationEvent.OpenExternalLink).url)

            navigationEventsFlow.expectNoEvents()
            assertFalse(viewModel.isErrorFlow.value)
        }
    }

    @Test
    fun `Given SSL errors when onReceivedSslError is invoked then Error event with message`() = runTest {
        val viewModel = ConnectionViewModel("http://homeassistant.local:8123", keyChainRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)

            suspend fun testError(primaryError: Int?, @StringRes messageRes: Int) {
                viewModel.webViewClient.onReceivedSslError(
                    null,
                    null,
                    primaryError?.let {
                        mockk<SslError> {
                            every { this@mockk.primaryError } returns primaryError
                        }
                    },
                )
                assertError(navigationEventsFlow.awaitItem(), messageRes)
                assertTrue(viewModel.isErrorFlow.value)
            }

            testError(SslError.SSL_DATE_INVALID, commonR.string.webview_error_SSL_DATE_INVALID)
            testError(SslError.SSL_EXPIRED, commonR.string.webview_error_SSL_EXPIRED)
            testError(SslError.SSL_IDMISMATCH, commonR.string.webview_error_SSL_IDMISMATCH)
            testError(SslError.SSL_INVALID, commonR.string.webview_error_SSL_INVALID)
            testError(SslError.SSL_NOTYETVALID, commonR.string.webview_error_SSL_NOTYETVALID)
            testError(SslError.SSL_UNTRUSTED, commonR.string.webview_error_SSL_UNTRUSTED)
            testError(null, commonR.string.error_ssl)
        }
    }

    @Test
    fun `Given HTTP errors when onReceivedHttpError is invoked then Error event with message`() = runTest {
        val rawUrl = "http://homeassistant.local:8123"
        val viewModel = ConnectionViewModel(rawUrl, keyChainRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val urlFlow = viewModel.urlFlow.testIn(backgroundScope)

            assertEquals(null, urlFlow.awaitItem())
            assertNotNull(urlFlow.awaitItem())

            val request = mockk<WebResourceRequest> {
                every { url } returns mockk<Uri> {
                    every { this@mockk.toString() } returns "http://homeassistant.local:8123/auth/authorize?response_type=code&client_id=${AuthenticationService.CLIENT_ID}&redirect_uri=homeassistant://auth-callback"
                }
            }

            val webViewClient = viewModel.webViewClient as TLSWebViewClient

            // Expired cert
            webViewClient.isTLSClientAuthNeeded = true
            webViewClient.isCertificateChainValid = false
            webViewClient.onReceivedHttpError(null, request, null)
            assertError(navigationEventsFlow.awaitItem(), commonR.string.tls_cert_expired_message)
            assertTrue(viewModel.isErrorFlow.value)

            // Cert not found
            webViewClient.isTLSClientAuthNeeded = true
            webViewClient.isCertificateChainValid = true
            webViewClient.onReceivedHttpError(
                null,
                request,
                mockk<WebResourceResponse> {
                    every { statusCode } returns 400
                    every { reasonPhrase } returns "reason"
                },
            )
            assertError(navigationEventsFlow.awaitItem(), commonR.string.tls_cert_not_found_message)
            assertTrue(viewModel.isErrorFlow.value)

            // Generic error
            webViewClient.isTLSClientAuthNeeded = false
            webViewClient.isCertificateChainValid = false
            webViewClient.onReceivedHttpError(
                null,
                request,
                mockk<WebResourceResponse> {
                    every { statusCode } returns 418
                    every { reasonPhrase } returns "I'm a teapot"
                },
            )
            assertError(navigationEventsFlow.awaitItem(), commonR.string.error_http_generic, 418, "I'm a teapot")
            assertTrue(viewModel.isErrorFlow.value)

            // Generic error without reason
            webViewClient.isTLSClientAuthNeeded = false
            webViewClient.isCertificateChainValid = false
            webViewClient.onReceivedHttpError(
                mockk<WebView> {
                    every { context } returns mockk {
                        every { getString(commonR.string.no_description) } returns "No description"
                    }
                },
                request,
                mockk<WebResourceResponse> {
                    every { statusCode } returns 418
                    every { reasonPhrase } returns ""
                },
            )
            assertError(navigationEventsFlow.awaitItem(), commonR.string.error_http_generic, 418, "No description")
            assertTrue(viewModel.isErrorFlow.value)
        }
    }

    @Test
    fun `Given received error when onReceivedError is invoked then Error event with message`() = runTest {
        val rawUrl = "http://homeassistant.local:8123"
        val viewModel = ConnectionViewModel(rawUrl, keyChainRepository)

        turbineScope {
            val navigationEventsFlow = viewModel.navigationEventsFlow.testIn(backgroundScope)
            val urlFlow = viewModel.urlFlow.testIn(backgroundScope)

            assertEquals(null, urlFlow.awaitItem())
            assertNotNull(urlFlow.awaitItem())

            val request = mockk<WebResourceRequest> {
                every { url } returns mockk<Uri> {
                    every { this@mockk.toString() } returns "http://homeassistant.local:8123/auth/authorize?response_type=code&client_id=https://home-assistant.io/android&redirect_uri=homeassistant://auth-callback"
                }
            }

            suspend fun testError(errorCode: Int, @StringRes messageRes: Int, description: String? = null) {
                viewModel.webViewClient.onReceivedError(
                    null,
                    request,
                    mockk<WebResourceError> {
                        every { this@mockk.errorCode } returns errorCode
                        every { this@mockk.description } returns description
                    },
                )
                if (description != null) {
                    assertError(navigationEventsFlow.awaitItem(), messageRes, errorCode, description)
                } else {
                    assertError(navigationEventsFlow.awaitItem(), messageRes)
                }
                assertTrue(viewModel.isErrorFlow.value)
            }

            testError(ERROR_FAILED_SSL_HANDSHAKE, commonR.string.webview_error_FAILED_SSL_HANDSHAKE)
            testError(ERROR_AUTHENTICATION, commonR.string.webview_error_AUTHENTICATION)
            testError(ERROR_PROXY_AUTHENTICATION, commonR.string.webview_error_PROXY_AUTHENTICATION)
            testError(ERROR_UNSUPPORTED_AUTH_SCHEME, commonR.string.webview_error_AUTH_SCHEME)
            testError(ERROR_HOST_LOOKUP, commonR.string.webview_error_HOST_LOOKUP)
            testError(-1, commonR.string.error_http_generic, "description")

            // Generic error without description
            viewModel.webViewClient.onReceivedError(
                mockk<WebView> {
                    every { context } returns mockk {
                        every { getString(commonR.string.no_description) } returns "No description"
                    }
                },
                request,
                mockk<WebResourceError> {
                    every { this@mockk.errorCode } returns -1
                    every { this@mockk.description } returns ""
                },
            )
            assertError(navigationEventsFlow.awaitItem(), commonR.string.error_http_generic, -1, "No description")
            assertTrue(viewModel.isErrorFlow.value)
        }
    }

    private fun assertError(event: ConnectionNavigationEvent, resId: Int, vararg formatArgs: Any?) {
        assertTrue(event is ConnectionNavigationEvent.Error)
        assertEquals(resId, (event as ConnectionNavigationEvent.Error).resId)
        if (formatArgs.isEmpty()) {
            assertTrue(event.formatArgs.isEmpty())
        } else {
            assertEquals(formatArgs.toList(), event.formatArgs.toList())
        }
    }

    private fun mockUriParse() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uriString = firstArg<String>()
            val javaURL = URL(uriString)
            return@answers mockk<Uri> {
                every { this@mockk.toString() } returns uriString
                every { host } returns javaURL.host
            }
        }
    }
}
