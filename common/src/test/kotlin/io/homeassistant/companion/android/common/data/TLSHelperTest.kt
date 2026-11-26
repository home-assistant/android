package io.homeassistant.companion.android.common.data

import android.content.Context
import android.content.pm.PackageManager
import io.homeassistant.companion.android.common.data.TestCertificates.PRIVATE_CA
import io.homeassistant.companion.android.common.data.TestCertificates.PRIVATE_CA_LOCALHOST
import io.homeassistant.companion.android.common.data.TestCertificates.SYSTEM_CA
import io.homeassistant.companion.android.common.data.keychain.ClientCertProvider
import io.homeassistant.companion.android.common.data.keychain.ClientCertificate
import io.homeassistant.companion.android.common.data.keychain.ClientCertificateManager
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.KeyStoreRepository
import io.homeassistant.companion.android.common.data.keychain.ServerTrustAnchorProvider
import io.homeassistant.companion.android.testing.unit.LocalTlsServer
import io.mockk.every
import io.mockk.mockk
import java.net.HttpURLConnection.HTTP_OK
import java.net.InetAddress
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull

class TLSHelperTest {

    @Test
    fun `Given a client cert selected on a previous run when creating the mTLS key manager then the cert is loaded before being returned`() = runTest {
        val certificate = ClientCertificate(mockk(), arrayOf(PRIVATE_CA))
        val keyChain = FakeKeyChainRepository(toLoad = certificate)
        val tlsHelper = tlsHelperOf(keyChain = keyChain)

        val keyManager = tlsHelper.getMTLSKeyManagerForOKHTTP()

        assertEquals(certificate.privateKey, keyManager.getPrivateKey(null))
        assertArrayEquals(certificate.chain, keyManager.getCertificateChain(null))
    }

    @Test
    fun `Given no cert in the keychain when creating the mTLS key manager then it falls back to the keystore`() = runTest {
        val certificate = ClientCertificate(mockk(), arrayOf(PRIVATE_CA))
        val keyStore = FakeKeyStoreRepository(toLoad = certificate)
        val tlsHelper = tlsHelperOf(keyStore = keyStore)

        val keyManager = tlsHelper.getMTLSKeyManagerForOKHTTP()

        assertEquals(certificate.privateKey, keyManager.getPrivateKey(null))
        assertArrayEquals(certificate.chain, keyManager.getCertificateChain(null))
    }

    @Test
    fun `Given a client cert selected after the key manager creation when getting the key then it is returned`() = runTest {
        val keyChain = FakeKeyChainRepository()
        val tlsHelper = tlsHelperOf(keyChain = keyChain)
        val keyManager = tlsHelper.getMTLSKeyManagerForOKHTTP()
        assertNull(keyManager.getPrivateKey(null))
        assertNull(keyManager.getCertificateChain(null))

        val certificate = ClientCertificate(mockk(), arrayOf(PRIVATE_CA))
        keyChain.certificate = certificate

        assertEquals(certificate.privateKey, keyManager.getPrivateKey(null))
        assertArrayEquals(certificate.chain, keyManager.getCertificateChain(null))
    }

    @Test
    fun `Given a phone when setting up the client then the server trust anchor is not loaded`() = runTest {
        val keyStore = FakeKeyStoreRepository()
        val tlsHelper = tlsHelperOf(keyStore = keyStore, isWear = false)

        tlsHelper.setupOkHttpClientSSLSocketFactory(OkHttpClient.Builder())

        assertEquals(0, keyStore.serverTrustAnchorProviderRequests)
    }

    @Test
    fun `Given a watch when setting up the client then the server trust anchor is loaded`() = runTest {
        val keyStore = FakeKeyStoreRepository()
        val tlsHelper = tlsHelperOf(keyStore = keyStore, isWear = true)

        tlsHelper.setupOkHttpClientSSLSocketFactory(OkHttpClient.Builder())

        assertEquals(1, keyStore.serverTrustAnchorProviderRequests)
    }

    @Test
    fun `Given a watch with the server CA as trust anchor when connecting to the server then it succeeds`() = runTest {
        val keyStore = FakeKeyStoreRepository().apply { serverTrustAnchor = PRIVATE_CA }
        val client = clientOf(keyStore, isWear = true)

        assertEquals(HTTP_OK, client.connectToNewServer())
    }

    @Test
    fun `Given a watch without a trust anchor when connecting to the server then the handshake fails`() = runTest {
        val client = clientOf(FakeKeyStoreRepository(), isWear = true)

        assertThrows(SSLHandshakeException::class.java) { client.connectToNewServer() }
    }

    @Test
    fun `Given a watch when the trust anchor changes after the client was built then new connections follow it`() = runTest {
        val keyStore = FakeKeyStoreRepository().apply { serverTrustAnchor = PRIVATE_CA }
        val client = clientOf(keyStore, isWear = true)

        assertEquals(HTTP_OK, client.connectToNewServer())

        keyStore.serverTrustAnchor = null
        assertThrows(SSLHandshakeException::class.java) { client.connectToNewServer() }

        keyStore.serverTrustAnchor = SYSTEM_CA
        assertThrows(SSLHandshakeException::class.java) { client.connectToNewServer() }

        keyStore.serverTrustAnchor = PRIVATE_CA
        assertEquals(HTTP_OK, client.connectToNewServer())
    }

    @Test
    fun `Given a phone with the server CA as trust anchor when connecting to the server then the handshake fails`() = runTest {
        val keyStore = FakeKeyStoreRepository().apply { serverTrustAnchor = PRIVATE_CA }
        val client = clientOf(keyStore, isWear = false)

        assertThrows(SSLHandshakeException::class.java) { client.connectToNewServer() }
    }

    @Test
    fun `Given user and system CAs when filtering then only the user CA is kept`() {
        val store = keyStoreOf(
            "user:1a2b.0" to PRIVATE_CA,
            "system:3c4d.0" to SYSTEM_CA,
        )

        val userCaStore = userInstalledCaKeyStore(store)

        assertNotNull(userCaStore)
        assertEquals(1, userCaStore.size())
        assertNotNull(userCaStore.getCertificateAlias(PRIVATE_CA))
        assertNull(userCaStore.getCertificateAlias(SYSTEM_CA))
    }

    @Test
    fun `Given several user CAs when filtering then all of them are kept`() {
        val store = keyStoreOf(
            "user:1a2b.0" to PRIVATE_CA,
            "user:5e6f.0" to PRIVATE_CA,
            "system:3c4d.0" to SYSTEM_CA,
        )

        val userCaStore = userInstalledCaKeyStore(store)

        assertNotNull(userCaStore)
        assertEquals(2, userCaStore.size())
    }

    @Test
    fun `Given only system CAs when filtering then it returns null`() {
        val store = keyStoreOf("system:3c4d.0" to SYSTEM_CA)

        assertNull(userInstalledCaKeyStore(store))
    }

    @Test
    fun `Given an empty store when filtering then it returns null`() {
        assertNull(userInstalledCaKeyStore(keyStoreOf()))
    }

    private fun tlsHelperOf(
        keyChain: KeyChainRepository = FakeKeyChainRepository(),
        keyStore: KeyStoreRepository = FakeKeyStoreRepository(),
        isWear: Boolean = false,
    ): TLSHelper {
        val context = mockk<Context> {
            every { packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) } returns isWear
        }
        return TLSHelper(ClientCertificateManager(keyChain, keyStore), keyStore, context)
    }

    /**
     * Builds a client the way the app does, through [TLSHelper.setupOkHttpClientSSLSocketFactory]. It only
     * resolves to the loopback address [LocalTlsServer] listens on, so a failure is the one of the handshake.
     */
    private suspend fun clientOf(keyStore: KeyStoreRepository, isWear: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder().dns { listOf(InetAddress.getLoopbackAddress()) }
        tlsHelperOf(keyStore = keyStore, isWear = isWear).setupOkHttpClientSSLSocketFactory(builder)
        return builder.build()
    }

    /**
     * Returns the status code of a `GET` on a new server presenting [PRIVATE_CA_LOCALHOST], only trusted
     * through [PRIVATE_CA].
     *
     * A new server is used for every call because a TLS session resumed with a known server skips the
     * certificate check, which would hide a change of the trust anchor.
     */
    private fun OkHttpClient.connectToNewServer(): Int = LocalTlsServer(PRIVATE_CA_LOCALHOST).use { server ->
        newCall(Request.Builder().url(server.url).build()).execute().use { it.code }
    }

    private fun keyStoreOf(vararg entries: Pair<String, X509Certificate>): KeyStore {
        return loadKeyStore().apply {
            entries.forEach { (alias, certificate) -> setCertificateEntry(alias, certificate) }
        }
    }
}

/**
 * Fake that only exposes its cert after [getClientCertProvider] was called, mimicking the real
 * repositories where the cert is unavailable until loaded
 * (https://github.com/home-assistant/android/issues/6119).
 */
private class FakeKeyChainRepository(private val toLoad: ClientCertificate? = null) :
    KeyChainRepository,
    ClientCertProvider {

    override var certificate: ClientCertificate? = null

    override suspend fun getClientCertProvider(): ClientCertProvider {
        if (certificate == null) {
            certificate = toLoad
        }
        return this
    }

    override suspend fun select(alias: String): ClientCertificate? = certificate

    override suspend fun clear() {
        certificate = null
    }
}

/** Same load-gated behavior as [FakeKeyChainRepository] for the keystore contract. */
private class FakeKeyStoreRepository(private val toLoad: ClientCertificate? = null) :
    KeyStoreRepository,
    ClientCertProvider {

    override var certificate: ClientCertificate? = null

    override suspend fun getClientCertProvider(): ClientCertProvider {
        if (certificate == null) {
            certificate = toLoad
        }
        return this
    }

    override suspend fun storeClientCertificate(privateKey: PrivateKey, chain: Array<X509Certificate>): ClientCertificate = ClientCertificate(privateKey, chain).also { certificate = it }

    var serverTrustAnchorProviderRequests = 0
        private set

    /** Can be changed at any time, like the anchor of the real repository. */
    var serverTrustAnchor: X509Certificate? = null

    override suspend fun getServerTrustAnchorProvider(): ServerTrustAnchorProvider {
        serverTrustAnchorProviderRequests++
        return object : ServerTrustAnchorProvider {
            override val certificate: X509Certificate?
                get() = serverTrustAnchor
        }
    }

    override suspend fun storeServerTrustAnchor(certificate: X509Certificate?) = Unit
}
