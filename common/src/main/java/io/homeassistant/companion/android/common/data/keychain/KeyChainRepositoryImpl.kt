package io.homeassistant.companion.android.common.data.keychain

import android.content.Context
import android.security.KeyChain
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import javax.inject.Inject

class KeyChainRepositoryImpl @Inject constructor(
    private val prefsRepository: PrefsRepository
) : KeyChainRepository {

    //@Inject
    //lateinit var prefsRepository: PrefsRepository

    private var alias: String? = null
    private var key: PrivateKey? = null
    private var chain: Array<X509Certificate>? = null
    private var isLoading: Boolean = false

    override suspend fun getAlias(): String? {
        return alias
    }

    override suspend fun load(context: Context, alias: String): Boolean {
        if (alias == null) return isLoading

        this.alias = alias
        prefsRepository.saveKeyAlias(alias)

        return load(context)
    }

    override suspend fun load(context: Context): Boolean {
        if (alias == null) {
            alias = prefsRepository.getKeyAlias()
        }

        if (alias != null && !isLoading && (key == null || chain == null)) {
            isLoading = true // TODO: need proper sync
            Executors.newSingleThreadExecutor().execute {
                if (chain == null) {
                    chain = KeyChain.getCertificateChain(context, alias!!)
                }
                if (key == null) {
                    key = KeyChain.getPrivateKey(context, alias!!)
                }
                isLoading = false
            }
        }

        return isLoading
    }

    override suspend fun getPrivateKey(): PrivateKey? {
        return key
    }

    override suspend fun getCertificateChain(): Array<X509Certificate>? {
        return chain
    }
}
