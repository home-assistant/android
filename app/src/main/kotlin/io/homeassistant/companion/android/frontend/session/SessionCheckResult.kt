package io.homeassistant.companion.android.frontend.session

/**
 * Result of checking server session state.
 */
sealed interface SessionCheckResult {
    /** Session is authenticated and ready for use. */
    data object Connected : SessionCheckResult

    /** Session is not authenticated (anonymous or expired). */
    data object NotConnected : SessionCheckResult
}
