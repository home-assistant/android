package io.homeassistant.companion.android.common.data

import io.homeassistant.companion.android.common.data.keychain.ClientCertProvider
import io.homeassistant.companion.android.common.data.keychain.ClientCertificate
import io.homeassistant.companion.android.common.data.keychain.ClientCertificateManager
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.KeyStoreRepository
import io.mockk.mockk
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull

class TLSHelperTest {

    @Test
    fun `Given a client cert selected on a previous run when creating the mTLS key manager then the cert is loaded before being returned`() = runTest {
        val certificate = ClientCertificate(mockk(), arrayOf(USER_CA))
        val keyChain = FakeKeyChainRepository(toLoad = certificate)
        val tlsHelper = tlsHelperOf(keyChain = keyChain)

        val keyManager = tlsHelper.getMTLSKeyManagerForOKHTTP()

        assertEquals(certificate.privateKey, keyManager.getPrivateKey(null))
        assertArrayEquals(certificate.chain, keyManager.getCertificateChain(null))
    }

    @Test
    fun `Given no cert in the keychain when creating the mTLS key manager then it falls back to the keystore`() = runTest {
        val certificate = ClientCertificate(mockk(), arrayOf(USER_CA))
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

        val certificate = ClientCertificate(mockk(), arrayOf(USER_CA))
        keyChain.certificate = certificate

        assertEquals(certificate.privateKey, keyManager.getPrivateKey(null))
        assertArrayEquals(certificate.chain, keyManager.getCertificateChain(null))
    }

    @Test
    fun `Given user and system CAs when filtering then only the user CA is kept`() {
        val store = keyStoreOf(
            "user:1a2b.0" to USER_CA,
            "system:3c4d.0" to SYSTEM_CA,
        )

        val userCaStore = userInstalledCaKeyStore(store)

        assertNotNull(userCaStore)
        assertEquals(1, userCaStore.size())
        assertNotNull(userCaStore.getCertificateAlias(USER_CA))
        assertNull(userCaStore.getCertificateAlias(SYSTEM_CA))
    }

    @Test
    fun `Given several user CAs when filtering then all of them are kept`() {
        val store = keyStoreOf(
            "user:1a2b.0" to USER_CA,
            "user:5e6f.0" to USER_CA,
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
    ): TLSHelper = TLSHelper(ClientCertificateManager(keyChain, keyStore))

    private fun keyStoreOf(vararg entries: Pair<String, X509Certificate>): KeyStore {
        return loadKeyStore().apply {
            entries.forEach { (alias, certificate) -> setCertificateEntry(alias, certificate) }
        }
    }

    companion object {
        private val USER_CA = parseCertificate(
            """
            -----BEGIN CERTIFICATE-----
            MIIBgjCCASmgAwIBAgIUK3+GgehOO2LIwFaGYCqBPSfr9gAwCgYIKoZIzj0EAwIw
            FzEVMBMGA1UEAwwMVGVzdCBVc2VyIENBMB4XDTI2MDYxNTA5MTYxMloXDTM2MDYx
            MjA5MTYxMlowFzEVMBMGA1UEAwwMVGVzdCBVc2VyIENBMFkwEwYHKoZIzj0CAQYI
            KoZIzj0DAQcDQgAEuWKQFJagdiv0NG8ROZUjviXdxMAYZVIPiBRtPKZ4xEdZ3TSy
            7gFe5R6ZRGD65P/554JCVv99Z5UiUdrn3A0Dt6NTMFEwHQYDVR0OBBYEFNTSQ/2z
            oR4mJW8BfJjWudET1UR+MB8GA1UdIwQYMBaAFNTSQ/2zoR4mJW8BfJjWudET1UR+
            MA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDRwAwRAIgWRDzptriiK+L11Em
            KRPW95Y/AAevho3810p++8sAiK8CIBL/aktDSszIPhvngN/YObx9Rsa1X0GhbCur
            WttX27wB
            -----END CERTIFICATE-----
            """.trimIndent(),
        )

        private val SYSTEM_CA = parseCertificate(
            """
            -----BEGIN CERTIFICATE-----
            MIIBhjCCAS2gAwIBAgIULM9Sv5gifek7RA04cDsow+5DUC8wCgYIKoZIzj0EAwIw
            GTEXMBUGA1UEAwwOVGVzdCBTeXN0ZW0gQ0EwHhcNMjYwNjE1MDkxNjEyWhcNMzYw
            NjEyMDkxNjEyWjAZMRcwFQYDVQQDDA5UZXN0IFN5c3RlbSBDQTBZMBMGByqGSM49
            AgEGCCqGSM49AwEHA0IABIoDRdoHkgePxBTI/FrxNtwmlmw2ASjf0nDsXrGzvaCe
            Hmopa+Kk+XidaLBFM7uxs01pYfMwfsVM8EFuK4OOsxajUzBRMB0GA1UdDgQWBBR5
            9mR1Ojk9OqBRX1pZSuaNsSnkuTAfBgNVHSMEGDAWgBR59mR1Ojk9OqBRX1pZSuaN
            sSnkuTAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIFWTMdMCTVkn
            0sVpgRzAofWK821vZgz9M2c9KOWLYrQNAiAAlkVuqpbn6Z+KXNvkMteeAyr0sTwn
            8x92kn3oJNr6WA==
            -----END CERTIFICATE-----
            """.trimIndent(),
        )

        private fun parseCertificate(pem: String): X509Certificate {
            return CertificateFactory.getInstance("X.509")
                .generateCertificate(pem.byteInputStream()) as X509Certificate
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

    override suspend fun store(privateKey: PrivateKey, chain: Array<X509Certificate>): ClientCertificate = ClientCertificate(privateKey, chain).also { certificate = it }
}
