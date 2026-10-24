package io.homeassistant.companion.android.tiles

import android.os.Build
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.wear.CameraTile
import io.homeassistant.companion.android.database.wear.CameraTileDao
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.database.wear.ThermostatTileDao
import io.mockk.called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class RefreshIntervalMigrationTest {

    private val cameraTileDao = mockk<CameraTileDao>(relaxed = true)
    private val thermostatTileDao = mockk<ThermostatTileDao>(relaxed = true)
    private val wearPrefsRepository = mockk<WearPrefsRepository>(relaxed = true)

    private val migration = RefreshIntervalMigration(cameraTileDao, thermostatTileDao, wearPrefsRepository)

    @AfterEach
    fun tearDown() {
        SdkVersion.resetSdkInt()
        unmockkAll()
    }

    @Test
    fun `Given stored when viewed intervals When migrating on Wear OS 6 Then only they are rewritten to never`() = runTest {
        SdkVersion.sdkInt = Build.VERSION_CODES.BAKLAVA
        val onViewedCamera = CameraTile(id = 1, entityId = "camera.door", refreshInterval = 1)
        val timedCamera = CameraTile(id = 2, entityId = "camera.yard", refreshInterval = 300)
        val onViewedThermostat = ThermostatTile(id = 3, entityId = "climate.living", refreshInterval = 1)
        coEvery { cameraTileDao.getAllFlow() } returns flowOf(listOf(onViewedCamera, timedCamera))
        coEvery { thermostatTileDao.getAllFlow() } returns flowOf(listOf(onViewedThermostat))
        coEvery { wearPrefsRepository.getAllTemplateTiles() } returns mapOf(
            4 to TemplateTileConfig(template = "{{ now() }}", refreshInterval = 1),
            5 to TemplateTileConfig(template = "{{ states.sensor }}", refreshInterval = 0),
        )

        migration.migrate()

        coVerify(exactly = 1) { cameraTileDao.add(onViewedCamera.copy(refreshInterval = 0)) }
        coVerify(exactly = 0) { cameraTileDao.add(timedCamera) }
        coVerify(exactly = 1) { thermostatTileDao.add(onViewedThermostat.copy(refreshInterval = 0)) }
        coVerify(exactly = 1) {
            wearPrefsRepository.setTemplateTile(4, "{{ now() }}", refreshInterval = 0)
        }
        coVerify(exactly = 0) { wearPrefsRepository.setTemplateTile(5, any(), any()) }
    }

    @Test
    fun `Given a device supporting when viewed interval When migrating Then nothing is read or written`() = runTest {
        SdkVersion.sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        migration.migrate()

        verify { listOf(cameraTileDao, thermostatTileDao, wearPrefsRepository) wasNot called }
    }
}
