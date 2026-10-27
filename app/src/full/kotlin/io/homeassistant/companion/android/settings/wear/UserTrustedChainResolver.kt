package io.homeassistant.companion.android.settings.wear

import android.net.http.X509TrustManagerExtensions
import android.os.Build
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.TLSHelper
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.util.sensitive
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import timber.log.Timber

/** Auth type passed to the trust manager, as Conscrypt does for TLS 1.3 where it is unused. */
private const val GENERIC_AUTH_TYPE = "GENERIC"

/**
 * Finds the certificate the phone trusts a Home Assistant server with because the user installed it,
 * so the watch can trust the server too (Wear OS has no UI to install user CAs).
 */
class UserTrustedChainResolver @Inject constructor(
    private val homeAssistantApis: HomeAssistantApis,
    private val tlsHelper: TLSHelper,
) {

    /**
     * Connects to [url] and returns the user-installed CA its certificate chain is trusted with (possibly
     * the server certificate itself), or `null` when there is none or before Android N.
     *
     * Never throws: any failure is logged and returns `null`.
     */
    suspend fun resolve(url: String): X509Certificate? {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.N)) return null
        val httpUrl = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        return try {
            val peerChain = fetchPeerChain(httpUrl.toString()) ?: return null
            userTrustedAnchor(peerChain, httpUrl.host).also { anchor ->
                Timber.d("User trusted anchor found for ${sensitive(httpUrl.host)}: ${anchor != null}")
            }
        } catch (e: IOException) {
            Timber.w(e, "Failed to connect to the server to resolve its user trusted anchor")
            null
        } catch (e: GeneralSecurityException) {
            Timber.w(e, "Failed to validate the server chain to resolve its user trusted anchor")
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Trust manager not supported to resolve the user trusted anchor")
            null
        }
    }

    /** Returns the certificates sent by the server during the handshake, or `null` when there are none. */
    private suspend fun fetchPeerChain(url: String): List<X509Certificate>? {
        val request = Request.Builder().url(url).head().build()
        val client = homeAssistantApis.getOkHttpClient().newBuilder()
            .followRedirects(false)
            .build()
        val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
        val peerCertificates = response.use { it.handshake?.peerCertificates }
        return peerCertificates?.filterIsInstance<X509Certificate>()?.takeIf { it.isNotEmpty() }
    }

    /** Returns the trust anchor of [peerChain] when it validates against the user-installed CAs only. */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun userTrustedAnchor(peerChain: List<X509Certificate>, host: String): X509Certificate? {
        val userCaTrustManager = tlsHelper.userInstalledCaTrustManager() ?: return null
        return try {
            // Server certificate first, trust anchor last
            X509TrustManagerExtensions(userCaTrustManager)
                .checkServerTrusted(peerChain.toTypedArray(), GENERIC_AUTH_TYPE, host)
                .lastOrNull()
        } catch (e: CertificateException) {
            Timber.d(e, "Server chain is not anchored on a user-installed CA")
            null
        }
    }
}
