package io.homeassistant.companion.android.settings.wear

import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.TLSHelper
import io.homeassistant.companion.android.testing.unit.LocalTlsServer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedTrustManager
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

    private lateinit var server: LocalTlsServer

    @Before
    fun setUp() {
        val serverCertificate = HeldCertificate.Builder()
            .commonName(LOCALHOST)
            .addSubjectAlternativeName(LOCALHOST)
            .signedBy(userCa)
            .build()
        server = LocalTlsServer(serverCertificate)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `Given a server trusted by the platform when resolving then the user CA is returned`() = runTest {
        val resolver = resolverOf(platformTrustedCa = userCa, userInstalledCa = userCa)

        assertEquals(userCa.certificate, resolver.resolve(server.url))
    }

    @Test
    fun `Given a server trusted only by the user-CA fallback when resolving then the user CA is returned`() = runTest {
        val resolver = resolverOf(platformTrustedCa = systemCa, userInstalledCa = userCa)

        assertEquals(userCa.certificate, resolver.resolve(server.url))
    }

    @Test
    fun `Given a server not anchored on a user-installed CA when resolving then no anchor is returned`() = runTest {
        val resolver = resolverOf(platformTrustedCa = userCa, userInstalledCa = systemCa)

        assertEquals(null, resolver.resolve(server.url))
    }

    @Test
    fun `Given non-HTTPS or invalid URLs when resolving then no anchor is returned`() = runTest {
        val resolver = UserTrustedChainResolver(mockk(), mockk())

        assertEquals(null, resolver.resolve("http://$LOCALHOST/"))
        assertEquals(null, resolver.resolve("not a URL"))
    }

    @Test
    fun `Given a connection failure when resolving then no anchor is returned`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { throw IOException("Connection failed") }
            .build()
        val homeAssistantApis = mockk<HomeAssistantApis> { coEvery { getOkHttpClient() } returns client }
        val resolver = UserTrustedChainResolver(homeAssistantApis, mockk())

        assertEquals(null, resolver.resolve(server.url))
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
