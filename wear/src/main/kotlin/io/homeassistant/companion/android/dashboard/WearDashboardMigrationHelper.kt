package io.homeassistant.companion.android.dashboard

import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAppSurface
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardBinding
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardComponent
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardPage
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardRefreshPolicy
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardSurfaces
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardTileSurface

private const val MIGRATED_PAGE_ID = "main"
private const val MIGRATED_DASHBOARD_ID_PREFIX = "template_tile_"

/**
 * Optional helpers to migrate legacy Wear template tiles into dashboard configs.
 */
object WearDashboardMigrationHelper {

    /**
     * Builds a minimal dashboard config from legacy [template] tile text, or `null` when empty.
     */
    fun dashboardFromTemplateTile(tileId: Int, template: TemplateTileConfig): WearDashboardConfig? {
        if (template.template.isBlank()) return null
        val dashboardId = "$MIGRATED_DASHBOARD_ID_PREFIX$tileId"
        return WearDashboardConfig(
            id = dashboardId,
            title = "Template tile $tileId",
            pages = listOf(
                WearDashboardPage(
                    id = MIGRATED_PAGE_ID,
                    root = WearDashboardComponent.Text(
                        id = "template_text",
                        text = WearDashboardBinding.Template(template = template.template),
                    ),
                ),
            ),
            surfaces = WearDashboardSurfaces(
                tile = WearDashboardTileSurface(page = MIGRATED_PAGE_ID),
                app = WearDashboardAppSurface(startPage = MIGRATED_PAGE_ID),
            ),
            refreshPolicy = WearDashboardRefreshPolicy.Interval(template.refreshInterval.coerceAtLeast(1)),
        )
    }
}
