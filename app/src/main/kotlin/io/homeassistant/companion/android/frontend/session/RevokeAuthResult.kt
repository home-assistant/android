package io.homeassistant.companion.android.frontend.session

/**
 * Result of revoking authentication.
 */
sealed interface RevokeAuthResult {
    /**
     * Session revoked successfully.
     *
     * @property callbackScript JavaScript callback to execute with success status
     */
    data class Success(val callbackScript: String) : RevokeAuthResult

    /**
     * Session revocation failed.
     *
     * @property callbackScript JavaScript callback to execute with failure status
     */
    data class Failed(val callbackScript: String) : RevokeAuthResult
}
