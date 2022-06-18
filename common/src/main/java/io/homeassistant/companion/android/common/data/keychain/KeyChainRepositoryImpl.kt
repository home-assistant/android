package io.homeassistant.companion.android.common.data.keychain

import android.content.Context
import android.security.KeyChain
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject

class KeyChainRepositoryImpl @Inject constructor(
    private val prefsRepository: PrefsRepository
) : KeyChainRepository {

    private var alias: String? = null
    private var key: PrivateKey? = null
    private var chain: Array<X509Certificate>? = null
    private var isLoading: Boolean = false

    override suspend fun load(context: Context, alias: String) {
        if (alias == null) return

        this.alias = alias
        prefsRepository.saveKeyAlias(alias)

        load(context)
    }

    override suspend fun load(context: Context) = withContext(Dispatchers.IO)  {
        if (alias == null) {
            alias = prefsRepository.getKeyAlias()
        }

        if (alias != null && !isLoading && (key == null || chain == null)) {
            isLoading = true
            if (chain == null) {
                chain = KeyChain.getCertificateChain(context, alias!!)
            }
            if (key == null) {
                key = KeyChain.getPrivateKey(context, alias!!)
            }
            isLoading = false
        }
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
}
