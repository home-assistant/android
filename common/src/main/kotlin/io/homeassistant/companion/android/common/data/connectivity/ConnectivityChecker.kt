package io.homeassistant.companion.android.common.data.connectivity

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
     * Checks if the server at the given URL is reachable.
     *
     * @param url The server URL to check
     * @return [ConnectivityCheckResult.Success] if server is reachable, or [ConnectivityCheckResult.Failure]
     */
    suspend fun server(url: String): ConnectivityCheckResult

    /**
     * Verifies if the server is a Home Assistant instance by checking its manifest.json.
     *
     * @param url The server URL to verify
     * @return [ConnectivityCheckResult.Success] if it's Home Assistant, or [ConnectivityCheckResult.Failure]
     */
    suspend fun homeAssistant(url: String): ConnectivityCheckResult
}
