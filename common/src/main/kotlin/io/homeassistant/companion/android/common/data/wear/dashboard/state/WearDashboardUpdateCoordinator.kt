package io.homeassistant.companion.android.common.data.wear.dashboard.state

import kotlinx.coroutines.flow.SharedFlow

/**
 * Coordinates Home Assistant subscriptions and writes resolved dashboard state to the cache.
 */
interface WearDashboardUpdateCoordinator {

    /**
     * Starts tracking state for [dashboardId] using the stored dashboard configuration.
     *
     * Calling this again for the same ID restarts subscriptions.
     */
    fun startTracking(dashboardId: String)

    /** Stops tracking and cancels subscriptions for [dashboardId]. */
    fun stopTracking(dashboardId: String)

    /** Stops tracking for all dashboards. */
    fun stopAll()

    /**
     * Emits dashboard IDs that should request a Wear Tile update.
     *
     * Emissions are throttled to at most one request every two seconds per dashboard.
     */
    val tileUpdateRequests: SharedFlow<String>

    /** Forces an immediate refresh of cached state for [dashboardId]. */
    suspend fun refreshNow(dashboardId: String)
}
