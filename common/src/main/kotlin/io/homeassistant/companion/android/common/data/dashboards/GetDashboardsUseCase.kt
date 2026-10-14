package io.homeassistant.companion.android.common.data.dashboards

import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.PanelResponse
import javax.inject.Inject

private const val LOVELACE_COMPONENT = "lovelace"
private const val LEGACY_OVERVIEW_URL_PATH = "lovelace"
private const val HOME_URL_PATH = "home"

/** Dashboards built into Home Assistant, in the order the frontend lists them. */
private val BUILT_IN_DASHBOARDS = listOf(
    HOME_URL_PATH to R.string.dashboard_overview,
    "light" to R.string.dashboard_lights,
    "security" to R.string.dashboard_security,
    "climate" to R.string.dashboard_climate,
    "energy" to R.string.dashboard_energy,
    "maintenance" to R.string.dashboard_maintenance,
)

/**
 * Lists the dashboards of a server the current user can open, matching the list in Settings > Dashboards:
 * the built-in dashboards first, then the user created ones sorted by title.
 */
class GetDashboardsUseCase @Inject constructor(private val serverManager: ServerManager) {

    /** Returns the dashboards of the server [serverId], or `null` when they could not be retrieved. */
    suspend operator fun invoke(serverId: Int): List<Dashboard>? {
        val panels = serverManager.webSocketRepository(serverId).getPanels() ?: return null
        val panelsByUrlPath = panels.associateBy { it.urlPath }

        return legacyOverview(panelsByUrlPath) + builtInDashboards(panelsByUrlPath) + userDashboards(panels)
    }

    /**
     * Servers without the `home` dashboard (before 2026.x) use the untitled `lovelace` panel as their default
     * Overview dashboard.
     */
    private fun legacyOverview(panelsByUrlPath: Map<String, PanelResponse>): List<Dashboard> {
        val overview = panelsByUrlPath[LEGACY_OVERVIEW_URL_PATH]
        return if (HOME_URL_PATH !in panelsByUrlPath && overview != null && overview.title == null) {
            listOf(Dashboard(overview.urlPath, DashboardTitle.Resource(R.string.dashboard_overview)))
        } else {
            emptyList()
        }
    }

    private fun builtInDashboards(panelsByUrlPath: Map<String, PanelResponse>): List<Dashboard> =
        BUILT_IN_DASHBOARDS.mapNotNull { (urlPath, titleRes) ->
            panelsByUrlPath[urlPath]?.let { Dashboard(it.urlPath, DashboardTitle.Resource(titleRes)) }
        }

    private fun userDashboards(panels: List<PanelResponse>): List<Dashboard> = panels
        .mapNotNull { panel ->
            panel.title?.takeIf { panel.componentName == LOVELACE_COMPONENT }?.let {
                panel.urlPath to
                    it
            }
        }
        .sortedBy { (_, title) -> title.lowercase() }
        .map { (urlPath, title) -> Dashboard(urlPath, DashboardTitle.Text(title)) }
}
