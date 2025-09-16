package io.homeassistant.companion.android.onboarding.locationsharing

import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.testing.unit.ConsoleLogTree
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class LocationSharingViewModelTest {
    private val serverId = 42
    private val sensorDao: SensorDao = mockk(relaxUnitFun = true)
    private val integrationRepository: IntegrationRepository = mockk()

    private val serverManager: ServerManager = mockk {
        coEvery { integrationRepository(serverId) } returns integrationRepository
    }
    private lateinit var viewModel: LocationSharingViewModel

    private val locationSensorIds = listOf(
        "location_background",
        "zone_background",
        "accurate_location",
    )

    @BeforeEach
    fun setup() {
        Timber.plant(ConsoleLogTree)
        ConsoleLogTree.verbose = true // As per reference

        viewModel = LocationSharingViewModel(
            serverId = serverId,
            sensorDao = sensorDao,
            serverManager = serverManager,
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given location sharing is set when setupLocationSensor is invoked then sensors are set and insecure connection is set`() = runTest {
        val enabled = true

        viewModel.setupLocationSensor(enabled)

        runCurrent()

        coVerify {
            sensorDao.setSensorsEnabled(
                sensorIds = locationSensorIds,
                serverId = serverId,
                enabled = enabled,
            )
            serverManager.integrationRepository(serverId)
            integrationRepository.setAllowInsecureConnection(!enabled)
        }
    }

    @Test
    fun `Given sensorDao throws exception When setupLocationSensor is called Then exception is caught`() = runTest {
        val enabled = true
        val exception = RuntimeException("Test exception from sensorDao")
        coEvery { sensorDao.setSensorsEnabled(any(), any(), any()) } throws exception

        viewModel.setupLocationSensor(enabled)
        runCurrent()

        coVerify {
            sensorDao.setSensorsEnabled(
                sensorIds = locationSensorIds,
                serverId = serverId,
                enabled = enabled,
            )
        }
        coVerify(exactly = 0) {
            serverManager.integrationRepository(serverId)
            integrationRepository.setAllowInsecureConnection(any())
        }
    }

    @Test
    fun `Given integrationRepository throws exception When setupLocationSensor is called Then exception is caught`() = runTest {
        val enabled = true
        val exception = RuntimeException("Test exception from integrationRepository")
        coEvery { integrationRepository.setAllowInsecureConnection(any()) } throws exception

        viewModel.setupLocationSensor(enabled)
        runCurrent()

        coVerify {
            sensorDao.setSensorsEnabled(
                sensorIds = locationSensorIds,
                serverId = serverId,
                enabled = enabled,
            )
            serverManager.integrationRepository(serverId)
            integrationRepository.setAllowInsecureConnection(!enabled)
        }
    }
}
