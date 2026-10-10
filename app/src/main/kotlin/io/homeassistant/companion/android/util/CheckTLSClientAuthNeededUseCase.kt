package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.security.auth.x500.X500Principal

// SAN (Subject Alternative Name) type codes, see RFC 5280 section 4.2.1.6.
private const val SAN_TYPE_DNS_NAME = 2
private const val SAN_TYPE_IP_ADDRESS = 7

private const val COMMON_NAME_PREFIX = "CN="
private const val WILDCARD_PREFIX = "*."

/**
 * Determines whether the server a connection is being made to requires a client certificate, by
 * checking whether the certificate loaded on the device is valid for that server's host.
 *
 * This is needed during onboarding because the WebView can resume a TLS session established
 * earlier in the same process, in which case the server does not issue a new `CertificateRequest`
 * and [android.webkit.WebViewClient.onReceivedClientCertRequest] never fires, even though the
 * server requires a client certificate.
 */
class CheckTLSClientAuthNeededUseCase @Inject constructor(private val keyChainRepository: KeyChainRepository) {

    /**
     * Returns `true` when a client certificate is loaded on the device and it is valid for
     * [targetHost].
     *
     * @param targetHost the hostname of the server being connected to, or `null` when the URL
     * could not be parsed. A `null` or blank host never matches, so the result is `false`.
     */
    suspend operator fun invoke(targetHost: String?): Boolean {
        if (targetHost.isNullOrEmpty()) return false
        val certificate = keyChainRepository.getClientCertProvider().certificate?.chain?.firstOrNull()
        return certificate != null && certCoversHost(certificate, targetHost)
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
    } catch (_: CertificateParsingException) {
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
     * Compares [host] with an iPAddress SAN value, normalizing both sides through [InetAddress] so
     * that different textual forms of the same address compare equal. The value is a `String` on
     * the standard Java API and a `ByteArray` on some providers (e.g. BouncyCastle).
     */
    private fun hostMatchesIpAddress(host: String, sanValue: Any?): Boolean {
        val sanAddress = sanValue.toInetAddress()
        val hostAddress = host.toInetAddress()
        return sanAddress != null && sanAddress == hostAddress
    }

    private fun Any?.toInetAddress(): InetAddress? = try {
        when (this) {
            is ByteArray -> InetAddress.getByAddress(this)
            is String -> InetAddress.getByName(this)
            else -> null
        }
    } catch (_: UnknownHostException) {
        null
    }
}
