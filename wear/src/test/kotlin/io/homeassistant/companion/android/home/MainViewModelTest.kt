package io.homeassistant.companion.android.home

import android.app.Application
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TileUpdateRequester
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.wear.CameraTileDao
import io.homeassistant.companion.android.database.wear.FavoriteCachesDao
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.tiles.thermostatSettingsMutex
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherJUnit5Extension::class)
class MainViewModelTest {

    private val entitiesForDisplayManager: EntitiesForDisplayManager = mockk()
    private val favoritesDao: FavoritesDao = mockk {
        every { getAllFavoritesFlow() } returns flowOf(emptyList())
    }
    private val favoriteCachesDao: FavoriteCachesDao = mockk {
        coEvery { getAll() } returns emptyList()
    }
    private val sensorRepository: SensorRepository = mockk {
        every { getAllFlow() } returns flowOf(emptyList())
    }
    private val cameraTileDao: CameraTileDao = mockk {
        every { getAllFlow() } returns flowOf(emptyList())
    }
    private val thermostatTileDao: ThermostatTileDao = mockk {
        every { getAllFlow() } returns flowOf(emptyList())
    }

    private fun createViewModel() = MainViewModel(
        entitiesForDisplayManager = entitiesForDisplayManager,
        favoritesDao = favoritesDao,
        favoriteCachesDao = favoriteCachesDao,
        sensorRepository = sensorRepository,
        cameraTileDao = cameraTileDao,
        thermostatTileDao = thermostatTileDao,
        wearPrefsRepository = mockk(relaxed = true),
        managers = emptySet(),
        application = mockk<Application>(relaxed = true),
    )

    private fun fakeItem(entityId: String) = EntityDisplayWithContext(
        EntityDisplayWithoutContext(entityId = entityId, name = entityId, icon = mockk()),
    )

    /** Makes the manager emit the given entities selected by the filter of the caller, as the real one does. */
    private fun givenServerEntities(vararg entityIds: String) {
        every { entitiesForDisplayManager.observeInContext(ServerManager.SERVER_ID_ACTIVE, any()) } answers {
            val filter = secondArg<EntityFilter>()
            val items = entityIds.filter { filter.matches(it) }.map { fakeItem(it) }
            flowOf(EntityDisplayState.Loaded(items))
        }
    }

    @Test
    fun `Given camera, climate and light entities when observing entities then camera and climate are only offered to the tiles`() = runTest {
        givenServerEntities("camera.door", "climate.living", "light.kitchen", "sensor.temperature")
        val viewModel = createViewModel()

        viewModel.observeEntities()

        val uiState = viewModel.mainViewUiState.value
        assertEquals(listOf("camera.door"), uiState.cameraItems.map { it.entityId })
        assertEquals(listOf("climate.living"), uiState.climateItems.map { it.entityId })
        assertEquals(setOf("light.kitchen"), uiState.displayItems.keys)
        assertEquals(setOf("light"), uiState.allDisplayItemsByDomain.keys)
    }

    @Test
    fun `Given missing thermostat tile when selecting an entity then an enhanced configuration is created`() = runTest {
        mockkStatic(TileService::class) {
            val updater = mockk<TileUpdateRequester>(relaxed = true)
            every { TileService.getUpdater(any()) } returns updater
            coEvery { thermostatTileDao.get(1) } returns null
            coJustRun { thermostatTileDao.add(any()) }

            createViewModel().setThermostatTileEntity(1, "climate.living").join()

            coVerify(exactly = 1) {
                thermostatTileDao.add(ThermostatTile(id = 1, entityId = "climate.living", layoutStyle = "enhanced"))
            }
            verify(exactly = 1) { updater.requestUpdate(any()) }
        }
    }

    @Test
    fun `Given existing thermostat tile when editing settings then other preferences remain and refresh runs outside the lock`() = runTest {
        mockkStatic(TileService::class) {
            val updater = mockk<TileUpdateRequester>()
            every { TileService.getUpdater(any()) } returns updater
            every { updater.requestUpdate(any()) } answers { assertFalse(thermostatSettingsMutex.isLocked) }
            val existing = ThermostatTile(id = 1, entityId = "climate.living", refreshInterval = 60L)
            coEvery { thermostatTileDao.get(1) } returns existing
            coJustRun { thermostatTileDao.add(any()) }

            createViewModel().setThermostatTileShowName(1, false).join()

            coVerify(exactly = 1) { thermostatTileDao.add(existing.copy(showEntityName = false)) }
            verify(exactly = 1) { updater.requestUpdate(any()) }
        }
    }
}
