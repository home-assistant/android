package io.homeassistant.companion.android.common.data.keychain

import android.content.Context
import android.util.Log
import java.security.KeyStore
import java.security.KeyStore.PrivateKeyEntry
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KeyStoreRepositoryImpl @Inject constructor() : KeyChainRepository {
    companion object {
        private const val TAG = "KeyStoreRepository"
        const val ALIAS = "TLSClientCertificate"
    }

    private var alias: String? = null
    private var key: PrivateKey? = null
    private var chain: Array<X509Certificate>? = null

    override suspend fun clear() {
        // intentionally left empty
    }

    override suspend fun load(context: Context, alias: String) = withContext(Dispatchers.IO) {
        this@KeyStoreRepositoryImpl.alias = alias
        doLoad()
    }

    override suspend fun load(context: Context) {
        throw IllegalArgumentException("Key alias cannot be null.")
    }

    override suspend fun setData(alias: String, privateKey: PrivateKey, certificateChain: Array<X509Certificate>) = withContext(Dispatchers.IO) {
        // clear state
        this@KeyStoreRepositoryImpl.alias = null
        this@KeyStoreRepositoryImpl.key = null
        this@KeyStoreRepositoryImpl.chain = null

        // store and load certificate to/from KeyStore
        doStore(alias, privateKey, certificateChain)
        this@KeyStoreRepositoryImpl.alias = alias
        doLoad()
    }

    override fun getAlias(): String? {
        return alias
    }

    override fun getPrivateKey(): PrivateKey? {
        return key
    }

    override fun getCertificateChain(): Array<X509Certificate>? {
        return chain
    }

    @Synchronized
    private fun doLoad() {
        if (alias != null && alias?.isNotEmpty() == true) {
            val aks = KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (!containsAlias(alias)) return
            }
            val entry = try {
                aks.getEntry(alias, null) as PrivateKeyEntry
            } catch (e: Exception) {
                Log.e(TAG, "Exception getting KeyStore.Entry", e)
                null
            }
            if (entry != null) {
                if (chain == null) {
                    chain = try {
                        @Suppress("UNCHECKED_CAST")
                        entry.certificateChain as Array<X509Certificate>
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception getting certificate chain", e)
                        null
                    }
                }
                if (key == null) {
                    key = try {
                        entry.privateKey
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception getting private key", e)
                        null
                    }
                }
            }
        }
    }

    @Synchronized
    private fun doStore(alias: String, key: PrivateKey, chain: Array<X509Certificate>) {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                setEntry(alias, PrivateKeyEntry(key, chain), null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception storing KeyStore.Entry", e)
        }
    }
}
