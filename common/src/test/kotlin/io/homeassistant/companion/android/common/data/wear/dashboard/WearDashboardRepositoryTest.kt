package io.homeassistant.companion.android.common.data.wear.dashboard

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRepositoryImpl.Companion.PREF_WEAR_DASHBOARDS
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRepositoryImpl.Companion.PREF_WEAR_DASHBOARD_TILES
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.model.wearDashboardJson
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WearDashboardRepositoryTest {

    private val localStorage = mockk<LocalStorage>()
    private lateinit var repository: WearDashboardRepository

    @BeforeEach
    fun setup() {
        repository = WearDashboardRepositoryImpl(localStorage)
    }

    @Test
    fun `Given dashboard config when storing then it serializes into wear dashboards preference`() = runTest {
        val slot = slot<String>()
        coEvery { localStorage.getString(PREF_WEAR_DASHBOARDS) } returns null
        coJustRun { localStorage.putString(PREF_WEAR_DASHBOARDS, capture(slot)) }

        repository.setDashboard(WearDashboardTestFixtures.carDashboard)

        val stored = wearDashboardJson.decodeFromString<Map<String, WearDashboardConfig>>(slot.captured)
        assertEquals(WearDashboardTestFixtures.carDashboard, stored["car"])
    }

    @Test
    fun `Given stored dashboards when reading then configs deserialize from preference`() = runTest {
        val json = wearDashboardJson.encodeToString(mapOf("car" to WearDashboardTestFixtures.carDashboard))
        coEvery { localStorage.getString(PREF_WEAR_DASHBOARDS) } returns json

        val result = repository.getAllDashboards()

        assertEquals(WearDashboardTestFixtures.carDashboard, result["car"])
    }

    @Test
    fun `Given invalid dashboard json when reading then empty map is returned`() = runTest {
        coEvery { localStorage.getString(PREF_WEAR_DASHBOARDS) } returns "{ invalid"

        val result = repository.getAllDashboards()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `Given tile mapping when storing then it serializes into tile preference`() = runTest {
        val slot = slot<String>()
        coEvery { localStorage.getString(PREF_WEAR_DASHBOARD_TILES) } returns null
        coJustRun { localStorage.putString(PREF_WEAR_DASHBOARD_TILES, capture(slot)) }

        repository.setTileDashboard(tileId = 3, dashboardId = "car")

        assertEquals("""{"3":"car"}""", slot.captured)
    }

    @Test
    fun `Given stored tile mapping when reading then mapping deserializes from preference`() = runTest {
        coEvery { localStorage.getString(PREF_WEAR_DASHBOARD_TILES) } returns """{"1":"car","2":"home"}"""

        val result = repository.getTileDashboardMapping()

        assertEquals(mapOf(1 to "car", 2 to "home"), result)
    }

    @Test
    fun `Given dashboard id when removing then config is deleted and returned`() = runTest {
        val json = wearDashboardJson.encodeToString(mapOf("car" to WearDashboardTestFixtures.carDashboard))
        coEvery { localStorage.getString(PREF_WEAR_DASHBOARDS) } returnsMany listOf(
            json,
            "{}",
        )
        coJustRun { localStorage.putString(PREF_WEAR_DASHBOARDS, any()) }

        val removed = repository.removeDashboard("car")

        assertEquals(WearDashboardTestFixtures.carDashboard, removed)
        coVerify { localStorage.putString(PREF_WEAR_DASHBOARDS, "{}") }
    }

    @Test
    fun `Given missing dashboard id when removing then null is returned`() = runTest {
        coEvery { localStorage.getString(PREF_WEAR_DASHBOARDS) } returns "{}"

        val removed = repository.removeDashboard("missing")

        assertNull(removed)
    }

    @Test
    fun `Given unknown tile id mapping when saving tile id then assignment migrates to tile`() = runTest {
        coEvery { localStorage.getString(PREF_WEAR_DASHBOARD_TILES) } returnsMany listOf(
            """{"-1":"car"}""",
            """{"3":"car"}""",
        )
        coJustRun { localStorage.putString(PREF_WEAR_DASHBOARD_TILES, any()) }

        val result = repository.getDashboardTileAssignmentAndSaveTileId(tileId = 3)

        assertEquals("car", result)
        coVerify { localStorage.putString(PREF_WEAR_DASHBOARD_TILES, """{"3":"car"}""") }
    }

    @Test
    fun `Given tile assignment when removing dashboard tile then mapping is cleared`() = runTest {
        coEvery { localStorage.getString(PREF_WEAR_DASHBOARD_TILES) } returnsMany listOf(
            """{"3":"car"}""",
            "{}",
        )
        coJustRun { localStorage.putString(PREF_WEAR_DASHBOARD_TILES, any()) }

        repository.removeDashboardTileAssignment(tileId = 3)

        coVerify { localStorage.putString(PREF_WEAR_DASHBOARD_TILES, "{}") }
    }
}
