package io.homeassistant.companion.android.onboarding.locationsharing

import io.homeassistant.companion.android.common.sensors.SensorRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class LocationSharingViewModelTest {
    private val serverId = 42
    private val sensorRepository: SensorRepository = mockk(relaxUnitFun = true)

    private lateinit var viewModel: LocationSharingViewModel

    private val locationSensorIds = listOf(
        "location_background",
        "zone_background",
        "accurate_location",
    )

    @BeforeEach
    fun setup() {
        viewModel = LocationSharingViewModel(
            serverId = serverId,
            sensorRepository = sensorRepository,
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given location sharing is set when setupLocationSensor is invoked then sensors are set and insecure connection is set`() = runTest {
        val enabled = true

        viewModel.setupLocationSensor(enabled)

        runCurrent()

        coVerify {
            sensorRepository.setSensorsEnabled(
                sensorIds = locationSensorIds,
                serverId = serverId,
                enabled = enabled,
            )
        }
    }

    @Test
    fun `Given repository throws exception When setupLocationSensor is called Then exception is caught`() = runTest {
        val enabled = true
        val exception = RuntimeException("Test exception from repository")
        coEvery { sensorRepository.setSensorsEnabled(any(), any(), any()) } throws exception

        viewModel.setupLocationSensor(enabled)
        runCurrent()

        coVerify {
            sensorRepository.setSensorsEnabled(
                sensorIds = locationSensorIds,
                serverId = serverId,
                enabled = enabled,
            )
        }
    }
}
