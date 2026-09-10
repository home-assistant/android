package io.homeassistant.companion.android.frontend.url

/**
 * Query parameter names used in Home Assistant frontend URLs, shared across the code paths that
 * build or inspect these URLs so the literals are defined in a single place.
 */
internal object FrontendUrlParams {

    /**
     * Signals the Home Assistant frontend that authentication will be provided via the native
     * JavaScript bridge (`external_auth=1`).
     */
    const val EXTERNAL_AUTH = "external_auth"

    /** Opens the more-info dialog for the referenced entity id (Home Assistant 2025.6+). */
    const val MORE_INFO_ENTITY_ID = "more-info-entity-id"
}
