package io.homeassistant.companion.android.common.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class CompositeX509ExtendedTrustManagerTest {

    private lateinit var primary: X509ExtendedTrustManager
    private lateinit var fallback: X509ExtendedTrustManager
    private lateinit var composite: CompositeX509ExtendedTrustManager

    @BeforeEach
    fun setUp() {
        primary = mockk(relaxed = true)
        fallback = mockk(relaxed = true)
        composite = CompositeX509ExtendedTrustManager(primary = primary, fallback = fallback)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serverChecks")
    fun `Given primary accepts when checking server certificate then fallback is not consulted`(check: ServerCheck) {
        check.run(composite)

        verify(exactly = 1) { check.run(primary) }
        verify(exactly = 0) { check.run(fallback) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serverChecks")
    fun `Given primary rejects and fallback accepts when checking server certificate then it is trusted`(
        check: ServerCheck,
    ) {
        every { check.run(primary) } throws CertificateException("primary rejected")

        // Must not throw: the fallback (relaxed mock) accepts the certificate.
        check.run(composite)

        verify(exactly = 1) { check.run(fallback) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serverChecks")
    fun `Given both reject when checking server certificate then primary error is thrown with fallback suppressed`(
        check: ServerCheck,
    ) {
        val primaryError = CertificateException("primary rejected")
        val fallbackError = CertificateException("fallback rejected")
        every { check.run(primary) } throws primaryError
        every { check.run(fallback) } throws fallbackError

        val thrown = assertThrows(CertificateException::class.java) { check.run(composite) }

        assertSame(primaryError, thrown)
        assertTrue(thrown.suppressed.contains(fallbackError))
    }

    @Test
    fun `Given primary throws a non-certificate error when checking server then it propagates without fallback`() {
        every { primary.checkServerTrusted(CHAIN, AUTH_TYPE) } throws IllegalStateException("boom")

        assertThrows(IllegalStateException::class.java) { composite.checkServerTrusted(CHAIN, AUTH_TYPE) }

        verify(exactly = 0) { fallback.checkServerTrusted(CHAIN, AUTH_TYPE) }
    }

    @Test
    fun `When checking client certificate then it delegates to the primary only`() {
        composite.checkClientTrusted(CHAIN, AUTH_TYPE)
        composite.checkClientTrusted(CHAIN, AUTH_TYPE, SOCKET)
        composite.checkClientTrusted(CHAIN, AUTH_TYPE, ENGINE)

        verify {
            primary.checkClientTrusted(CHAIN, AUTH_TYPE)
            primary.checkClientTrusted(CHAIN, AUTH_TYPE, SOCKET)
            primary.checkClientTrusted(CHAIN, AUTH_TYPE, ENGINE)
        }
        verify(exactly = 0) {
            fallback.checkClientTrusted(any(), any())
            fallback.checkClientTrusted(any(), any(), any<Socket>())
            fallback.checkClientTrusted(any(), any(), any<SSLEngine>())
        }
    }

    @Test
    fun `When getting accepted issuers then only the primary issuers are returned`() {
        val issuers = arrayOf<X509Certificate>(mockk())
        every { primary.acceptedIssuers } returns issuers

        assertSame(issuers, composite.acceptedIssuers)

        verify(exactly = 0) { fallback.acceptedIssuers }
    }

    /**
     * One of the three `checkServerTrusted` overloads, used to run each test against all of them.
     * [run] calls the overload on any [X509ExtendedTrustManager]: the composite under test, or a mock
     * inside a MockK `every`/`verify` block.
     */
    data class ServerCheck(private val label: String, val run: (X509ExtendedTrustManager) -> Unit) {
        override fun toString(): String = label
    }

    companion object {
        private const val AUTH_TYPE = "RSA"
        private val CHAIN = arrayOf<X509Certificate>(mockk(relaxed = true))
        private val SOCKET = mockk<Socket>(relaxed = true)
        private val ENGINE = mockk<SSLEngine>(relaxed = true)

        @JvmStatic
        fun serverChecks() = listOf(
            ServerCheck("checkServerTrusted(chain, authType)") {
                it.checkServerTrusted(CHAIN, AUTH_TYPE)
            },
            ServerCheck("checkServerTrusted(chain, authType, socket)") {
                it.checkServerTrusted(CHAIN, AUTH_TYPE, SOCKET)
            },
            ServerCheck("checkServerTrusted(chain, authType, engine)") {
                it.checkServerTrusted(CHAIN, AUTH_TYPE, ENGINE)
            },
        )
    }
}
