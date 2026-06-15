package io.homeassistant.companion.android.common.data

import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChain
import io.homeassistant.companion.android.common.data.keychain.NamedKeyStore
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import timber.log.Timber

class TLSHelper @Inject constructor(
    @NamedKeyChain private val keyChainRepository: KeyChainRepository,
    @NamedKeyStore private val keyStore: KeyChainRepository,
) {

    fun setupOkHttpClientSSLSocketFactory(builder: OkHttpClient.Builder) {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        // Load AndroidCAStore explicitly to include user-installed CAs alongside
        // system CAs. On some Android builds, passing null may load only the
        // system store, which can bypass user-CA trust configured in
        // network_security_config.xml (#5565).
        val androidCaStore: KeyStore? = try {
            KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        } catch (e: Throwable) {
            Timber.w(e, "AndroidCAStore unavailable, falling back to system trust store")
            null
        }
        trustManagerFactory.init(androidCaStore)
        val trustManagers = trustManagerFactory.trustManagers

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(arrayOf(getMTLSKeyManagerForOKHTTP()), trustManagers, null)

        builder.sslSocketFactory(sslContext.socketFactory, trustManagers[0] as X509TrustManager)
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
