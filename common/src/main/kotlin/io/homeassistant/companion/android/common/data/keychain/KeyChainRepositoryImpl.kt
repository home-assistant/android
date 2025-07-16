package io.homeassistant.companion.android.common.data.keychain

import android.content.Context
import android.security.KeyChain
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import java.lang.UnsupportedOperationException
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class KeyChainRepositoryImpl @Inject constructor(private val prefsRepository: PrefsRepository) : KeyChainRepository {

    private var alias: String? = null
    private var key: PrivateKey? = null
    private var chain: Array<X509Certificate>? = null

    override suspend fun clear() {
        prefsRepository.saveKeyAlias("")
    }

    override suspend fun load(context: Context, alias: String) {
        this.alias = alias
        prefsRepository.saveKeyAlias(alias)
        load(context)
    }

    override suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (alias == null) {
            alias = prefsRepository.getKeyAlias()
        }

        doLoad(context)
    }

    override suspend fun setData(alias: String, privateKey: PrivateKey, certificateChain: Array<X509Certificate>) {
        throw UnsupportedOperationException("setData not supported for KeyChainRepositoryImpl")
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
    private fun doLoad(context: Context) {
        if (alias != null && alias?.isNotEmpty() == true) {
            if (chain == null) {
                chain = try {
                    KeyChain.getCertificateChain(context, alias!!)
                } catch (t: Throwable) {
                    when (t) {
                        is AssertionError,
                        is Exception,
                        -> Timber.e(t, "Issue getting certificate chain")
                        else -> throw t
                    }
                    null
                }
            }
            if (key == null) {
                key = try {
                    KeyChain.getPrivateKey(context, alias!!)
                } catch (t: Throwable) {
                    when (t) {
                        is AssertionError,
                        is Exception,
                        -> Timber.e(t, "Issue getting private key")
                        else -> throw t
                    }
                    null
                }
            }
        }
    }
}
