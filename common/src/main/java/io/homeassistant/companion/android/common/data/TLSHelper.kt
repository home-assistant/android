package io.homeassistant.companion.android.common.data

import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
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

class TLSHelper @Inject constructor(private val keyChainRepository: KeyChainRepository) {

    fun setupOkHttpClientSSLSocketFactory(builder: OkHttpClient.Builder) {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(arrayOf(getMTLSKeyManagerForOKHTTP()), trustManagers, null)

        builder.sslSocketFactory(sslContext.socketFactory, trustManagers[0] as X509TrustManager)
    }

    private fun getMTLSKeyManagerForOKHTTP(): X509ExtendedKeyManager {
        return object : X509ExtendedKeyManager() {
            override fun getClientAliases(
                p0: String?,
                p1: Array<out Principal>?
            ): Array<String> {
                return emptyArray()
            }

            override fun chooseClientAlias(
                p0: Array<out String>?,
                p1: Array<out Principal>?,
                p2: Socket?
            ): String {
                return ""
            }

            override fun getServerAliases(
                p0: String?,
                p1: Array<out Principal>?
            ): Array<String> {
                return arrayOf()
            }

            override fun chooseServerAlias(
                p0: String?,
                p1: Array<out Principal>?,
                p2: Socket?
            ): String {
                return ""
            }

            override fun getCertificateChain(p0: String?): Array<X509Certificate>? {
                var chain: Array<X509Certificate>?

                // block until a chain is provided via the TLSWebView
                runBlocking {
                    chain = keyChainRepository.getCertificateChain()
                }

                return chain
            }

            override fun getPrivateKey(p0: String?): PrivateKey? {
                var key: PrivateKey?

                // block until a key is provided via the TLSWebView
                runBlocking {
                    key = keyChainRepository.getPrivateKey()
                }

                return key
            }
        }
    }
}
