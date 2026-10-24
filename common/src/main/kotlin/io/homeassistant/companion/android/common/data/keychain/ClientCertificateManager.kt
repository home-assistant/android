package io.homeassistant.companion.android.common.data.keychain

import javax.inject.Inject

/**
 * Single entry point for the device's mTLS client certificate, combining its sources: the system
 * KeyChain takes precedence over the app's KeyStore.
 *
 * Awaiting [getClientCertProvider] before performing a TLS handshake guarantees the certificate
 * saved on the device is available for it (see https://github.com/home-assistant/android/issues/6119).
 */
class ClientCertificateManager @Inject constructor(
    private val keyChainRepository: KeyChainRepository,
    private val keyStoreRepository: KeyStoreRepository,
) {

    suspend fun getClientCertProvider(): ClientCertProvider = CombinedClientCertProvider(
        primary = keyChainRepository.getClientCertProvider(),
        fallback = keyStoreRepository.getClientCertProvider(),
    )
}

private class CombinedClientCertProvider(
    private val primary: ClientCertProvider,
    private val fallback: ClientCertProvider,
) : ClientCertProvider {
    override val certificate: ClientCertificate?
        get() = primary.certificate ?: fallback.certificate
}
