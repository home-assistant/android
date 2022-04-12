package io.homeassistant.companion.android.common.data.keychain

import android.content.Context
import java.security.PrivateKey
import java.security.cert.X509Certificate

interface KeyChainRepository {
    suspend fun getAlias(): String?

    suspend fun load(context: Context, alias: String): Boolean

    suspend fun load(context: Context): Boolean

    suspend fun getPrivateKey(): PrivateKey?

    suspend fun getCertificateChain(): Array<X509Certificate>?
}
