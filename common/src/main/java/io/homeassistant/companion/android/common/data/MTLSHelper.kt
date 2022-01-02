package io.homeassistant.companion.android.common.data

import android.content.Context
import android.util.Base64
import io.homeassistant.companion.android.common.R
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileInputStream
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

class MTLSHelper {
    private val filename = "tls_client"
    private val keyentryname = "client_mtls_cert"
    companion object {
        var clientPrivateKey: PrivateKey? = null
        var clientPublicKey: Certificate? = null
        var importMsg: String? = null
        var importErrorMsg: String? = null
    }

    private fun loadKeyData() {
        val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        clientPrivateKey = (keyStore.getKey(keyentryname, null) ?: return) as PrivateKey?
        clientPublicKey = keyStore.getCertificate(keyentryname) ?: return
    }

    private fun tryImportKeys(context: Context) {
        val baseDir = context.getExternalFilesDir(null)!!
        val keyFile = File(baseDir, "$filename.key")
        val certFile = File(baseDir, "$filename.crt")

        if (!keyFile.exists())return
        if (!certFile.exists())return

        var certificateInputStream: FileInputStream? = null
        try {
            val certificateFactory = CertificateFactory.getInstance("X.509")

            val privateKeyContent = keyFile.readText()
                .replace("-----BEGIN.*PRIVATE KEY-----".toRegex(), "")
                .replace(System.lineSeparator().toRegex(), "")
                .replace("-----END.*PRIVATE KEY-----".toRegex(), "")
            val privateKeyAsBytes: ByteArray = Base64.decode(privateKeyContent, Base64.DEFAULT)
            val keySpec = PKCS8EncodedKeySpec(privateKeyAsBytes)
            val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(keySpec)

            // Get certificate
            certificateInputStream = FileInputStream(certFile)
            val certificate = certificateFactory.generateCertificate(certificateInputStream)

            // Set up KeyStore
            val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.setKeyEntry(
                keyentryname,
                privateKey,
                null,
                arrayOf(certificate)
            )

            // Remove files after importing them if tls_client.keep doesn't exists (useful for debugging purposes)
            if (!File(baseDir, "$filename.keep").exists()) {
                keyFile.delete()
                certFile.delete()
            }
            importMsg = context.getString(R.string.mtls_cert_importmsg_message_ok)
        } catch (ex: Exception) {
            importErrorMsg = ex.localizedMessage
        } finally {
            certificateInputStream?.close()
        }
    }

    fun init(context: Context) {
        tryImportKeys(context)
        loadKeyData()
    }
    private fun getMTLSKeyManagerForOKHTTP(): X509KeyManager {
        val mtlsKeyManager = object : X509KeyManager {
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

            override fun getCertificateChain(p0: String?): Array<X509Certificate> {
                return arrayOf(clientPublicKey as X509Certificate)
            }

            override fun getPrivateKey(p0: String?): PrivateKey {
                return clientPrivateKey!!
            }
        }
        return mtlsKeyManager
    }
    fun setupOkHttpClientSSLSocketFactory(builder: OkHttpClient.Builder) {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers

        val sslContext = SSLContext.getInstance("TLS")

        sslContext.init(arrayOf(MTLSHelper().getMTLSKeyManagerForOKHTTP()), trustManagers, null)

        builder.sslSocketFactory(sslContext.socketFactory, trustManagers[0] as X509TrustManager)
    }
}
