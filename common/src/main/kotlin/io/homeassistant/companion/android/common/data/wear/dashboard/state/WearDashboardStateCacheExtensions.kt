package io.homeassistant.companion.android.common.data.wear.dashboard.state

/** Returns cached resolved state for [dashboardId], or `null` when none is cached. */
fun WearDashboardStateCache.getResolvedState(dashboardId: String): WearDashboardResolvedState? =
    getState(dashboardId)
