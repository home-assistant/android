package io.homeassistant.companion.android.common.data.wear.dashboard.state

import kotlinx.coroutines.flow.Flow

/**
 * In-memory cache of resolved dashboard state keyed by dashboard ID.
 */
interface WearDashboardStateCache {

    /** Returns the latest cached state for [dashboardId], or `null` when none is cached. */
    fun getState(dashboardId: String): WearDashboardResolvedState?

    /** Emits cached state updates for [dashboardId]. */
    fun observeState(dashboardId: String): Flow<WearDashboardResolvedState?>

    /** Stores [state] for [dashboardId] and notifies observers. */
    suspend fun updateState(dashboardId: String, state: WearDashboardResolvedState)

    /** Removes cached state for [dashboardId]. */
    suspend fun clearState(dashboardId: String)

    /** Clears all cached dashboard state. */
    suspend fun clearAll()
}
