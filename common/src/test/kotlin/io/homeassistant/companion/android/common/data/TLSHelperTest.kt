package io.homeassistant.companion.android.common.data

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TLSHelperTest {

    @Test
    fun `Given user and system CAs when filtering then only the user CA is kept`() {
        val store = keyStoreOf(
            "user:1a2b.0" to USER_CA,
            "system:3c4d.0" to SYSTEM_CA,
        )

        val userCaStore = userInstalledCaKeyStore(store)

        assertNotNull(userCaStore)
        assertEquals(1, userCaStore!!.size())
        assertNotNull(userCaStore.getCertificateAlias(USER_CA))
        assertNull(userCaStore.getCertificateAlias(SYSTEM_CA))
    }

    @Test
    fun `Given several user CAs when filtering then all of them are kept`() {
        val store = keyStoreOf(
            "user:1a2b.0" to USER_CA,
            "user:5e6f.0" to SYSTEM_CA,
            "system:3c4d.0" to SYSTEM_CA,
        )

        val userCaStore = userInstalledCaKeyStore(store)

        assertNotNull(userCaStore)
        assertEquals(2, userCaStore!!.size())
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

    private fun keyStoreOf(vararg entries: Pair<String, X509Certificate>): KeyStore {
        return KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            entries.forEach { (alias, certificate) -> setCertificateEntry(alias, certificate) }
        }
    }

    companion object {
        private val USER_CA = loadCertificate("/tls/user-ca.b64")
        private val SYSTEM_CA = loadCertificate("/tls/system-ca.b64")

        private fun loadCertificate(resource: String): X509Certificate {
            val base64 = TLSHelperTest::class.java.getResourceAsStream(resource)!!
                .bufferedReader().use { it.readText() }.trim()
            val der = Base64.getDecoder().decode(base64)
            return CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        }
    }
}
