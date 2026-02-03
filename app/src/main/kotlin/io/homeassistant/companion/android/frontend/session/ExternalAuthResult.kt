package io.homeassistant.companion.android.frontend.session

import io.homeassistant.companion.android.frontend.error.FrontendError

/**
 * Result of retrieving external authentication.
 */
sealed interface ExternalAuthResult {
    /**
     * Auth retrieved successfully.
     *
     * @property callbackScript JavaScript callback to execute with auth token
     */
    data class Success(val callbackScript: String) : ExternalAuthResult

    /**
     * Auth retrieval failed.
     *
     * @property callbackScript JavaScript callback to execute with failure status
     * @property error Non-null if anonymous session - otherwise it is an unknown error
     */
    data class Failed(val callbackScript: String, val error: FrontendError?) : ExternalAuthResult
}
