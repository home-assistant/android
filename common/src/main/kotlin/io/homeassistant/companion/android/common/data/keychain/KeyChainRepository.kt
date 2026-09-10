package io.homeassistant.companion.android.common.data.keychain

/**
 * Client certificate the user picked from the Android system KeyChain. The selected alias is
 * persisted and restored across app starts.
 */
interface KeyChainRepository {

    /**
     * Returns the live view of the certificate, loading the persisted selection when it is not
     * loaded yet.
     */
    suspend fun getClientCertProvider(): ClientCertProvider

    /**
     * Persists [alias] as the user's selection and loads its certificate, replacing any previously
     * loaded one.
     *
     * @return the loaded certificate, or `null` when it could not be retrieved from the KeyChain.
     */
    suspend fun select(alias: String): ClientCertificate?

    /** Forgets the persisted selection and drops the loaded certificate. */
    suspend fun clear()
}
