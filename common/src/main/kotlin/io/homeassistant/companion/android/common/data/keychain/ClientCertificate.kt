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
