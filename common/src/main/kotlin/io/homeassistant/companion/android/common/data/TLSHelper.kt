package io.homeassistant.companion.android.common.data

import android.os.Build
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChain
import io.homeassistant.companion.android.common.data.keychain.NamedKeyStore
import java.io.IOException
import java.net.Socket
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Collections
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import timber.log.Timber

private const val ANDROID_CA_STORE = "AndroidCAStore"

// AndroidCAStore prefixes user-installed CA aliases with "user:" and system ones with "system:".
private const val USER_CA_ALIAS_PREFIX = "user:"

class TLSHelper @Inject constructor(
    @NamedKeyChain private val keyChainRepository: KeyChainRepository,
    @NamedKeyStore private val keyStore: KeyChainRepository,
) {

    fun setupOkHttpClientSSLSocketFactory(builder: OkHttpClient.Builder) {
        // Default trust manager. init(null) gives us Android's default, which honors
        // network_security_config.xml (system + user CAs) and builds the certificate chain itself.
        // Passing an explicit KeyStore here instead breaks validation of valid certificates (#6810).
        val platformTrustManager = defaultX509TrustManager(keyStore = null)

        // Some ROMs (e.g. /e/OS, #5565) don't trust user-installed CAs through the default path even
        // though the browser and WebView do. On those we fall back to a trust manager holding only
        // the user-installed CAs, consulted only when the default rejects a certificate. It can add
        // acceptances but never cause a rejection, so it can't bring back #6810. See
        // [CompositeX509ExtendedTrustManager] for the trust trade-off.
        val handshakeTrustManager = withUserInstalledCaFallback(platformTrustManager)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(arrayOf(getMTLSKeyManagerForOKHTTP()), arrayOf(handshakeTrustManager), null)

        // OkHttp only uses this trust manager to build a chain cleaner for certificate pinning, which
        // the app doesn't use, so it doesn't affect the handshake (handshakeTrustManager decides
        // that). We still pass the default one so OkHttp keeps using Android's chain cleaner if
        // pinning is ever added.
        builder.sslSocketFactory(sslContext.socketFactory, platformTrustManager)
    }

    /**
     * Wraps [platformTrustManager] so user-installed CAs are honored as a fallback (see #5565).
     *
     * Returns it unchanged, with no fallback, when:
     * - running before Android N, where user-installed CAs are trusted by default;
     * - the platform trust manager is not an [X509ExtendedTrustManager] (the composite needs the
     *   hostname-aware overloads); or
     * - there are no user-installed CAs to fall back to.
     */
    private fun withUserInstalledCaFallback(platformTrustManager: X509TrustManager): X509TrustManager {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return platformTrustManager
        val extendedPlatform = platformTrustManager as? X509ExtendedTrustManager ?: return platformTrustManager
        val userCaTrustManager = userInstalledCaTrustManager() ?: return platformTrustManager
        return CompositeX509ExtendedTrustManager(primary = extendedPlatform, fallback = userCaTrustManager)
    }

    private fun defaultX509TrustManager(keyStore: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * Builds a trust manager from the user-installed CAs (see [userInstalledCaKeyStore]), or `null`
     * when there are none or the AndroidCAStore can't be read.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun userInstalledCaTrustManager(): X509ExtendedTrustManager? = try {
        val androidCaStore = KeyStore.getInstance(ANDROID_CA_STORE).apply { load(null, null) }
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
 * Returns a key store holding only the user-installed CA certificates of [androidCaStore] (the
 * entries aliased with [USER_CA_ALIAS_PREFIX]), or `null` if there are none.
 *
 * The preinstalled system CAs are left out so the fallback can only trust CAs the user added, never
 * override the platform's rejection of a system-rooted certificate (e.g. one from a distrusted CA).
 */
internal fun userInstalledCaKeyStore(androidCaStore: KeyStore): KeyStore? {
    val userCaAliases = Collections.list(androidCaStore.aliases())
        .filter { it.startsWith(USER_CA_ALIAS_PREFIX) }
    if (userCaAliases.isEmpty()) return null
    return KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        userCaAliases.forEach { alias -> setCertificateEntry(alias, androidCaStore.getCertificate(alias)) }
    }
}
