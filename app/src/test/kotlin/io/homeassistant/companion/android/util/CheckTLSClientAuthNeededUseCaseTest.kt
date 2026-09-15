package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.keychain.ClientCertProvider
import io.homeassistant.companion.android.common.data.keychain.ClientCertificate
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.net.InetAddress
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [CheckTLSClientAuthNeededUseCase].
 */
class CheckTLSClientAuthNeededUseCaseTest {

    private val keyChainRepository: KeyChainRepository = mockk(relaxed = true)

    private val useCase = CheckTLSClientAuthNeededUseCase(keyChainRepository)

    private fun stubLoadedCertificate(cert: X509Certificate?) {
        val clientCertificate = cert?.let { ClientCertificate(mockk(), arrayOf(it)) }
        val provider = mockk<ClientCertProvider> {
            every { certificate } returns clientCertificate
        }
        coEvery { keyChainRepository.getClientCertProvider() } returns provider
    }

    @Test
    fun `Given cert with exact DNS SAN matching target host when checking then returns true`() = runTest {
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(2, "homeassistant.local"))
        }
        stubLoadedCertificate(cert)

        assertTrue(useCase("homeassistant.local"))
    }

    @Test
    fun `Given cert with wildcard DNS SAN matching target host when checking then returns true`() = runTest {
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(2, "*.example.com"))
        }
        stubLoadedCertificate(cert)

        assertTrue(useCase("ha.example.com"))
    }

    @Test
    fun `Given cert with DNS SAN for a different host when checking then returns false`() = runTest {
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(2, "other-server.example.com"))
        }
        stubLoadedCertificate(cert)

        assertFalse(useCase("homeassistant.local"))
    }

    @Test
    fun `Given no certificate chain in memory when checking then returns false`() = runTest {
        stubLoadedCertificate(null)

        assertFalse(useCase("homeassistant.local"))
    }

    @Test
    fun `Given null or blank target host when checking then returns false`() = runTest {
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(2, "homeassistant.local"))
        }
        stubLoadedCertificate(cert)

        assertFalse(useCase(null))
        assertFalse(useCase(""))
    }

    @Test
    fun `Given cert with CN matching target host and no SANs when checking then returns true`() = runTest {
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns null
            every { subjectX500Principal } returns mockk {
                every { getName("RFC2253") } returns "CN=homeassistant.local,O=Home Assistant"
            }
        }
        stubLoadedCertificate(cert)

        assertTrue(useCase("homeassistant.local"))
    }

    @Test
    fun `Given cert with only an empty CN when checking then returns false`() = runTest {
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns null
            every { subjectX500Principal } returns mockk {
                every { getName("RFC2253") } returns "CN=,O=Home Assistant"
            }
        }
        stubLoadedCertificate(cert)

        assertFalse(useCase("homeassistant.local"))
    }

    @Test
    fun `Given cert with wildcard SAN that does not cover a multi-label subdomain when checking then returns false`() = runTest {
        val cert = mockk<X509Certificate> {
            // *.example.com covers foo.example.com but not foo.bar.example.com
            every { subjectAlternativeNames } returns listOf(listOf(2, "*.example.com"))
        }
        stubLoadedCertificate(cert)

        assertFalse(useCase("foo.bar.example.com"))
    }

    @Test
    fun `Given cert with wildcard SAN that does not cover apex domain when checking then returns false`() = runTest {
        val cert = mockk<X509Certificate> {
            // *.example.com covers foo.example.com but not example.com itself (RFC 2818 §3.1)
            every { subjectAlternativeNames } returns listOf(listOf(2, "*.example.com"))
        }
        stubLoadedCertificate(cert)

        assertFalse(useCase("example.com"))
    }

    @Test
    fun `Given cert with non-matching SANs and matching CN when checking then returns false`() = runTest {
        // When SANs are present, the CN must not be used as fallback even if it would match —
        // this is the standard behaviour defined in RFC 2818 §3.1.
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(2, "other-server.example.com"))
            every { subjectX500Principal } returns mockk {
                every { getName("RFC2253") } returns "CN=homeassistant.local,O=Home Assistant"
            }
        }
        stubLoadedCertificate(cert)

        assertFalse(useCase("homeassistant.local"))
    }

    @Test
    fun `Given cert with IP address SAN as ByteArray matching target host when checking then returns true`() = runTest {
        // Some providers (e.g. BouncyCastle) return iPAddress (type 7) as a ByteArray.
        val ipBytes = InetAddress.getByName("192.168.1.100").address
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(7, ipBytes))
        }
        stubLoadedCertificate(cert)

        assertTrue(useCase("192.168.1.100"))
    }

    @Test
    fun `Given cert with IP address SAN as String matching target host when checking then returns true`() = runTest {
        // The standard Java X.509 API returns iPAddress (type 7) as a String (dotted-quad or colon-hex).
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(7, "192.168.1.100"))
        }
        stubLoadedCertificate(cert)

        assertTrue(useCase("192.168.1.100"))
    }

    @Test
    fun `Given cert with IPv6 SAN in expanded form matching target host in compressed form when checking then returns true`() = runTest {
        // InetAddress equality normalizes different textual forms of the same IPv6 address.
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(7, "0:0:0:0:0:0:0:1"))
        }
        stubLoadedCertificate(cert)

        assertTrue(useCase("::1"))
    }

    @Test
    fun `Given cert with DNS SAN in mixed case when checking then returns true`() = runTest {
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(2, "HomeAssistant.Local"))
        }
        stubLoadedCertificate(cert)

        assertTrue(useCase("homeassistant.local"))
    }

    @Test
    fun `Given cert whose subjectAlternativeNames throws CertificateParsingException when checking then falls back to CN matching`() = runTest {
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } throws CertificateParsingException("bad extension")
            every { subjectX500Principal } returns mockk {
                every { getName("RFC2253") } returns "CN=homeassistant.local,O=Home Assistant"
            }
        }
        stubLoadedCertificate(cert)

        assertTrue(useCase("homeassistant.local"))
    }

    @Test
    fun `Given cert with a malformed SAN entry when checking then returns false`() = runTest {
        // A SAN entry must have at least a type and a value (RFC 5280 section 4.2.1.6).
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(2))
        }
        stubLoadedCertificate(cert)

        assertFalse(useCase("homeassistant.local"))
    }

    @Test
    fun `Given cert with a SAN of an unrelated type when checking then returns false`() = runTest {
        // Type 1 is rfc822Name (email), which is irrelevant for hostname matching.
        val cert = mockk<X509Certificate> {
            every { subjectAlternativeNames } returns listOf(listOf(1, "someone@example.com"))
        }
        stubLoadedCertificate(cert)

        assertFalse(useCase("homeassistant.local"))
    }
}
