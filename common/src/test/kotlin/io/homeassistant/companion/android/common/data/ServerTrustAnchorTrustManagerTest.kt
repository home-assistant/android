package io.homeassistant.companion.android.common.data

import io.homeassistant.companion.android.common.data.TestCertificates.PRIVATE_CA
import io.homeassistant.companion.android.common.data.TestCertificates.PRIVATE_CA_LEAF
import io.homeassistant.companion.android.common.data.TestCertificates.SELF_SIGNED_LEAF
import io.homeassistant.companion.android.common.data.keychain.ServerTrustAnchorProvider
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ServerTrustAnchorTrustManagerTest {

    private val anchor = FakeServerTrustAnchorProvider()
    private val trustManager = ServerTrustAnchorTrustManager(anchor)

    @Test
    fun `Given no anchor when checking a server then it is rejected`() {
        assertThrows(CertificateException::class.java) { trustManager.checkServer(PRIVATE_CA_LEAF) }
    }

    @Test
    fun `Given a CA anchor stored after the trust manager creation when checking a server signed by it then it is trusted until deleted`() {
        assertThrows(CertificateException::class.java) { trustManager.checkServer(PRIVATE_CA_LEAF) }

        anchor.certificate = PRIVATE_CA
        assertDoesNotThrow { trustManager.checkServer(PRIVATE_CA_LEAF) }
        assertDoesNotThrow { trustManager.checkServer(PRIVATE_CA_LEAF, PRIVATE_CA) }

        anchor.certificate = null
        assertThrows(CertificateException::class.java) { trustManager.checkServer(PRIVATE_CA_LEAF) }
    }

    @Test
    fun `Given a self-signed server certificate as anchor when checking it then it is trusted until replaced`() {
        anchor.certificate = SELF_SIGNED_LEAF

        assertDoesNotThrow { trustManager.checkServer(SELF_SIGNED_LEAF) }
        assertThrows(CertificateException::class.java) { trustManager.checkServer(PRIVATE_CA_LEAF) }

        anchor.certificate = PRIVATE_CA
        assertThrows(CertificateException::class.java) { trustManager.checkServer(SELF_SIGNED_LEAF) }
    }

    @Test
    fun `Given an anchor when checking a client then it is rejected`() {
        anchor.certificate = PRIVATE_CA

        assertThrows(CertificateException::class.java) { trustManager.checkClientTrusted(arrayOf(PRIVATE_CA_LEAF), AUTH_TYPE) }
    }

    private fun ServerTrustAnchorTrustManager.checkServer(vararg chain: X509Certificate) {
        checkServerTrusted(arrayOf(*chain), AUTH_TYPE)
    }

    private class FakeServerTrustAnchorProvider : ServerTrustAnchorProvider {
        override var certificate: X509Certificate? = null
    }

    private companion object {
        const val AUTH_TYPE = "ECDHE_ECDSA"
    }
}
