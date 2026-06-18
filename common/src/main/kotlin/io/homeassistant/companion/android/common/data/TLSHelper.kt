package io.homeassistant.companion.android.common.data

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChain
import io.homeassistant.companion.android.common.data.keychain.NamedKeyStore
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.SdkVersion
import java.io.IOException
import java.net.Socket
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Helper to configure an [OkHttpClient] for server-certificate validation (with a
 * user-installed CA fallback) and the client certificate for mutual TLS (mTLS).
 */
class TLSHelper @Inject constructor(
    @NamedKeyChain private val keyChainRepository: KeyChainRepository,
    @NamedKeyStore private val keyStore: KeyChainRepository,
) {

    /**
     * Configures [builder] to validate server certificates.
     *
     * It uses Android's default trust manager, which honors `network_security_config.xml` (system and
     * user CAs) and can rebuild an incomplete chain itself (see
     * https://github.com/home-assistant/android/issues/6810).
     *
     * Some ROMs (e.g. /e/OS, https://github.com/home-assistant/android/issues/5565) don't honor
     * user-installed CAs through that default path even though their browser and WebView do. To cover
     * them, the handshake also falls back to a trust manager holding only the user-installed CAs
     * whenever the default rejects a certificate (see [withUserInstalledCaFallback]). This relies on
     * the app opting into user CAs via `<certificates src="user"/>` in `network_security_config.xml`.
     */
    fun setupOkHttpClientSSLSocketFactory(builder: OkHttpClient.Builder) {
        val platformTrustManager = defaultX509TrustManager() ?: run {
            FailFast.fail { "No default X509 trust manager available" }
            return
        }
        val handshakeTrustManager = withUserInstalledCaFallback(platformTrustManager)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(arrayOf(getMTLSKeyManagerForOKHTTP()), arrayOf(handshakeTrustManager), null)

        builder.sslSocketFactory(sslContext.socketFactory, platformTrustManager)
    }

    /**
     * Wraps [trustManager] so user-installed CAs are honored as a fallback (see
     * https://github.com/home-assistant/android/issues/5565).
     *
     * Returns [trustManager] unchanged when there is nothing to add:
     * - before Android N, where user-installed CAs are already trusted by default;
     * - when [trustManager] is not an [X509ExtendedTrustManager] (the composite needs the
     *   hostname-aware overloads); or
     * - when there are no user-installed CAs.
     */
    private fun withUserInstalledCaFallback(trustManager: X509TrustManager): X509TrustManager {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.N)) return trustManager
        val extended = trustManager as? X509ExtendedTrustManager ?: return trustManager
        val userCaTrustManager = userInstalledCaTrustManager() ?: return trustManager
        return CompositeX509ExtendedTrustManager(primary = extended, fallback = userCaTrustManager)
    }

    private fun defaultX509TrustManager(keyStore: KeyStore? = null): X509TrustManager? {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
    }

    /**
     * Builds a trust manager from the user-installed CAs (see [userInstalledCaKeyStore]), or `null`
     * when there are none or AndroidCAStore can't be read.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun userInstalledCaTrustManager(): X509ExtendedTrustManager? = try {
        val androidCaStore = loadKeyStore("AndroidCAStore")
        userInstalledCaKeyStore(androidCaStore)?.let { defaultX509TrustManager(it) as? X509ExtendedTrustManager }
    } catch (e: GeneralSecurityException) {
        Timber.w(e, "Could not read user-installed CAs, user-CA fallback disabled")
        null
    } catch (e: IOException) {
        Timber.w(e, "Could not read user-installed CAs, user-CA fallback disabled")
        null
    }

    private fun getMTLSKeyManagerForOKHTTP(): X509ExtendedKeyManager {
        return object : X509ExtendedKeyManager() {
            override fun getClientAliases(p0: String?, p1: Array<out Principal>?): Array<String> {
                return emptyArray()
            }

            override fun chooseClientAlias(p0: Array<out String>?, p1: Array<out Principal>?, p2: Socket?): String {
                return ""
            }

            override fun getServerAliases(p0: String?, p1: Array<out Principal>?): Array<String> {
                return arrayOf()
            }

            override fun chooseServerAlias(p0: String?, p1: Array<out Principal>?, p2: Socket?): String {
                return ""
            }

            override fun getCertificateChain(p0: String?): Array<X509Certificate>? {
                return keyChainRepository.getCertificateChain() ?: keyStore.getCertificateChain()
            }

            override fun getPrivateKey(p0: String?): PrivateKey? {
                return keyChainRepository.getPrivateKey() ?: keyStore.getPrivateKey()
            }
        }
    }
}

/**
 * Returns a new key store holding only the user-installed CA certificates of [caStore],
 * or `null` if there are none.
 */
@VisibleForTesting
internal fun userInstalledCaKeyStore(caStore: KeyStore): KeyStore? {
    val userCaStore = loadKeyStore()

    for (alias in caStore.aliases()) {
        if (alias.startsWith("user:")) {
            userCaStore.setCertificateEntry(alias, caStore.getCertificate(alias))
        }
    }
    return userCaStore.takeIf { it.size() > 0 }
}

/**
 * Returns a key store of the given [type] initialized with `load(null, null)`.
 */
@VisibleForTesting
internal fun loadKeyStore(type: String = KeyStore.getDefaultType()): KeyStore =
    KeyStore.getInstance(type).apply { load(null, null) }
