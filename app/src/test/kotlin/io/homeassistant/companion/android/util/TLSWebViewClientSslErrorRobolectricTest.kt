package io.homeassistant.companion.android.util

import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.security.cert.CertificateException
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the API >= 29 path in [TLSWebViewClient.isTlsTrusted] that uses [X509TrustManager]
 * directly. JVM unit tests run with SDK 0, so this separate Robolectric test is required to
 * exercise [SslCertificate.x509Certificate] and the trust manager validation branch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.Q])
class TLSWebViewClientSslErrorRobolectricTest {

    @get:Rule(order = 0)
    var consoleLog = ConsoleLogRule()

    @get:Rule(order = 1)
    val mainDispatcherRule = MainDispatcherJUnit4Rule(UnconfinedTestDispatcher())

    private val keyChainRepository: KeyChainRepository = mockk(relaxed = true)
    private val trustManager: X509TrustManager = mockk(relaxed = true)
    private val okHttpClient: OkHttpClient = mockk(relaxed = true)

    @Test
    fun `Given SSL_UNTRUSTED when X509TrustManager trusts cert then handler proceeds`() = runTest(mainDispatcherRule.testDispatcher) {
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = makeSslError(
            primaryError = SslError.SSL_UNTRUSTED,
            sslErrorUrl = "https://homeassistant.local",
            x509Certificate = makeX509Certificate(),
        )
        var rejectedError: SslError? = null

        val client = testClient(
            validationScope = this,
            onRejected = { rejectedError = it },
        )

        client.onReceivedSslError(null, handler, error)

        verify { handler.proceed() }
        verify(exactly = 0) { handler.cancel() }
        assertNull(rejectedError)
        // Verify "UNKNOWN" is passed as authType, not the public key algorithm
        verify { trustManager.checkServerTrusted(any(), "UNKNOWN") }
    }

    @Test
    fun `Given SSL_UNTRUSTED when X509TrustManager rejects cert but OkHttp succeeds then handler proceeds`() = runTest(mainDispatcherRule.testDispatcher) {
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = makeSslError(
            primaryError = SslError.SSL_UNTRUSTED,
            sslErrorUrl = "https://homeassistant.local",
            x509Certificate = makeX509Certificate(),
        )
        var rejectedError: SslError? = null

        every { trustManager.checkServerTrusted(any(), any()) } throws CertificateException("Leaf-only chain rejected")
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
    fun `Given SSL_UNTRUSTED when both X509TrustManager and OkHttp reject cert then handler cancels and reports error`() = runTest(mainDispatcherRule.testDispatcher) {
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = makeSslError(
            primaryError = SslError.SSL_UNTRUSTED,
            sslErrorUrl = "https://homeassistant.local",
            x509Certificate = makeX509Certificate(),
        )
        var rejectedError: SslError? = null

        every { trustManager.checkServerTrusted(any(), any()) } throws CertificateException("Untrusted")
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
    fun `Given SSL_UNTRUSTED with null x509Certificate on API 29 then handler cancels`() = runTest(mainDispatcherRule.testDispatcher) {
        val handler = mockk<SslErrorHandler>(relaxed = true)
        // SslCertificate.x509Certificate returns null
        val error = makeSslError(
            primaryError = SslError.SSL_UNTRUSTED,
            sslErrorUrl = "https://homeassistant.local",
            x509Certificate = null,
        )
        var rejectedError: SslError? = null

        val client = testClient(
            validationScope = this,
            onRejected = { rejectedError = it },
        )

        client.onReceivedSslError(null, handler, error)

        verify(exactly = 0) { handler.proceed() }
        verify { handler.cancel() }
        assertEquals(error, rejectedError)
    }

    private fun testClient(
        validationScope: CoroutineScope,
        okHttpClient: OkHttpClient = this.okHttpClient,
        onRejected: (SslError?) -> Unit = {},
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
        }
    }

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

    private fun makeSslError(primaryError: Int, sslErrorUrl: String, x509Certificate: X509Certificate?): SslError {
        val sslCertificate = mockk<SslCertificate>(relaxed = true) {
            every { this@mockk.x509Certificate } returns x509Certificate
        }
        return mockk {
            every { this@mockk.primaryError } returns primaryError
            every { this@mockk.url } returns sslErrorUrl
            every { this@mockk.certificate } returns sslCertificate
        }
    }

    private fun makeX509Certificate(): X509Certificate = mockk(relaxed = true) {
        every { publicKey } returns mockk { every { algorithm } returns "RSA" }
    }
}
