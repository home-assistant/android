package io.homeassistant.companion.android.common.data.wear.dashboard.model

import kotlinx.serialization.Serializable

/**
 * Maps a dashboard to the pages used by each native Wear surface.
 */
@Serializable
data class WearDashboardSurfaces(
    val tile: WearDashboardTileSurface? = null,
    val app: WearDashboardAppSurface? = null,
    val ongoingActivity: WearDashboardOngoingActivitySurface? = null,
)

/** Tile surface configuration referencing a compact dashboard page. */
@Serializable
data class WearDashboardTileSurface(val page: String)

/** Full Wear app surface configuration referencing a start page. */
@Serializable
data class WearDashboardAppSurface(val startPage: String)

/** Ongoing Activity surface configuration referencing an activity page. */
@Serializable
data class WearDashboardOngoingActivitySurface(val page: String)
