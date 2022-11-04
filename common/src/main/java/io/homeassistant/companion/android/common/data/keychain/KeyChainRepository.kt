package io.homeassistant.companion.android.common.data.keychain

import android.content.Context
import java.security.PrivateKey
import java.security.cert.X509Certificate

interface KeyChainRepository {

    suspend fun clear()

    suspend fun load(context: Context, alias: String)

    suspend fun load(context: Context)

    fun getAlias(): String?

    fun getPrivateKey(): PrivateKey?

    fun getCertificateChain(): Array<X509Certificate>?
}
