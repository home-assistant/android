package io.homeassistant.companion.android.common.data.keychain

import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Client certificate stored in the app's own Android KeyStore, used on Wear OS where the
 * certificate is pushed from the phone instead of being picked by the user.
 */
interface KeyStoreRepository {

    /** Returns the live view of the certificate, loading it from the KeyStore when it is not loaded yet. */
    suspend fun getClientCertProvider(): ClientCertProvider

    /**
     * Stores the certificate and loads it, replacing any previously loaded one.
     *
     * @return the loaded certificate, or `null` when storing failed.
     */
    suspend fun store(privateKey: PrivateKey, chain: Array<X509Certificate>): ClientCertificate?
}
