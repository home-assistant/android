package io.homeassistant.companion.android.sensors

import android.content.Context
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.FlaskEmpty
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SensorManagerTest {

    @Test
    fun `Given attributes when invoking onSensorUpdated then attributes are replaced in DAO properly formatted in json`() = runTest {
        val sensorRepository = mockk<SensorRepository>()
        val sensorManager = FakeSensorManager(sensorRepository = sensorRepository)
        coEvery { sensorRepository.get("test") } returns listOf(
            Sensor(
                id = "test",
                serverId = 0,
                enabled = true,
                state = "test",
            ),
        )
        coJustRun { sensorRepository.update(any()) }
        val slot = slot<List<Attribute>>()
        coJustRun { sensorRepository.replaceAllAttributes(any(), capture(slot)) }

        sensorManager.onSensorUpdated(
            SensorManager.BasicSensor("test", "test", statelessIcon = Mdi.FlaskEmpty),
            "test",
            Mdi.FlaskEmpty,
            mapOf("test" to listOf("test", "hello")),
        )

        assertEquals("""["test","hello"]""", slot.captured.first().value)

        sensorManager.onSensorUpdated(
            SensorManager.BasicSensor("test", "test", statelessIcon = Mdi.FlaskEmpty),
            "test",
            Mdi.FlaskEmpty,
            mapOf("test" to listOf(true, false)),
        )

        assertEquals("""[true,false]""", slot.captured.first().value)

        sensorManager.onSensorUpdated(
            SensorManager.BasicSensor("test", "test", statelessIcon = Mdi.FlaskEmpty),
            "test",
            Mdi.FlaskEmpty,
            mapOf("test" to true),
        )

        assertEquals("""true""", slot.captured.first().value)
    }

    @Test
    fun `Given manager owning the sensor on a supporting device when invoking hasBasicSensor then returns true`() = runTest {
        val sensorManager = FakeSensorManager(availableSensors = listOf(basicSensor))

        assertTrue(sensorManager.hasBasicSensor(basicSensor.id))
    }

    @Test
    fun `Given manager owning the sensor on a device not supporting it when invoking hasBasicSensor then returns false`() = runTest {
        val sensorManager = FakeSensorManager(availableSensors = listOf(basicSensor), hasSensor = false)

        assertFalse(sensorManager.hasBasicSensor(basicSensor.id))
    }

    @Test
    fun `Given manager not owning the sensor when invoking hasBasicSensor then returns false`() = runTest {
        val sensorManager = FakeSensorManager(availableSensors = listOf(basicSensor))

        assertFalse(sensorManager.hasBasicSensor("other"))
    }
}

private val basicSensor = SensorManager.BasicSensor("test", "test", statelessIcon = Mdi.FlaskEmpty)

private class FakeSensorManager(
    override val applicationContext: Context = mockk(),
    override val sensorRepository: SensorRepository = mockk(),
    override val serverManager: ServerManager = mockk(),
    private val availableSensors: List<SensorManager.BasicSensor> = emptyList(),
    private val hasSensor: Boolean = true,
) : SensorManager {
    override val name: Int = -1

    override fun requiredPermissions(sensorId: String): Array<String> {
        TODO("Not needed")
    }

    override suspend fun requestSensorUpdate() {
        TODO("Not needed")
    }

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> = availableSensors

    override fun hasSensor(): Boolean = hasSensor
}
