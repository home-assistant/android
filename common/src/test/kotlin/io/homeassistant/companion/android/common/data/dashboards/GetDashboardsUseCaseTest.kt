package io.homeassistant.companion.android.common.data.dashboards

import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.PanelResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val SERVER_ID = 1

class GetDashboardsUseCaseTest {

    private val webSocketRepository: WebSocketRepository = mockk()
    private val serverManager: ServerManager = mockk()
    private val getDashboards = GetDashboardsUseCase(serverManager)

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.webSocketRepository(SERVER_ID) } returns webSocketRepository
    }

    @Test
    fun `Given the panels of a current server when listing dashboards then built-in ones come first and user ones are sorted by title`() = runTest {
        coEvery { webSocketRepository.getPanels() } returns listOf(
            panel("config", "config", "config"),
            panel("lovelace-heating", "lovelace", "Home"),
            panel("dashboard-weather", "lovelace", "Weather"),
            panel("map", "lovelace", "Map"),
            panel("lovelace", "lovelace", null),
            panel("light", "light", "light"),
            panel("security", "security", "security"),
            panel("climate", "climate", "climate"),
            panel("home", "home", "home"),
            panel("maintenance", "maintenance", "maintenance"),
            panel("profile", "profile", null),
            panel("logbook", "logbook", "logbook"),
            panel("history", "history", "history"),
            panel("energy", "energy", "energy"),
            panel("hacs", "custom", "HACS"),
        )

        assertEquals(
            listOf(
                Dashboard("home", DashboardTitle.Resource(R.string.dashboard_overview)),
                Dashboard("light", DashboardTitle.Resource(R.string.dashboard_lights)),
                Dashboard("security", DashboardTitle.Resource(R.string.dashboard_security)),
                Dashboard("climate", DashboardTitle.Resource(R.string.dashboard_climate)),
                Dashboard("energy", DashboardTitle.Resource(R.string.dashboard_energy)),
                Dashboard("maintenance", DashboardTitle.Resource(R.string.dashboard_maintenance)),
                Dashboard("lovelace-heating", DashboardTitle.Text("Home")),
                Dashboard("map", DashboardTitle.Text("Map")),
                Dashboard("dashboard-weather", DashboardTitle.Text("Weather")),
            ),
            getDashboards(SERVER_ID),
        )
    }

    @Test
    fun `Given a server without the home dashboard when listing dashboards then the default lovelace panel is the Overview`() = runTest {
        coEvery { webSocketRepository.getPanels() } returns listOf(
            panel("lovelace", "lovelace", null),
            panel("energy", "energy", "energy"),
            panel("garden", "lovelace", "garden"),
        )

        assertEquals(
            listOf(
                Dashboard("lovelace", DashboardTitle.Resource(R.string.dashboard_overview)),
                Dashboard("energy", DashboardTitle.Resource(R.string.dashboard_energy)),
                Dashboard("garden", DashboardTitle.Text("garden")),
            ),
            getDashboards(SERVER_ID),
        )
    }

    @Test
    fun `Given the panels cannot be retrieved when listing dashboards then returns null`() = runTest {
        coEvery { webSocketRepository.getPanels() } returns null

        assertNull(getDashboards(SERVER_ID))
    }

    private fun panel(urlPath: String, componentName: String, title: String?) = PanelResponse(urlPath = urlPath, componentName = componentName, title = title)
}
