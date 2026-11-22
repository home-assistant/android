package io.homeassistant.companion.android.common.data.keychain

import android.content.Context
import android.security.KeyChain
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class KeyChainRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepository: PrefsRepository,
) : KeyChainRepository {

    private val mutex = Mutex()

    /** Written under [mutex]; read lock-free through [provider] from non-suspending contexts like TLS handshakes. */
    @Volatile
    private var certificate: ClientCertificate? = null

    /** Only accessed while holding [mutex]. */
    private var alias: String? = null

    private val provider = object : ClientCertProvider {
        override val certificate: ClientCertificate?
            get() = this@KeyChainRepositoryImpl.certificate
    }

    override suspend fun clear() {
        mutex.withLock {
            prefsRepository.saveKeyAlias("")
            alias = null
            certificate = null
        }
    }

    override suspend fun getClientCertProvider(): ClientCertProvider {
        if (certificate == null) {
            mutex.withLock {
                if (certificate == null) {
                    if (alias == null) {
                        alias = prefsRepository.getKeyAlias()
                    }
                    certificate = loadCertificate()
                }
            }
        }
        return provider
    }

    override suspend fun select(alias: String): ClientCertificate? = mutex.withLock {
        prefsRepository.saveKeyAlias(alias)
        this.alias = alias
        loadCertificate().also { certificate = it }
    }

    /** Must be called while holding [mutex]; runs the blocking [KeyChain] calls on [Dispatchers.IO]. */
    private suspend fun loadCertificate(): ClientCertificate? {
        val alias = alias
        if (alias.isNullOrEmpty()) return null

        return withContext(Dispatchers.IO) {
            val chain = getOrLogFailure("Issue getting certificate chain") {
                KeyChain.getCertificateChain(context, alias)
            }
            val key = getOrLogFailure("Issue getting private key") {
                KeyChain.getPrivateKey(context, alias)
            }
            if (key != null && !chain.isNullOrEmpty()) {
                ClientCertificate(key, chain)
            } else {
                null
            }
        }
    }

    /**
     * [KeyChain] can throw [AssertionError] on some devices, so it is handled like an exception.
     */
    private fun <T> getOrLogFailure(message: String, block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        when (t) {
            is AssertionError,
            is Exception,
            -> Timber.e(t, message)
            else -> throw t
        }
        null
    }
}
