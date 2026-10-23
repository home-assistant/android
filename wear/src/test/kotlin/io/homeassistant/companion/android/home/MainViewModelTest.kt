package io.homeassistant.companion.android.home

import android.app.Application
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.wear.CameraTileDao
import io.homeassistant.companion.android.database.wear.FavoriteCachesDao
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
        managers = emptySet(),
        application = mockk<Application>(relaxed = true),
    )

    private fun fakeEntity(entityId: String) = Entity(
        entityId = entityId,
        state = "",
        attributes = emptyMap(),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )

    private fun fakeItem(entityId: String) = EntityDisplayWithContext(
        EntityDisplayWithoutContext(entityId = entityId, name = entityId, icon = mockk()),
    )

    /** Makes the manager emit the given entities that pass the filter of the caller, as the real one does. */
    private fun givenServerEntities(vararg entityIds: String) {
        every { entitiesForDisplayManager.observeInContext(ServerManager.SERVER_ID_ACTIVE, any()) } answers {
            val filter = secondArg<(Entity) -> Boolean>()
            val items = entityIds.filter { filter(fakeEntity(it)) }.map { fakeItem(it) }
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
}
