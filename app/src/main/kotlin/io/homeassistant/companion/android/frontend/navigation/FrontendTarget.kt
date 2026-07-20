package io.homeassistant.companion.android.frontend.navigation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

private const val ENTITY_ID_PREFIX = "entityId:"

/**
 * A destination within the Home Assistant frontend.
 */
sealed interface FrontendTarget : Parcelable {

    /** Open the server's default dashboard with no specific path. */
    @Parcelize
    data object Default : FrontendTarget

    /** Navigate to a relative path or URL within the frontend (e.g. `/lovelace/0`). */
    @Parcelize
    data class Path(val path: String) : FrontendTarget

    /** Open the more-info dialog for [entityId] (e.g. `light.kitchen`). */
    @Parcelize
    data class EntityMoreInfo(val entityId: String) : FrontendTarget

    companion object {
        /**
         * Parses a raw path string into a [FrontendTarget].
         *
         * A `null` or blank path maps to [Default].
         */
        fun fromRawPath(path: String?): FrontendTarget {
            val trimmed = path?.trim()
            return when {
                trimmed.isNullOrEmpty() -> Default
                // Matched ignoring case and surrounding spaces since the value is typed by hand
                trimmed.startsWith(ENTITY_ID_PREFIX, ignoreCase = true) ->
                    EntityMoreInfo(trimmed.substring(ENTITY_ID_PREFIX.length).trim())
                else -> Path(trimmed)
            }
        }

        /**
         * Serializes this target back to the raw path string
         * Returns `null` for [FrontendTarget.Default].
         */
        internal fun FrontendTarget.toRawPath(): String? = when (this) {
            Default -> null
            is Path -> path
            is EntityMoreInfo -> "$ENTITY_ID_PREFIX$entityId"
        }
    }
}
