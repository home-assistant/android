package io.homeassistant.companion.android.frontend.url

/**
 * Result of loading a server URL from [FrontendUrlManager.serverUrlFlow].
 *
 * Each result type indicates either success or a specific condition that prevents
 * loading the frontend, allowing the UI to show the appropriate screen.
 */
sealed interface UrlLoadResult {

    /**
     * URL ready to load in the WebView.
     */
    data class Success(val url: String, val serverId: Int) : UrlLoadResult

    /**
     * Server not found in the database.
     *
     * This typically indicates the server was deleted or an invalid ID was provided.
     *
     * @property serverId The server ID that was not found
     */
    data class ServerNotFound(val serverId: Int) : UrlLoadResult

    /**
     * Session not authenticated. The user needs to log in again.
     *
     * This occurs when [SessionState.ANONYMOUS] is detected, indicating
     * the stored credentials are invalid or expired.
     *
     * @property serverId The server ID with unauthenticated session
     */
    data class SessionNotConnected(val serverId: Int) : UrlLoadResult

    /**
     * Insecure (HTTP) connection was blocked based on user's security settings.
     *
     * The UI should show the blocked insecure connection screen with options
     * to configure home network detection or proceed anyway.
     *
     * @property serverId The server ID with blocked insecure connection
     * @property missingHomeSetup True if home network Wi-Fi SSID/BSSID is not configured
     * @property missingLocation True if location permission is needed for home network detection
     */
    data class InsecureBlocked(val serverId: Int, val missingHomeSetup: Boolean, val missingLocation: Boolean) :
        UrlLoadResult

    /**
     * Server has a plain-text (HTTP) URL but the user hasn't configured their security preference.
     *
     * The UI should show the security level configuration screen where the user
     * can choose to allow or block insecure connections. After configuration,
     * call [FrontendUrlManager.onSecurityLevelShown] to proceed.
     *
     * @property serverId The server ID requiring security level configuration
     */
    data class SecurityLevelRequired(val serverId: Int) : UrlLoadResult

    /**
     * No URL could be resolved for the server.
     *
     * This can occur when neither internal nor external URL is configured,
     * or when the URL cannot be parsed.
     *
     * @property serverId The server ID with no available URL
     */
    data class NoUrlAvailable(val serverId: Int) : UrlLoadResult
}
