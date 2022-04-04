package io.homeassistant.companion.android.common.data.keychain

import android.content.Context
import android.security.KeyChain
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject

class KeyChainRepositoryImpl@Inject constructor() : KeyChainRepository {
    
    private var alias: String? = null
    private var key: PrivateKey? = null
    private var chain: Array<X509Certificate>? = null

    override suspend fun getAlias(): String? {
        return this.alias
    }

    override suspend fun load(context: Context, alias: String) {
        if (alias == null) return

        this.alias = alias
        this.key = KeyChain.getPrivateKey(context, alias)
        this.chain = KeyChain.getCertificateChain(context, alias)
    }

    override suspend fun getPrivateKey(): PrivateKey? {
        return this.key
    }

    override suspend fun getCertificateChain(): Array<X509Certificate>? {
        return this.chain
    }
}