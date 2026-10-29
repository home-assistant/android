package io.homeassistant.companion.android.settings.wear

import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.TLSHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedTrustManager
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val LOCALHOST = "localhost"
private const val EMPTY_OK_RESPONSE = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class UserTrustedChainResolverTest {

    private val userCa = HeldCertificate.Builder()
        .commonName("Test User CA")
        .certificateAuthority(0)
        .build()

    private val systemCa = HeldCertificate.Builder()
        .commonName("Test System CA")
        .certificateAuthority(0)
        .build()

    private lateinit var serverSocket: ServerSocket
    private lateinit var serverThread: Thread

    @Before
    fun setUp() {
        val serverCertificate = HeldCertificate.Builder()
            .commonName(LOCALHOST)
            .addSubjectAlternativeName(LOCALHOST)
            .signedBy(userCa)
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(serverCertificate)
            .build()
        serverSocket = serverCertificates.sslContext().serverSocketFactory
            .createServerSocket(0, 1, InetAddress.getByName(LOCALHOST))
        serverThread = thread { serveOneRequest() }
    }

    @After
    fun tearDown() {
        serverSocket.close()
        serverThread.join()
    }

    @Test
    fun `Given a server trusted by the platform when resolving then the user CA is returned`() = runTest {
        val resolver = resolverOf(platformTrustedCa = userCa, userInstalledCa = userCa)

        assertEquals(userCa.certificate, resolver.resolve(serverUrl()))
    }

    @Test
    fun `Given a server trusted only by the user-CA fallback when resolving then the user CA is returned`() = runTest {
        val resolver = resolverOf(platformTrustedCa = systemCa, userInstalledCa = userCa)

        assertEquals(userCa.certificate, resolver.resolve(serverUrl()))
    }

    private fun serverUrl(): String = "https://$LOCALHOST:${serverSocket.localPort}/"

    /** Answers a single request with an empty `200 OK`, completing the TLS handshake on the first read. */
    private fun serveOneRequest() {
        try {
            serverSocket.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (reader.readLine()?.isNotEmpty() == true) {
                    // Skip the request headers until the blank line ending them
                }
                socket.getOutputStream().write(EMPTY_OK_RESPONSE.toByteArray())
                socket.getOutputStream().flush()
            }
        } catch (_: IOException) {
            // Server socket closed by tearDown, or the client dropped the connection
        }
    }

    /**
     * Builds a resolver whose client is set up like the one of [TLSHelper]:
     * - the handshake trusts [platformTrustedCa] and, as the user-CA fallback, [userInstalledCa];
     * - OkHttp cleans the chain with the platform trust manager, trusting only [platformTrustedCa].
     */
    private fun resolverOf(
        platformTrustedCa: HeldCertificate,
        userInstalledCa: HeldCertificate,
    ): UserTrustedChainResolver {
        val handshakeTrustManager = trustManagerOf(platformTrustedCa, userInstalledCa)
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(handshakeTrustManager), null) }
        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManagerOf(platformTrustedCa))
            .build()
        val homeAssistantApis = mockk<HomeAssistantApis> { coEvery { getOkHttpClient() } returns client }
        val tlsHelper = mockk<TLSHelper> {
            every { userInstalledCaTrustManager() } returns trustManagerOf(userInstalledCa)
        }
        return UserTrustedChainResolver(homeAssistantApis, tlsHelper)
    }

    /** Returns a trust manager (Conscrypt under Robolectric) trusting only [cas]. */
    private fun trustManagerOf(vararg cas: HeldCertificate): X509ExtendedTrustManager {
        val trustManager = HandshakeCertificates.Builder()
            .apply { cas.forEach { addTrustedCertificate(it.certificate) } }
            .build()
            .trustManager
        return trustManager as X509ExtendedTrustManager
    }
}
