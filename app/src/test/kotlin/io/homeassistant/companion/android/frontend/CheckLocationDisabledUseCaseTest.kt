package io.homeassistant.companion.android.frontend

import android.Manifest
import android.content.Context
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.util.CheckLocationDisabledUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class CheckLocationDisabledUseCaseTest {

    private val context: Context = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)

    private lateinit var useCase: CheckLocationDisabledUseCase

    @BeforeEach
    fun setUp() {
        mockkObject(DisabledLocationHandler)
        mockkObject(SensorReceiver)
        every { SensorReceiver.MANAGERS } returns emptyList()
        every { DisabledLocationHandler.removeLocationDisabledWarning(any()) } just runs
        every { DisabledLocationHandler.showLocationDisabledNotification(any(), any()) } just runs
        useCase = CheckLocationDisabledUseCase(
            context = context,
            serverManager = serverManager,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(DisabledLocationHandler)
        unmockkObject(SensorReceiver)
    }

    @Test
    fun `Given location is enabled when invoked then removes warning`() = runTest {
        every { DisabledLocationHandler.isLocationEnabled(context) } returns true

        useCase()

        coVerify { DisabledLocationHandler.removeLocationDisabledWarning(context) }
        coVerify(exactly = 0) {
            DisabledLocationHandler.showLocationDisabledNotification(any(), any())
        }
    }

    @Test
    fun `Given location is disabled and SSID is used when invoked then shows notification`() = runTest {
        every { DisabledLocationHandler.isLocationEnabled(context) } returns false
        coEvery { serverManager.getServer(any<Int>()) } returns serverWithSsids(listOf("HomeWifi"))

        useCase()

        coVerify {
            DisabledLocationHandler.showLocationDisabledNotification(context, any())
        }
    }

    @Test
    fun `Given location is disabled and sensor requires location when invoked then shows notification`() = runTest {
        val locationSensor = BasicSensor(id = "test_sensor", type = "sensor")
        val sensorManager: io.homeassistant.companion.android.common.sensors.SensorManager =
            mockk(relaxed = true)

        every { DisabledLocationHandler.isLocationEnabled(context) } returns false
        coEvery { serverManager.getServer(any<Int>()) } returns serverWithSsids(emptyList())
        every { SensorReceiver.MANAGERS } returns listOf(sensorManager)
        coEvery { sensorManager.getAvailableSensors(context) } returns listOf(locationSensor)
        coEvery { sensorManager.isEnabled(context, locationSensor) } returns true
        every { sensorManager.requiredPermissions(context, "test_sensor") } returns arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        useCase()

        coVerify {
            DisabledLocationHandler.showLocationDisabledNotification(context, any())
        }
    }

    @Test
    fun `Given location is disabled but no feature needs it when invoked then removes warning`() = runTest {
        every { DisabledLocationHandler.isLocationEnabled(context) } returns false
        coEvery { serverManager.getServer(any<Int>()) } returns serverWithSsids(emptyList())

        useCase()

        coVerify { DisabledLocationHandler.removeLocationDisabledWarning(context) }
        coVerify(exactly = 0) {
            DisabledLocationHandler.showLocationDisabledNotification(any(), any())
        }
    }

    @Test
    fun `Given sensor does not require location when invoked then removes warning`() = runTest {
        val nonLocationSensor = BasicSensor(id = "battery_sensor", type = "sensor")
        val sensorManager: io.homeassistant.companion.android.common.sensors.SensorManager =
            mockk(relaxed = true)

        every { DisabledLocationHandler.isLocationEnabled(context) } returns false
        coEvery { serverManager.getServer(any<Int>()) } returns serverWithSsids(emptyList())
        every { SensorReceiver.MANAGERS } returns listOf(sensorManager)
        coEvery { sensorManager.getAvailableSensors(context) } returns listOf(nonLocationSensor)
        coEvery { sensorManager.isEnabled(context, nonLocationSensor) } returns true
        every { sensorManager.requiredPermissions(context, "battery_sensor") } returns emptyArray()

        useCase()

        coVerify { DisabledLocationHandler.removeLocationDisabledWarning(context) }
        coVerify(exactly = 0) {
            DisabledLocationHandler.showLocationDisabledNotification(any(), any())
        }
    }

    @Test
    fun `Given sensor requires location but is disabled when invoked then removes warning`() = runTest {
        val locationSensor = BasicSensor(id = "gps_sensor", type = "sensor")
        val sensorManager: io.homeassistant.companion.android.common.sensors.SensorManager =
            mockk(relaxed = true)

        every { DisabledLocationHandler.isLocationEnabled(context) } returns false
        coEvery { serverManager.getServer(any<Int>()) } returns serverWithSsids(emptyList())
        every { SensorReceiver.MANAGERS } returns listOf(sensorManager)
        coEvery { sensorManager.getAvailableSensors(context) } returns listOf(locationSensor)
        coEvery { sensorManager.isEnabled(context, locationSensor) } returns false

        useCase()

        coVerify { DisabledLocationHandler.removeLocationDisabledWarning(context) }
        coVerify(exactly = 0) {
            DisabledLocationHandler.showLocationDisabledNotification(any(), any())
        }
    }
}

private fun serverWithSsids(ssids: List<String>): Server = Server(
    id = 1,
    _name = "Test Server",
    connection = ServerConnectionInfo(
        externalUrl = "https://example.com",
        internalSsids = ssids,
    ),
    session = ServerSessionInfo(),
    user = ServerUserInfo(),
)
