package io.homeassistant.companion.android.common.data.wear.dashboard

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardCapabilities
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardResolvedState

/**
 * Renders a Wear Dashboard page into a native surface-specific representation.
 *
 * @param T The renderer output type, such as a ProtoLayout [LayoutElement] or Compose UI tree.
 */
interface WearDashboardRenderer<T> {
    /**
     * Renders the requested page from [config] using resolved [state] and device [capabilities].
     */
    fun render(
        config: WearDashboardConfig,
        pageId: String,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
    ): T
}
