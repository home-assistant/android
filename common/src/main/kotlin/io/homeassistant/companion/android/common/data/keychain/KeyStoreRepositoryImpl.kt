package io.homeassistant.companion.android.common.data.keychain

import android.os.Build
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class KeyStoreRepositoryImpl @Inject constructor() : KeyStoreRepository {

    private companion object {
        /** Static alias because there is no way to ask the user for one on the watch. */
        private const val CLIENT_CERTIFICATE_ALIAS = "TLSClientCertificate"
        private const val SERVER_TRUST_ANCHOR_ALIAS = "TLSServerTrustAnchor"
    }

    private val mutex = Mutex()

    /**
     * Written under [mutex]; read lock-free through [clientCertProvider] from non-suspending contexts like TLS
     * handshakes.
     */
    @Volatile
    private var clientCertificate: ClientCertificate? = null

    private val clientCertProvider = object : ClientCertProvider {
        override val certificate: ClientCertificate?
            get() = clientCertificate
    }

    /** Written under [mutex]; read lock-free through [serverTrustAnchorProvider] from TLS handshakes. */
    @Volatile
    private var serverTrustAnchor: X509Certificate? = null

    private val serverTrustAnchorProvider = object : ServerTrustAnchorProvider {
        override val certificate: X509Certificate?
            get() = serverTrustAnchor
    }

    override suspend fun getClientCertProvider(): ClientCertProvider {
        if (clientCertificate == null) {
            mutex.withLock {
                if (clientCertificate == null) {
                    clientCertificate = loadClientCertificate()
                }
            }
        }
        return clientCertProvider
    }

    override suspend fun storeClientCertificate(
        privateKey: PrivateKey,
        chain: Array<X509Certificate>,
    ): ClientCertificate? = mutex.withLock {
        storeClientCertificateEntry(privateKey, chain)
        loadClientCertificate().also { clientCertificate = it }
    }

    override suspend fun getServerTrustAnchorProvider(): ServerTrustAnchorProvider {
        if (serverTrustAnchor == null) {
            mutex.withLock {
                if (serverTrustAnchor == null) {
                    serverTrustAnchor = loadServerTrustAnchor()
                }
            }
        }
        return serverTrustAnchorProvider
    }

    override suspend fun storeServerTrustAnchor(certificate: X509Certificate?) {
        mutex.withLock {
            storeServerTrustAnchorEntry(certificate)
            serverTrustAnchor = loadServerTrustAnchor()
        }
    }

    /** Must be called while holding [mutex]; runs the blocking [KeyStore] calls on [Dispatchers.IO]. */
    private suspend fun storeServerTrustAnchorEntry(certificate: X509Certificate?) {
        withContext(Dispatchers.IO) {
            try {
                val aks = keyStore().apply { load(null) }
                when {
                    certificate != null -> aks.setCertificateEntry(SERVER_TRUST_ANCHOR_ALIAS, certificate)
                    aks.containsAlias(SERVER_TRUST_ANCHOR_ALIAS) -> aks.deleteEntry(SERVER_TRUST_ANCHOR_ALIAS)
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception storing the server trust anchor")
            }
        }
    }

    /**
     * Must be called while holding [mutex]; runs the blocking [KeyStore] calls on [Dispatchers.IO].
     *
     * Never throws: a KeyStore failure is logged and returns `null`, as if no anchor was stored.
     */
    private suspend fun loadServerTrustAnchor(): X509Certificate? = withContext(Dispatchers.IO) {
        try {
            keyStore().apply { load(null) }.getCertificate(SERVER_TRUST_ANCHOR_ALIAS) as? X509Certificate
        } catch (e: Exception) {
            Timber.e(e, "Exception getting the server trust anchor")
            null
        }
    }

    /** Must be called while holding [mutex]; runs the blocking [KeyStore] calls on [Dispatchers.IO]. */
    private suspend fun loadClientCertificate(): ClientCertificate? = withContext(Dispatchers.IO) {
        val aks = keyStore().apply { load(null) }
        if (!aks.containsAlias(CLIENT_CERTIFICATE_ALIAS)) return@withContext null

        val entry = try {
            aks.getEntry(CLIENT_CERTIFICATE_ALIAS, null) as PrivateKeyEntry
        } catch (e: Exception) {
            Timber.e(e, "Exception getting KeyStore.Entry")
            null
        } ?: return@withContext null

        val chain = try {
            @Suppress("UNCHECKED_CAST")
            entry.certificateChain as Array<X509Certificate>
        } catch (e: Exception) {
            Timber.e(e, "Exception getting certificate chain")
            null
        }
        val key = try {
            entry.privateKey
        } catch (e: Exception) {
            Timber.e(e, "Exception getting private key")
            null
        }
        if (key != null && !chain.isNullOrEmpty()) {
            ClientCertificate(key, chain)
        } else {
            null
        }
    }

    /** Must be called while holding [mutex]; runs the blocking [KeyStore] calls on [Dispatchers.IO]. */
    private suspend fun storeClientCertificateEntry(key: PrivateKey, chain: Array<X509Certificate>) {
        withContext(Dispatchers.IO) {
            try {
                keyStore().apply {
                    load(null)
                    setEntry(CLIENT_CERTIFICATE_ALIAS, PrivateKeyEntry(key, chain), null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception storing KeyStore.Entry")
            }
        }
    }

    private fun keyStore(): KeyStore = if ("robolectric" == Build.FINGERPRINT) {
        KeyStore.getInstance(KeyStore.getDefaultType())
    } else {
        KeyStore.getInstance("AndroidKeyStore")
    }
}
