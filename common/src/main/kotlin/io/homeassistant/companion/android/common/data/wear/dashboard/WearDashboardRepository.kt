package io.homeassistant.companion.android.common.data.wear.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig

/**
 * Persists Wear Dashboard configurations and tile-to-dashboard mappings.
 */
interface WearDashboardRepository {

    /** Returns all stored dashboard configs keyed by dashboard ID. */
    suspend fun getAllDashboards(): Map<String, WearDashboardConfig>

    /** Returns the dashboard config for [id], or `null` when it is not stored. */
    suspend fun getDashboard(id: String): WearDashboardConfig?

    /** Stores or replaces a single dashboard config keyed by [WearDashboardConfig.id]. */
    suspend fun setDashboard(config: WearDashboardConfig)

    /** Replaces all stored dashboard configs with [configs]. */
    suspend fun setAllDashboards(configs: Map<String, WearDashboardConfig>)

    /**
     * Removes the dashboard config for [id].
     *
     * @return The removed config, or `null` when [id] was not stored.
     */
    suspend fun removeDashboard(id: String): WearDashboardConfig?

    /** Returns tile instance IDs mapped to their assigned dashboard ID. */
    suspend fun getTileDashboardMapping(): Map<Int, String>

    /** Assigns [dashboardId] to the Wear Tile instance identified by [tileId]. */
    suspend fun setTileDashboard(tileId: Int, dashboardId: String)

    /** Removes the dashboard assignment for [tileId], if present. */
    suspend fun removeTileDashboard(tileId: Int)

    /**
     * Returns the dashboard ID assigned to [tileId], migrating a legacy unknown tile ID when needed.
     *
     * @return The assigned dashboard ID, or `null` when this tile has no dashboard assignment.
     */
    suspend fun getDashboardTileAssignmentAndSaveTileId(tileId: Int): String?

    /** Removes the dashboard assignment for [tileId], if present. */
    suspend fun removeDashboardTileAssignment(tileId: Int)
}
