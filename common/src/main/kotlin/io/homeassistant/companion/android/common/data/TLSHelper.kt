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

class TLSHelper @Inject constructor(
    @NamedKeyChain private val keyChainRepository: KeyChainRepository,
    @NamedKeyStore private val keyStore: KeyChainRepository,
) {
    private var certificateChain: Array<X509Certificate>? = null
    private var privateKey: PrivateKey? = null

    fun setupOkHttpClientSSLSocketFactory(builder: OkHttpClient.Builder) {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(arrayOf(getMTLSKeyManagerForOKHTTP()), trustManagers, null)

        builder.sslSocketFactory(sslContext.socketFactory, trustManagers[0] as X509TrustManager)
    }
    suspend fun preloadKeys() {
        certificateChain = keyChainRepository.getCertificateChain() ?: keyStore.getCertificateChain()
        privateKey = keyChainRepository.getPrivateKey() ?: keyStore.getPrivateKey()
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
                return certificateChain
            }

            override fun getPrivateKey(p0: String?): PrivateKey? {
                return privateKey
            }
        }
    }
}
