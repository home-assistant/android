package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.security.auth.x500.X500Principal

// SAN (Subject Alternative Name) type codes per RFC 5280 section 4.2.1.6
private const val SAN_TYPE_DNS_NAME = 2
private const val SAN_TYPE_IP_ADDRESS = 7

/**
 * Checks whether the in-memory client certificate chain covers the target host, which indicates
 * the server requires a TLS client certificate.
 *
 * This is needed to handle TLS session resumption: when the WebView reuses a TLS session that was
 * established earlier in the same process, the server does not issue a new `CertificateRequest`,
 * so [android.webkit.WebViewClient.onReceivedClientCertRequest] is never invoked and the
 * WebViewClient's own flag stays false — even if the server requires a client certificate.
 *
 * The check inspects the certificate kept in memory by the [KeyChainRepository] and verifies it
 * against the host via its Subject Alternative Names (SANs), falling back to the Common Name
 * (CN) when no SANs are present. Matching the host avoids a false positive when the user has
 * multiple servers where only one requires mTLS.
 *
 * If the app was force-stopped first (clearing in-memory state) no TLS session can be resumed
 * either, so [android.webkit.WebViewClient.onReceivedClientCertRequest] fires naturally on the
 * fresh handshake and the WebViewClient's flag is set without this check.
 *
 * @param targetHost the hostname of the server being connected to, or `null` if the URL could not
 * be parsed. A `null` (or blank) host never matches, so the result is `false`.
 */
class CheckTLSClientAuthNeededUseCase @Inject constructor(private val keyChainRepository: KeyChainRepository) {
    suspend operator fun invoke(targetHost: String?): Boolean {
        if (targetHost.isNullOrEmpty()) return false
        val cert = keyChainRepository.getClientCertProvider().certificate?.chain?.firstOrNull()
            ?: return false
        return certCoversHost(cert, targetHost)
    }

    /**
     * Returns `true` if [cert] is valid for [host].
     *
     * Checks Subject Alternative Names (SANs) first — both DNS names (with wildcard support)
     * and IP addresses. Falls back to the Common Name (CN) in the Subject DN if no SANs are
     * present, matching the behaviour of legacy TLS stacks.
     */
    private fun certCoversHost(cert: X509Certificate, host: String): Boolean {
        val sans: Collection<List<*>>? = try {
            cert.subjectAlternativeNames
        } catch (_: CertificateParsingException) {
            null
        }

        return if (!sans.isNullOrEmpty()) {
            sans.any { san ->
                if (san.size < 2) return@any false
                val type = san[0] as? Int ?: return@any false
                when (type) {
                    SAN_TYPE_DNS_NAME -> { // dNSName — returned as String
                        val value = san[1] as? String ?: return@any false
                        hostMatchesSan(host, value)
                    }
                    SAN_TYPE_IP_ADDRESS -> {
                        // iPAddress — the standard Java X.509 API returns this as a String
                        // (dotted-quad or colon-hex), but some providers (e.g. BouncyCastle)
                        // return a ByteArray; handle both defensively.
                        // Normalize both sides through InetAddress so that different textual
                        // representations of the same address compare equal (e.g. "::1" vs
                        // "0:0:0:0:0:0:0:1").
                        val sanAddress = try {
                            when (val ipEntry = san[1]) {
                                is ByteArray -> InetAddress.getByAddress(ipEntry)
                                is String -> InetAddress.getByName(ipEntry)
                                else -> return@any false
                            }
                        } catch (_: UnknownHostException) {
                            return@any false
                        }
                        val hostAddress = try {
                            InetAddress.getByName(host)
                        } catch (_: UnknownHostException) {
                            return@any false
                        }
                        hostAddress == sanAddress
                    }
                    else -> false
                }
            }
        } else {
            // Fallback: extract CN from the Subject DN.
            // getName(RFC2253) uses comma as AVA separator; commas inside values are escaped
            // as \, which we don't need to handle because hostnames never contain commas.
            val dn = cert.subjectX500Principal.getName(X500Principal.RFC2253)
            val cn = dn.splitToSequence(",")
                .map { it.trim() }
                .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
                ?.let { it.substring(it.indexOf('=') + 1).trim() }
                ?.takeIf { it.isNotEmpty() }
            cn != null && hostMatchesSan(host, cn)
        }
    }

    /**
     * Matches [host] against a SAN value that may contain a leading wildcard.
     *
     * A wildcard (`*.example.com`) covers any single label: `foo.example.com` matches but
     * `foo.bar.example.com` and `example.com` do not (per RFC 2818 §3.1).
     */
    private fun hostMatchesSan(host: String, san: String): Boolean {
        if (!san.startsWith("*.")) return host.equals(san, ignoreCase = true)
        val suffix = san.substring(1) // ".example.com"
        if (!host.endsWith(suffix, ignoreCase = true)) return false
        val wildcardLabel = host.substring(0, host.length - suffix.length)
        return wildcardLabel.isNotEmpty() && !wildcardLabel.contains('.')
    }
}
