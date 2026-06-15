package io.homeassistant.companion.android.common.data

import android.os.Build
import androidx.annotation.RequiresApi
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

/**
 * An [X509ExtendedTrustManager] that trusts a server certificate if either the [primary] or the
 * [fallback] accepts it. The [primary] is always tried first; the [fallback] is only asked when the
 * [primary] rejects the chain.
 *
 * The [primary] is Android's default trust manager (`TrustManagerFactory.init(null)`), which honors
 * `network_security_config.xml` and builds the certificate chain. Some ROMs (e.g. /e/OS, #5565)
 * don't trust user-installed CAs through that path even though the browser and WebView do, so
 * [TLSHelper] gives us a [fallback] holding only the user-installed CAs to cover that case.
 *
 * This is deliberately more permissive than the [primary]: it re-trusts user-installed CAs the
 * platform refused, which widens the trust surface to whatever CAs the user (or an attacker who
 * tricks them) has installed. It can't do worse than that: the [fallback] has no system anchors, so
 * it can't override a rejected system-rooted certificate, and it can't accept a certificate that
 * chains to no trusted anchor (e.g. self-signed). And since the [fallback] only adds acceptances, it
 * can't bring back the over-strict rejection of #6810.
 *
 * Client-trust checks and [getAcceptedIssuers] use the [primary] only; the app is a TLS client, so
 * the [fallback] has no role there.
 *
 * Requires API 24; below it [X509ExtendedTrustManager] doesn't exist and user CAs are trusted by
 * default anyway.
 */
@RequiresApi(Build.VERSION_CODES.N)
internal class CompositeX509ExtendedTrustManager(
    private val primary: X509ExtendedTrustManager,
    private val fallback: X509ExtendedTrustManager,
) : X509ExtendedTrustManager() {

    /**
     * Runs [check] against the [primary], and only if it rejects with a [CertificateException]
     * retries against the [fallback]. If both reject, the [primary]'s exception is rethrown (its
     * verdict wins) with the [fallback]'s attached via [Throwable.addSuppressed].
     *
     * Only [CertificateException] triggers the fallback; anything else propagates unchanged.
     */
    private inline fun checkServerOrFallback(check: (X509ExtendedTrustManager) -> Unit) {
        try {
            check(primary)
        } catch (primaryError: CertificateException) {
            try {
                check(fallback)
            } catch (fallbackError: CertificateException) {
                primaryError.addSuppressed(fallbackError)
                throw primaryError
            }
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        checkServerOrFallback { it.checkServerTrusted(chain, authType) }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        checkServerOrFallback { it.checkServerTrusted(chain, authType, socket) }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        checkServerOrFallback { it.checkServerTrusted(chain, authType, engine) }
    }

    // Client trust uses the primary only (see class doc).
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        primary.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        primary.checkClientTrusted(chain, authType, socket)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        primary.checkClientTrusted(chain, authType, engine)
    }

    /**
     * Returns the [primary]'s issuers only. This list is the CAs accepted for client authentication,
     * which the [fallback] has nothing to do with.
     */
    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return primary.acceptedIssuers
    }
}
