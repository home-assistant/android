package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.security.auth.x500.X500Principal
import okhttp3.HttpUrl
import timber.log.Timber

// SAN (Subject Alternative Name) type codes, see RFC 5280 section 4.2.1.6.
private const val SAN_TYPE_DNS_NAME = 2
private const val SAN_TYPE_IP_ADDRESS = 7

private const val COMMON_NAME_PREFIX = "CN="
private const val WILDCARD_PREFIX = "*."

// A dotted-quad IPv4 literal, and the character set of an IPv6 literal. Both are checked before any
// address parsing so a DNS host name is never handed to the resolver (see [parseIpLiteral]).
private const val IPV4_ADDRESS_LENGTH = 4
private const val IPV6_ADDRESS_LENGTH = 16

private val IPV4_LITERAL = Regex("""\d{1,3}(\.\d{1,3}){3}""")
private val IPV6_LITERAL = Regex("""[0-9A-Fa-f:.]+""")

/**
 * Determines whether the server a connection is being made to requires a client certificate.
 *
 * This is needed during onboarding because the WebView can resume a TLS session established earlier
 * in the same process, in which case the server does not issue a new `CertificateRequest` and
 * [android.webkit.WebViewClient.onReceivedClientCertRequest] never fires, even though the server
 * requires a client certificate.
 *
 * The primary signal is the record of endpoints that have already answered with a
 * `CertificateRequest` (see [PrefsRepository.rememberClientCertRequestedEndpoint]), which is
 * independent of which certificate is loaded: a client identity such as `CN=phone` need not name the
 * Home Assistant host. The loaded certificate is consulted only as a fallback, for endpoints nothing
 * has been observed for yet.
 */
class CheckTLSClientAuthNeededUseCase @Inject constructor(
    private val keyChainRepository: KeyChainRepository,
    private val prefsRepository: PrefsRepository,
) {

    /**
     * Returns `true` when the server behind [targetUrl] is known, or likely, to require a client
     * certificate.
     *
     * A `null` URL, or one that is not `https`, is always `false`: TLS client authentication cannot
     * happen over a plaintext connection.
     *
     * @param targetUrl the URL the WebView is on, after any redirects, so a redirect to another
     * port or scheme is evaluated as it stands.
     */
    suspend operator fun invoke(targetUrl: HttpUrl?): Boolean = when {
        targetUrl == null || !targetUrl.isHttps -> false
        prefsRepository.hasClientCertBeenRequested(targetUrl.host, targetUrl.port) -> true
        else -> {
            val certificate = keyChainRepository.getClientCertProvider().certificate?.chain?.firstOrNull()
            certificate != null && certCoversHost(certificate, targetUrl.host)
        }
    }

    /** Returns whether [cert] is valid for [host], using its SANs or, when there are none, its CN. */
    private fun certCoversHost(cert: X509Certificate, host: String): Boolean {
        val sans = cert.subjectAlternativeNamesOrNull()
        return if (sans.isNullOrEmpty()) {
            cert.commonName()?.let { hostMatchesSan(host, it) } == true
        } else {
            sans.any { san -> sanMatchesHost(san, host) }
        }
    }

    private fun X509Certificate.subjectAlternativeNamesOrNull(): Collection<List<*>>? = try {
        subjectAlternativeNames
    } catch (e: CertificateParsingException) {
        // The subject and the host are deliberately kept out of the log: neither belongs in a
        // crash report, and the exception message can carry both.
        Timber.w(e, "Unable to read the client certificate SANs, falling back to the Common Name")
        null
    }

    /** The Common Name of the Subject DN, or `null` when it is absent or empty. */
    private fun X509Certificate.commonName(): String? = subjectX500Principal
        .getName(X500Principal.RFC2253)
        .splitToSequence(",")
        .map { it.trim() }
        .firstOrNull { it.startsWith(COMMON_NAME_PREFIX, ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    /** Returns whether a single SAN entry [san] covers [host]. */
    private fun sanMatchesHost(san: List<*>, host: String): Boolean {
        if (san.size < 2) return false
        return when (san[0]) {
            SAN_TYPE_DNS_NAME -> (san[1] as? String)?.let { hostMatchesSan(host, it) } == true
            SAN_TYPE_IP_ADDRESS -> hostMatchesIpAddress(host, san[1])
            else -> false
        }
    }

    /**
     * Matches [host] against a DNS SAN value, supporting a single leading wildcard:
     * `*.example.com` covers `foo.example.com` but neither `example.com` nor `foo.bar.example.com`
     * (RFC 2818 section 3.1).
     */
    private fun hostMatchesSan(host: String, san: String): Boolean {
        if (!san.startsWith(WILDCARD_PREFIX)) return host.equals(san, ignoreCase = true)
        val suffix = san.removePrefix("*") // ".example.com"
        val wildcardLabel = host.takeIf { it.endsWith(suffix, ignoreCase = true) }?.dropLast(suffix.length)
        return wildcardLabel != null && wildcardLabel.isNotEmpty() && !wildcardLabel.contains('.')
    }

    /**
     * Compares [host] with an IP address SAN value. Both sides must be IP literals: a DNS name never
     * matches an IP SAN, and resolving one here would put a blocking lookup on the main thread. The
     * SAN value is a `String` on the standard Java API and a `ByteArray` on some providers
     * (e.g. BouncyCastle).
     */
    private fun hostMatchesIpAddress(host: String, sanValue: Any?): Boolean {
        val sanAddress = sanValue.toIpAddressBytes()
        val hostAddress = host.toIpAddressBytes()
        return sanAddress != null && hostAddress != null && sanAddress.contentEquals(hostAddress)
    }

    private fun Any?.toIpAddressBytes(): ByteArray? = when (this) {
        is ByteArray -> takeIf { it.size == IPV4_ADDRESS_LENGTH || it.size == IPV6_ADDRESS_LENGTH }
        is String -> parseIpLiteral(this)
        else -> null
    }

    /**
     * Parses an IP literal into its address bytes, returning `null` for anything that is not one.
     *
     * Only text that is syntactically an IPv4 or IPv6 literal reaches [InetAddress.getByName], and
     * that call is then a pure parse: the JDK does not consult a name service for a literal, so it
     * cannot block on DNS.
     */
    private fun parseIpLiteral(value: String): ByteArray? {
        val isLiteral = IPV4_LITERAL.matches(value) || (value.contains(':') && IPV6_LITERAL.matches(value))
        if (!isLiteral) return null
        return try {
            InetAddress.getByName(value).address
        } catch (_: UnknownHostException) {
            null
        }
    }
}
