package io.homeassistant.companion.android.common.data

import java.security.cert.X509Certificate
import okhttp3.tls.HeldCertificate

/**
 * Certificates for TLS tests, generated when first used (EC P-256, valid for 24 hours).
 */
internal object TestCertificates {

    private val privateCa = HeldCertificate.Builder()
        .commonName("Test Private CA")
        .certificateAuthority(0)
        .build()

    /** CA certificate (`CN=Test Private CA`) that signed [PRIVATE_CA_LEAF]. */
    val PRIVATE_CA: X509Certificate = privateCa.certificate

    /** Server certificate for `ha.example.com`, signed by [PRIVATE_CA]. */
    val PRIVATE_CA_LEAF: X509Certificate = HeldCertificate.Builder()
        .commonName("ha.example.com")
        .addSubjectAlternativeName("ha.example.com")
        .signedBy(privateCa)
        .build()
        .certificate

    /** CA certificate (`CN=Test System CA`) standing in for a preinstalled one, unrelated to the others. */
    val SYSTEM_CA: X509Certificate = HeldCertificate.Builder()
        .commonName("Test System CA")
        .certificateAuthority(0)
        .build()
        .certificate

    /** Self-signed server certificate for `selfsigned.example.com`, not a CA. */
    val SELF_SIGNED_LEAF: X509Certificate = HeldCertificate.Builder()
        .commonName("selfsigned.example.com")
        .addSubjectAlternativeName("selfsigned.example.com")
        .build()
        .certificate
}
