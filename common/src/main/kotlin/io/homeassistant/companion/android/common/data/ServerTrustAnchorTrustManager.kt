package io.homeassistant.companion.android.common.data

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.keychain.ServerTrustAnchorProvider
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

private const val SERVER_TRUST_ANCHOR_ALIAS = "serverTrustAnchor"

/**
 * An [X509ExtendedTrustManager] trusting only the current certificate of [serverTrustAnchor], meant as
 * the fallback of a [CompositeX509ExtendedTrustManager].
 *
 * The certificate is read on every check, so a long-lived client sees a certificate stored after it was
 * built. Without a certificate every server certificate is rejected.
 *
 * Client-trust checks are not supported by this fallback and always reject.
 */
@RequiresApi(Build.VERSION_CODES.N)
@SuppressLint("CustomX509TrustManager")
internal class ServerTrustAnchorTrustManager(private val serverTrustAnchor: ServerTrustAnchorProvider) :
    X509ExtendedTrustManager() {

    private fun delegate(): X509ExtendedTrustManager {
        val anchor = serverTrustAnchor.certificate ?: throw CertificateException("No server trust anchor")
        val keyStore = loadKeyStore().apply { setCertificateEntry(SERVER_TRUST_ANCHOR_ALIAS, anchor) }
        return defaultX509TrustManager(keyStore) as? X509ExtendedTrustManager
            ?: throw CertificateException("No trust manager for the server trust anchor")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        delegate().checkServerTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        delegate().checkServerTrusted(chain, authType, socket)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        delegate().checkServerTrusted(chain, authType, engine)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not checked by this trust manager")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        throw CertificateException("Client certificates are not checked by this trust manager")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        throw CertificateException("Client certificates are not checked by this trust manager")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
