package io.homeassistant.companion.android.common.data.keychain

import android.content.Context
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Qualifier

/**
 * Qualifier for the [KeyChainRepository] used to select the key chain.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NamedKeyChain

/**
 * Qualifier for the [KeyChainRepository] used to select the key store.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NamedKeyStore

interface KeyChainRepository {

    suspend fun clear()

    suspend fun load(context: Context, alias: String)

    suspend fun load(context: Context)

    suspend fun setData(alias: String, privateKey: PrivateKey, certificateChain: Array<X509Certificate>)

    fun getAlias(): String?

    fun getPrivateKey(): PrivateKey?

    fun getCertificateChain(): Array<X509Certificate>?
}
