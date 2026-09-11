package io.homeassistant.companion.android.common.data.connectivity

/**
 * Outcome of the manifest request, which backs both the server connection and the Home Assistant
 * verification checks. Modeled as one result because a single request answers both: a server cannot
 * be verified as Home Assistant without having answered.
 */
internal sealed interface ManifestCheckResult {

    /** The server did not answer the manifest request with a successful response. */
    data class NotReached(val failure: ConnectivityCheckResult.Failure) : ManifestCheckResult

    /** The server answered, but its manifest could not be read or belongs to another product. */
    data class NotVerified(val failure: ConnectivityCheckResult.Failure) : ManifestCheckResult

    /** The server answered with the manifest of a Home Assistant instance. */
    data object Verified : ManifestCheckResult
}

/**
 * Interface for performing individual connectivity checks.
 */
internal interface ConnectivityChecker {

    /**
     * Performs DNS resolution for the given hostname.
     *
     * @param hostname The hostname to resolve
     * @return [ConnectivityCheckResult.Success] with resolved IP addresses, or [ConnectivityCheckResult.Failure]
     */
    suspend fun dns(hostname: String): ConnectivityCheckResult

    /**
     * Checks if a port is reachable on the given hostname.
     *
     * @param hostname The hostname to check
     * @param port The port number to check
     * @return [ConnectivityCheckResult.Success] with port number, or [ConnectivityCheckResult.Failure]
     */
    suspend fun port(hostname: String, port: Int): ConnectivityCheckResult

    /**
     * Performs TLS certificate validation for the given URL.
     *
     * @param url The HTTPS URL to validate
     * @return [ConnectivityCheckResult.Success] if TLS is valid, or [ConnectivityCheckResult.Failure]
     */
    suspend fun tls(url: String): ConnectivityCheckResult

    /**
     * Requests the manifest.json of the server to check that it answers HTTP requests and that it is
     * a Home Assistant instance.
     *
     * @param url The server URL to verify
     * @return Whether the server answered, and whether its answer is the manifest of a Home Assistant instance
     */
    suspend fun homeAssistant(url: String): ManifestCheckResult
}
