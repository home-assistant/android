package io.homeassistant.companion.android.common.data.keychain

import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * A complete mTLS client identity.
 */
class ClientCertificate(val privateKey: PrivateKey, val chain: Array<X509Certificate>)

/**
 * Live read-only view of the loaded client certificate, reflecting a certificate selected,
 * stored, or cleared after this view was obtained.
 */
interface ClientCertProvider {
    val certificate: ClientCertificate?
}

/**
 * Live read-only view of the loaded server trust anchor, reflecting a certificate stored or deleted
 * after this view was obtained.
 */
interface ServerTrustAnchorProvider {
    val certificate: X509Certificate?
}
