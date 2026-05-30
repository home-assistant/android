package io.homeassistant.companion.android.widgets

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardCapabilities
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardResolvedState

/**
 * Placeholder for future Wear home-screen widgets backed by Wear Dashboard configs.
 *
 * Migration path: replace this stub with [androidx.glance](https://developer.android.com/jetpack/glance)
 * or Wear Remote Compose when [WEAR_WIDGETS_ENABLED] is enabled, reusing the same config and state
 * cache as tiles and the full-screen Compose dashboard.
 */
object WearDashboardWidgetRenderer {

    /** Feature flag gating Wear widget rendering until Remote Compose / Glance support lands. */
    const val WEAR_WIDGETS_ENABLED: Boolean = false

    /**
     * Renders a dashboard page for a Wear widget surface.
     *
     * @return `null` while [WEAR_WIDGETS_ENABLED] is false.
     */
    @Suppress("UNUSED_PARAMETER")
    fun render(
        config: WearDashboardConfig,
        pageId: String,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
    ): Nothing? {
        if (!WEAR_WIDGETS_ENABLED) return null
        return null
    }
}
