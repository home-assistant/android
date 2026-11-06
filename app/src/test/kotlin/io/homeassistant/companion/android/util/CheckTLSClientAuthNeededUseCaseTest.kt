package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.keychain.ClientCertProvider
import io.homeassistant.companion.android.common.data.keychain.ClientCertificate
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.net.InetAddress
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.util.stream.Stream
import javax.security.auth.x500.X500Principal
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Tests for [CheckTLSClientAuthNeededUseCase].
 *
 * The loaded client identity is a mock certificate; the record of endpoints that asked for a client
 * certificate lives in [PrefsRepository] and is stubbed per case.
 */
class CheckTLSClientAuthNeededUseCaseTest {

    private val keyChainRepository: KeyChainRepository = mockk(relaxed = true)
    private val prefsRepository: PrefsRepository = mockk(relaxed = true)

    private val useCase = CheckTLSClientAuthNeededUseCase(keyChainRepository, prefsRepository)

    private fun stubCertificate(
        sans: List<List<Any>>?,
        commonName: String? = "client",
        parsingFails: Boolean = false,
    ) {
        val cert = mockk<X509Certificate> {
            if (parsingFails) {
                every { subjectAlternativeNames } throws CertificateParsingException("unparseable")
            } else {
                every { subjectAlternativeNames } returns sans
            }
            every { subjectX500Principal } returns X500Principal("CN=$commonName")
        }
        val provider = mockk<ClientCertProvider> {
            every { certificate } returns ClientCertificate(mockk(), arrayOf(cert))
        }
        coEvery { keyChainRepository.getClientCertProvider() } returns provider
    }

    private fun stubNoCertificate() {
        val provider = mockk<ClientCertProvider> {
            every { certificate } returns null
        }
        coEvery { keyChainRepository.getClientCertProvider() } returns provider
    }

    private fun stubObserved(host: String, port: Int) {
        coEvery { prefsRepository.hasClientCertBeenRequested(host, port) } returns true
    }

    /** A combination of target URL, loaded identity, endpoint record and expected result. */
    data class Case(
        val description: String,
        val url: String,
        val observedEndpoint: Pair<String, Int>?,
        val sans: List<List<Any>>?,
        val commonName: String?,
        val certificateLoaded: Boolean,
        val expected: Boolean,
    ) {
        override fun toString(): String = description
    }

    companion object {
        private const val HOST = "homeassistant.local"
        private const val PORT = 8123
        private const val HTTPS = "https://$HOST:$PORT"
        private const val HTTP = "http://$HOST:$PORT"

        @JvmStatic
        fun cases(): Stream<Case> = Stream.of(
            Case(
                description = "endpoint observed earlier, client identity does not name the host",
                url = HTTPS,
                observedEndpoint = HOST to PORT,
                sans = null,
                commonName = "phone",
                certificateLoaded = true,
                expected = true,
            ),
            Case(
                description = "endpoint observed earlier, no certificate loaded at check time",
                url = HTTPS,
                observedEndpoint = HOST to PORT,
                sans = null,
                commonName = null,
                certificateLoaded = false,
                expected = true,
            ),
            Case(
                description = "nothing observed, cert DNS SAN matches the host",
                url = HTTPS,
                observedEndpoint = null,
                sans = listOf(listOf(2, HOST)),
                commonName = "client",
                certificateLoaded = true,
                expected = true,
            ),
            Case(
                description = "nothing observed, cert wildcard SAN covers the host",
                url = "https://ha.example.com:$PORT",
                observedEndpoint = null,
                sans = listOf(listOf(2, "*.example.com")),
                commonName = "client",
                certificateLoaded = true,
                expected = true,
            ),
            Case(
                description = "nothing observed, cert SAN is for another host",
                url = HTTPS,
                observedEndpoint = null,
                sans = listOf(listOf(2, "other-server.example.com")),
                commonName = "client",
                certificateLoaded = true,
                expected = false,
            ),
            Case(
                description = "nothing observed, no certificate loaded",
                url = HTTPS,
                observedEndpoint = null,
                sans = null,
                commonName = null,
                certificateLoaded = false,
                expected = false,
            ),
            Case(
                description = "observed on a different port of the same host",
                url = "https://$HOST:8443",
                observedEndpoint = HOST to PORT,
                sans = null,
                commonName = "phone",
                certificateLoaded = true,
                expected = false,
            ),
            Case(
                description = "plain http cannot use client auth even when the cert covers the host",
                url = HTTP,
                observedEndpoint = null,
                sans = listOf(listOf(2, HOST)),
                commonName = "client",
                certificateLoaded = true,
                expected = false,
            ),
            Case(
                description = "plain http is rejected even when the endpoint was observed",
                url = HTTP,
                observedEndpoint = HOST to PORT,
                sans = null,
                commonName = null,
                certificateLoaded = false,
                expected = false,
            ),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `Given a target url when checking then the result follows the endpoint record and the loaded certificate`(
        case: Case,
    ) = runTest {
        coEvery { prefsRepository.hasClientCertBeenRequested(any(), any()) } returns false
        case.observedEndpoint?.let { (host, port) -> stubObserved(host, port) }
        if (case.certificateLoaded) {
            stubCertificate(case.sans, case.commonName ?: "client")
        } else {
            stubNoCertificate()
        }

        assertEquals(case.expected, useCase(case.url.toHttpUrl()), case.description)
    }

    @Test
    fun `Given no target url when checking then returns false`() = runTest {
        stubCertificate(listOf(listOf(2, "homeassistant.local")))
        stubObserved("homeassistant.local", 8123)

        assertFalse(useCase(null))
    }

    @Test
    fun `Given a DNS host and an IP SAN when checking then the IP SAN does not match and no lookup is attempted`() = runTest {
        stubCertificate(listOf(listOf(7, "203.0.113.10")))

        // A host name must never be resolved to compare against an IP SAN: only literals can match.
        assertFalse(useCase("https://homeassistant.local:8123".toHttpUrl()))
    }

    @Test
    fun `Given an IP literal host and the same IP SAN as text when checking then returns true`() = runTest {
        stubCertificate(listOf(listOf(7, "203.0.113.10")))

        assertTrue(useCase("https://203.0.113.10:8123".toHttpUrl()))
    }

    @Test
    fun `Given an IPv6 literal host and a byte array IP SAN when checking then returns true`() = runTest {
        // ByteArray SANs are what providers such as BouncyCastle return.
        val sanBytes = InetAddress.getByName("2001:db8::1").address
        stubCertificate(listOf(listOf(7, sanBytes)))

        assertTrue(useCase("https://[2001:db8:0:0:0:0:0:1]:8123".toHttpUrl()))
    }

    @Test
    fun `Given a different IP literal when checking then returns false`() = runTest {
        stubCertificate(listOf(listOf(7, "203.0.113.10")))

        assertFalse(useCase("https://203.0.113.11:8123".toHttpUrl()))
    }

    @Test
    fun `Given the SANs cannot be parsed when checking then it falls back to the Common Name`() = runTest {
        stubCertificate(sans = null, commonName = "homeassistant.local", parsingFails = true)

        assertTrue(useCase("https://homeassistant.local:8123".toHttpUrl()))
    }

    @Test
    fun `Given the SANs cannot be parsed and the Common Name differs when checking then returns false`() = runTest {
        stubCertificate(sans = null, commonName = "phone", parsingFails = true)

        assertFalse(useCase("https://homeassistant.local:8123".toHttpUrl()))
    }

    @Test
    fun `Given cert with DNS SAN in mixed case when checking then returns true`() = runTest {
        stubCertificate(listOf(listOf(2, "HomeAssistant.Local")))

        assertTrue(useCase("https://homeassistant.local:8123".toHttpUrl()))
    }

    @Test
    fun `Given cert with a malformed SAN entry when checking then returns false`() = runTest {
        // A SAN entry must have at least a type and a value (RFC 5280 section 4.2.1.6).
        stubCertificate(listOf(listOf(1)))

        assertFalse(useCase("https://homeassistant.local:8123".toHttpUrl()))
    }

    @Test
    fun `Given cert with a SAN of an unrelated type when checking then returns false`() = runTest {
        // Type 1 is rfc822Name (email), which is irrelevant for hostname matching.
        stubCertificate(listOf(listOf(1, "someone@example.com")))

        assertFalse(useCase("https://homeassistant.local:8123".toHttpUrl()))
    }

    @Test
    fun `Given the endpoint record and an unparseable certificate when checking then the record still wins`() = runTest {
        stubCertificate(sans = null, commonName = "phone", parsingFails = true)
        stubObserved("homeassistant.local", 8123)

        assertTrue(useCase("https://homeassistant.local:8123".toHttpUrl()))
    }
}
