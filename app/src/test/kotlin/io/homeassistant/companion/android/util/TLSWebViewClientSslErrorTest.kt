package io.homeassistant.companion.android.util

import android.net.http.SslCertificate
import android.net.http.SslError
import android.webkit.SslErrorHandler
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class TLSWebViewClientSslErrorTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherJUnit5Extension(UnconfinedTestDispatcher())

    private val keyChainRepository: KeyChainRepository = mockk(relaxed = true)
    private val trustManager: X509TrustManager = mockk(relaxed = true)

    @Test
    fun `Given SSL_UNTRUSTED when TLS validation succeeds then handler proceeds`() = runTest(mainDispatcherExtension.testDispatcher) {
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = makeSslError(primaryError = SslError.SSL_UNTRUSTED, sslErrorUrl = "https://homeassistant.local")
        var rejectedError: SslError? = null

        val client = testClient(
            validationScope = this,
            okHttpClient = makeOkHttpClient(trusted = true),
            onRejected = { rejectedError = it },
        )

        client.onReceivedSslError(null, handler, error)

        verify { handler.proceed() }
        verify(exactly = 0) { handler.cancel() }
        assertNull(rejectedError)
    }

    @Test
    fun `Given SSL_UNTRUSTED when TLS validation fails then handler cancels and reports error`() = runTest(mainDispatcherExtension.testDispatcher) {
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = makeSslError(primaryError = SslError.SSL_UNTRUSTED, sslErrorUrl = "https://homeassistant.local")
        var rejectedError: SslError? = null

        val client = testClient(
            validationScope = this,
            okHttpClient = makeOkHttpClient(trusted = false),
            onRejected = { rejectedError = it },
        )

        client.onReceivedSslError(null, handler, error)

        verify(exactly = 0) { handler.proceed() }
        verify { handler.cancel() }
        assertEquals(error, rejectedError)
    }

    @Test
    fun `Given SSL_EXPIRED when onReceivedSslError then immediately cancels without async validation`() = runTest(mainDispatcherExtension.testDispatcher) {
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = makeSslError(primaryError = SslError.SSL_EXPIRED, sslErrorUrl = "https://homeassistant.local")
        var rejectedError: SslError? = null

        val client = testClient(
            validationScope = this,
            okHttpClient = makeOkHttpClient(trusted = true),
            onRejected = { rejectedError = it },
        )

        client.onReceivedSslError(null, handler, error)

        verify(exactly = 0) { handler.proceed() }
        verify { handler.cancel() }
        assertEquals(error, rejectedError)
    }

    @Test
    fun `Given SSL_IDMISMATCH when onReceivedSslError then immediately cancels without async validation`() = runTest(mainDispatcherExtension.testDispatcher) {
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = makeSslError(primaryError = SslError.SSL_IDMISMATCH, sslErrorUrl = "https://homeassistant.local")
        var rejectedError: SslError? = null

        val client = testClient(
            validationScope = this,
            okHttpClient = makeOkHttpClient(trusted = true),
            onRejected = { rejectedError = it },
        )

        client.onReceivedSslError(null, handler, error)

        verify(exactly = 0) { handler.proceed() }
        verify { handler.cancel() }
        assertEquals(error, rejectedError)
    }

    @Test
    fun `Given SSL_UNTRUSTED with null URL when onReceivedSslError then immediately cancels`() = runTest(mainDispatcherExtension.testDispatcher) {
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = makeSslError(primaryError = SslError.SSL_UNTRUSTED, sslErrorUrl = null)
        var rejectedError: SslError? = null
        var validationAttempted = false

        val tlsCall = mockk<Call>(relaxed = true) {
            every { execute() } answers {
                validationAttempted = true
                mockk(relaxed = true)
            }
        }
        val client = testClient(
            validationScope = this,
            okHttpClient = makeOkHttpClientWithCall(tlsCall),
            onRejected = { rejectedError = it },
        )

        client.onReceivedSslError(null, handler, error)

        verify(exactly = 0) { handler.proceed() }
        verify { handler.cancel() }
        assertFalse(validationAttempted)
        assertEquals(error, rejectedError)
    }

    @Test
    fun `Given SSL_UNTRUSTED when OkHttp throws IOException then handler cancels and onTlsValidationNetworkError called`() = runTest(mainDispatcherExtension.testDispatcher) {
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = makeSslError(primaryError = SslError.SSL_UNTRUSTED, sslErrorUrl = "https://homeassistant.local")
        var rejectedError: SslError? = null
        var networkError: SslError? = null

        val call = mockk<Call>(relaxed = true) {
            every { execute() } throws IOException("Connection refused")
        }
        val client = testClient(
            validationScope = this,
            okHttpClient = makeOkHttpClientWithCall(call),
            onRejected = { rejectedError = it },
            onNetworkError = { networkError = it },
        )

        client.onReceivedSslError(null, handler, error)

        verify(exactly = 0) { handler.proceed() }
        verify { handler.cancel() }
        assertNull(rejectedError)
        assertEquals(error, networkError)
    }

    @Test
    fun `Given null handler and null error when onReceivedSslError then reports error`() = runTest(mainDispatcherExtension.testDispatcher) {
        var rejectedCalled = false
        var rejectedError: SslError? = null
        val client = testClient(
            validationScope = this,
            okHttpClient = makeOkHttpClient(trusted = true),
            onRejected = {
                rejectedCalled = true
                rejectedError = it
            },
        )

        client.onReceivedSslError(null, null, null)

        assertTrue(rejectedCalled)
        assertNull(rejectedError)
    }

    private fun testClient(
        validationScope: CoroutineScope,
        okHttpClient: OkHttpClient,
        onRejected: (SslError?) -> Unit = {},
        onNetworkError: (SslError?) -> Unit = {},
    ): TLSWebViewClient {
        return object : TLSWebViewClient(
            keyChainRepository = keyChainRepository,
            validationScope = validationScope,
            trustManager = trustManager,
            okHttpClient = okHttpClient,
            ioDispatcher = Dispatchers.Unconfined,
        ) {
            override fun onSslErrorRejected(error: SslError?) {
                onRejected(error)
            }

            override fun onTlsValidationNetworkError(error: SslError?) {
                onNetworkError(error)
            }
        }
    }

    /**
     * Creates an OkHttpClient mock that succeeds or fails at the TLS validation call.
     * JVM unit tests run with Build.VERSION.SDK_INT = 0 (< Q), so the OkHttp fallback
     * path is exercised rather than the X509TrustManager path.
     */
    private fun makeOkHttpClient(trusted: Boolean): OkHttpClient {
        val call = mockk<Call>(relaxed = true) {
            if (trusted) {
                every { execute() } returns mockk<Response>(relaxed = true)
            } else {
                every { execute() } throws SSLException("Untrusted certificate")
            }
        }
        return makeOkHttpClientWithCall(call)
    }

    private fun makeOkHttpClientWithCall(call: Call): OkHttpClient {
        val builtClient = mockk<OkHttpClient> {
            every { newCall(any()) } returns call
        }
        val builder = mockk<OkHttpClient.Builder>()
        every { builder.callTimeout(any<java.time.Duration>()) } returns builder
        every { builder.build() } returns builtClient

        return mockk {
            every { newBuilder() } returns builder
        }
    }

    private fun makeSslError(primaryError: Int, sslErrorUrl: String?): SslError {
        val cert = mockk<X509Certificate> {
            every { publicKey } returns mockk { every { algorithm } returns "RSA" }
        }
        val sslCertificate = mockk<SslCertificate>(relaxed = true)
        return mockk {
            every { this@mockk.primaryError } returns primaryError
            every { this@mockk.url } returns sslErrorUrl
            every { this@mockk.certificate } returns sslCertificate
        }
    }
}
