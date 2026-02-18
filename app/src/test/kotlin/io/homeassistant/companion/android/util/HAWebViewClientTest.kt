package io.homeassistant.companion.android.util

import android.net.http.SslError
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient.ERROR_AUTHENTICATION
import android.webkit.WebViewClient.ERROR_CONNECT
import android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
import android.webkit.WebViewClient.ERROR_HOST_LOOKUP
import android.webkit.WebViewClient.ERROR_PROXY_AUTHENTICATION
import android.webkit.WebViewClient.ERROR_TIMEOUT
import android.webkit.WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME
import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class HAWebViewClientTest {

    private val keyChainRepository: KeyChainRepository = mockk(relaxed = true)
    private val currentUrlFlow = MutableStateFlow<String?>(null)
    private var capturedError: FrontendConnectionError? = null

    private lateinit var webViewClient: HAWebViewClient

    @BeforeEach
    fun setup() {
        capturedError = null
        webViewClient = HAWebViewClient(
            keyChainRepository = keyChainRepository,
            currentUrlFlow = currentUrlFlow,
            onFrontendError = { capturedError = it },
            frontendJsCallback = null,
            onCrash = null,
            onUrlIntercepted = null,
            onPageFinished = null,
        )
    }

    @Test
    fun `Given SSL_DATE_INVALID error when onReceivedSslError then emits AuthenticationError`() {
        testSslError(SslError.SSL_DATE_INVALID, commonR.string.webview_error_SSL_DATE_INVALID)
    }

    @Test
    fun `Given SSL_EXPIRED error when onReceivedSslError then emits AuthenticationError`() {
        testSslError(SslError.SSL_EXPIRED, commonR.string.webview_error_SSL_EXPIRED)
    }

    @Test
    fun `Given SSL_IDMISMATCH error when onReceivedSslError then emits AuthenticationError`() {
        testSslError(SslError.SSL_IDMISMATCH, commonR.string.webview_error_SSL_IDMISMATCH)
    }

    @Test
    fun `Given SSL_INVALID error when onReceivedSslError then emits AuthenticationError`() {
        testSslError(SslError.SSL_INVALID, commonR.string.webview_error_SSL_INVALID)
    }

    @Test
    fun `Given SSL_NOTYETVALID error when onReceivedSslError then emits AuthenticationError`() {
        testSslError(SslError.SSL_NOTYETVALID, commonR.string.webview_error_SSL_NOTYETVALID)
    }

    @Test
    fun `Given SSL_UNTRUSTED error when onReceivedSslError then emits AuthenticationError`() {
        testSslError(SslError.SSL_UNTRUSTED, commonR.string.webview_error_SSL_UNTRUSTED)
    }

    @Test
    fun `Given null SSL error when onReceivedSslError then emits generic SSL error`() {
        webViewClient.onReceivedSslError(null, null, null)

        assertNotNull(capturedError)
        assertTrue(capturedError is FrontendConnectionError.AuthenticationError)
        assertEquals(commonR.string.error_ssl, capturedError?.message)
    }

    private fun testSslError(primaryError: Int, @StringRes expectedMessageRes: Int) {
        val details = "SSL Error: $primaryError"
        val sslError = mockk<SslError> {
            every { this@mockk.primaryError } returns primaryError
            every { this@mockk.toString() } returns details
        }

        webViewClient.onReceivedSslError(null, null, sslError)

        assertFrontendError<FrontendConnectionError.AuthenticationError>(expectedMessageRes, details, SslError::class)
    }

    @Test
    fun `Given expired TLS cert when onReceivedHttpError then emits AuthenticationError`() {
        val webView = mockWebView()
        val currentUrl = "http://homeassistant.local:8123/auth/authorize"
        currentUrlFlow.value = currentUrl
        val request = mockRequest(currentUrl)

        webViewClient.isTLSClientAuthNeeded = true
        webViewClient.isCertificateChainValid = false

        webViewClient.onReceivedHttpError(webView, request, null)

        assertFrontendError<FrontendConnectionError.AuthenticationError>(
            commonR.string.tls_cert_expired_message,
            errorDetails(null, "No description"),
            WebResourceResponse::class,
        )
    }

    @Test
    fun `Given TLS cert not found when onReceivedHttpError then emits AuthenticationError`() {
        val webView = mockWebView()
        val currentUrl = "http://homeassistant.local:8123/auth/authorize"
        currentUrlFlow.value = currentUrl
        val request = mockRequest(currentUrl)
        val response = mockk<WebResourceResponse> {
            every { statusCode } returns 400
            every { reasonPhrase } returns "Bad Request"
        }

        webViewClient.isTLSClientAuthNeeded = true
        webViewClient.isCertificateChainValid = true

        webViewClient.onReceivedHttpError(webView, request, response)

        assertFrontendError<FrontendConnectionError.AuthenticationError>(
            commonR.string.tls_cert_not_found_message,
            errorDetails(400, "Bad Request"),
            WebResourceResponse::class,
        )
    }

    @Test
    fun `Given generic HTTP error when onReceivedHttpError then emits UnknownError`() {
        val webView = mockWebView()
        val currentUrl = "http://homeassistant.local:8123/auth/authorize"
        currentUrlFlow.value = currentUrl
        val request = mockRequest(currentUrl)
        val response = mockk<WebResourceResponse> {
            every { statusCode } returns 418
            every { reasonPhrase } returns "I'm a teapot"
        }

        webViewClient.isTLSClientAuthNeeded = false

        webViewClient.onReceivedHttpError(webView, request, response)

        assertFrontendError<FrontendConnectionError.UnknownError>(
            commonR.string.connection_error_unknown_error,
            errorDetails(418, "I'm a teapot"),
            WebResourceResponse::class,
        )
    }

    @Test
    fun `Given HTTP error without reason when onReceivedHttpError then emits UnknownError with no description`() {
        val webView = mockWebView()
        val currentUrl = "http://homeassistant.local:8123/auth/authorize"
        currentUrlFlow.value = currentUrl
        val request = mockRequest(currentUrl)
        val response = mockk<WebResourceResponse> {
            every { statusCode } returns 500
            every { reasonPhrase } returns ""
        }

        webViewClient.isTLSClientAuthNeeded = false

        webViewClient.onReceivedHttpError(webView, request, response)

        assertFrontendError<FrontendConnectionError.UnknownError>(
            commonR.string.connection_error_unknown_error,
            errorDetails(500, "No description"),
            WebResourceResponse::class,
        )
    }

    @Test
    fun `Given HTTP error for different URL when onReceivedHttpError then does not emit error`() {
        val webView = mockWebView()
        currentUrlFlow.value = "http://homeassistant.local:8123/auth/authorize"
        val request = mockRequest("http://different-url.com/something")
        val response = mockk<WebResourceResponse> {
            every { statusCode } returns 500
            every { reasonPhrase } returns "Server Error"
        }

        webViewClient.onReceivedHttpError(webView, request, response)

        assertEquals(null, capturedError)
    }

    @Test
    fun `Given ERROR_FAILED_SSL_HANDSHAKE when onReceivedError then emits AuthenticationError`() {
        testReceivedError(
            errorCode = ERROR_FAILED_SSL_HANDSHAKE,
            expectedMessageRes = commonR.string.webview_error_FAILED_SSL_HANDSHAKE,
            expectedErrorType = FrontendConnectionError.AuthenticationError::class,
        )
    }

    @Test
    fun `Given ERROR_AUTHENTICATION when onReceivedError then emits AuthenticationError`() {
        testReceivedError(
            errorCode = ERROR_AUTHENTICATION,
            expectedMessageRes = commonR.string.webview_error_AUTHENTICATION,
            expectedErrorType = FrontendConnectionError.AuthenticationError::class,
        )
    }

    @Test
    fun `Given ERROR_PROXY_AUTHENTICATION when onReceivedError then emits AuthenticationError`() {
        testReceivedError(
            errorCode = ERROR_PROXY_AUTHENTICATION,
            expectedMessageRes = commonR.string.webview_error_PROXY_AUTHENTICATION,
            expectedErrorType = FrontendConnectionError.AuthenticationError::class,
        )
    }

    @Test
    fun `Given ERROR_UNSUPPORTED_AUTH_SCHEME when onReceivedError then emits AuthenticationError`() {
        testReceivedError(
            errorCode = ERROR_UNSUPPORTED_AUTH_SCHEME,
            expectedMessageRes = commonR.string.webview_error_AUTH_SCHEME,
            expectedErrorType = FrontendConnectionError.AuthenticationError::class,
        )
    }

    @Test
    fun `Given ERROR_HOST_LOOKUP when onReceivedError then emits UnreachableError`() {
        testReceivedError(
            errorCode = ERROR_HOST_LOOKUP,
            expectedMessageRes = commonR.string.webview_error_HOST_LOOKUP,
            expectedErrorType = FrontendConnectionError.UnreachableError::class,
        )
    }

    @Test
    fun `Given ERROR_TIMEOUT when onReceivedError then emits UnreachableError`() {
        testReceivedError(
            errorCode = ERROR_TIMEOUT,
            expectedMessageRes = commonR.string.webview_error_TIMEOUT,
            expectedErrorType = FrontendConnectionError.UnreachableError::class,
        )
    }

    @Test
    fun `Given ERROR_CONNECT when onReceivedError then emits UnreachableError`() {
        testReceivedError(
            errorCode = ERROR_CONNECT,
            expectedMessageRes = commonR.string.webview_error_CONNECT,
            expectedErrorType = FrontendConnectionError.UnreachableError::class,
        )
    }

    @Test
    fun `Given unknown error code when onReceivedError then emits UnknownError`() {
        testReceivedError(
            errorCode = -999,
            expectedMessageRes = commonR.string.connection_error_unknown_error,
            expectedErrorType = FrontendConnectionError.UnknownError::class,
        )
    }

    @Test
    fun `Given error without description when onReceivedError then emits error with no description`() {
        val webView = mockWebView()
        val currentUrl = "http://homeassistant.local:8123/auth/authorize"
        currentUrlFlow.value = currentUrl
        val request = mockRequest(currentUrl)
        val error = mockk<WebResourceError> {
            every { errorCode } returns -1
            every { description } returns ""
        }

        webViewClient.onReceivedError(webView, request, error)

        assertFrontendError<FrontendConnectionError.UnknownError>(
            commonR.string.connection_error_unknown_error,
            errorDetails(-1, "No description"),
            WebResourceError::class,
        )
    }

    @Test
    fun `Given error for different URL when onReceivedError then does not emit error`() {
        val webView = mockWebView()
        currentUrlFlow.value = "http://homeassistant.local:8123/auth/authorize"
        val request = mockRequest("http://different-url.com/something")
        val error = mockk<WebResourceError> {
            every { errorCode } returns ERROR_HOST_LOOKUP
            every { description } returns "Host lookup failed"
        }

        webViewClient.onReceivedError(webView, request, error)

        assertEquals(null, capturedError)
    }

    private fun testReceivedError(
        errorCode: Int,
        @StringRes expectedMessageRes: Int,
        expectedErrorType: KClass<out FrontendConnectionError>,
    ) {
        val webView = mockWebView()
        val currentUrl = "http://homeassistant.local:8123/auth/authorize"
        currentUrlFlow.value = currentUrl
        val request = mockRequest(currentUrl)
        val description = "Error description"
        val error = mockk<WebResourceError> {
            every { this@mockk.errorCode } returns errorCode
            every { this@mockk.description } returns description
        }

        webViewClient.onReceivedError(webView, request, error)

        assertNotNull(capturedError)
        assertTrue(expectedErrorType.isInstance(capturedError))
        assertEquals(expectedMessageRes, capturedError?.message)
        assertEquals(errorDetails(errorCode, description), capturedError?.errorDetails)
        assertEquals(WebResourceError::class.toString(), capturedError?.rawErrorType)
    }

    private inline fun <reified T : FrontendConnectionError> assertFrontendError(
        @StringRes messageId: Int,
        errorDetails: String?,
        errorClass: KClass<*>,
    ) {
        assertNotNull(capturedError)
        assertTrue(capturedError is T, "Expected ${T::class.simpleName} but got ${capturedError?.let { it::class.simpleName }}")
        assertEquals(messageId, capturedError?.message)
        assertEquals(errorDetails, capturedError?.errorDetails)
        assertEquals(errorClass.toString(), capturedError?.rawErrorType)
    }

    private fun errorDetails(code: Int?, description: String?): String {
        return "Status Code: ${code}\nDescription: $description"
    }

    private fun mockWebView(): WebView {
        return mockk {
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

    private fun mockRequest(url: String) = mockk<android.webkit.WebResourceRequest> {
        every { this@mockk.url } returns mockk {
            every { this@mockk.toString() } returns url
        }
    }
}
