package io.homeassistant.companion.android.common.data.wear.dashboard.state

import kotlin.time.Instant

/**
 * Resolved binding values used when rendering a dashboard.
 */
data class WearDashboardResolvedState(
    val values: Map<String, ResolvedComponentValue> = emptyMap(),
    val isStale: Boolean = false,
    val lastUpdated: Instant? = null,
) {
    /** Display strings keyed by [io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardBindingKey]. */
    val bindingValues: Map<String, String> = values.mapValues { (_, value) -> value.toDisplayString() }
}
