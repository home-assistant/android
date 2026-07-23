package io.homeassistant.companion.android.common.data

import android.annotation.SuppressLint
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
 * [primary] rejects the chain with a [CertificateException].
 *
 * This is deliberately more permissive than the [primary] alone: a certificate the [primary] rejects
 * becomes trusted as soon as the [fallback] accepts it. It can never be stricter than the [primary],
 * so it only ever adds acceptances and never introduces new rejections.
 *
 * Client-trust checks ([checkClientTrusted]) and [getAcceptedIssuers] decide which client
 * certificates to accept, which is unrelated to widening server-certificate trust, so they delegate
 * to the [primary] only.
 *
 * Requires API 24: [X509ExtendedTrustManager] and its hostname-aware overloads don't exist below it.
 */
@RequiresApi(Build.VERSION_CODES.N)
@SuppressLint("CustomX509TrustManager")
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

    /**
     * Client trust uses the primary only (see class doc).
     */
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        primary.checkClientTrusted(chain, authType)
    }

    /**
     * Client trust uses the primary only (see class doc).
     */
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        primary.checkClientTrusted(chain, authType, socket)
    }

    /**
     * Client trust uses the primary only (see class doc).
     */
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        primary.checkClientTrusted(chain, authType, engine)
    }

    /** Returns the [primary]'s accepted issuers only; the [fallback] has no role in client authentication. */
    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return primary.acceptedIssuers
    }
}
