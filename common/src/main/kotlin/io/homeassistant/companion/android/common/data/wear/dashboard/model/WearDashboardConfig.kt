package io.homeassistant.companion.android.common.data.wear.dashboard.model

import kotlinx.serialization.Serializable

/**
 * Top-level Wear Dashboard configuration persisted as canonical JSON.
 */
@Serializable
data class WearDashboardConfig(
    val version: Int = WearDashboardSchemaVersion.CURRENT_VERSION,
    val id: String,
    val title: String? = null,
    val pages: List<WearDashboardPage>,
    val surfaces: WearDashboardSurfaces = WearDashboardSurfaces(),
    val refreshPolicy: WearDashboardRefreshPolicy = WearDashboardRefreshPolicy.OnEnter,
)
