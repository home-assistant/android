package io.homeassistant.companion.android.sensors

import android.content.Context
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
            SensorManager.BasicSensor("test", "test"),
            "test",
            "test",
            mapOf("test" to listOf("test", "hello")),
        )

        assertEquals("""["test","hello"]""", slot.captured.first().value)

        sensorManager.onSensorUpdated(
            SensorManager.BasicSensor("test", "test"),
            "test",
            "test",
            mapOf("test" to listOf(true, false)),
        )

        assertEquals("""[true,false]""", slot.captured.first().value)

        sensorManager.onSensorUpdated(
            SensorManager.BasicSensor("test", "test"),
            "test",
            "test",
            mapOf("test" to true),
        )

        assertEquals("""true""", slot.captured.first().value)
    }
}

private class FakeSensorManager(
    override val applicationContext: Context = mockk(),
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager = mockk(),
) : SensorManager {
    override val name: Int = -1

    override fun requiredPermissions(sensorId: String): Array<String> {
        TODO("Not needed")
    }

    override suspend fun requestSensorUpdate() {
        TODO("Not needed")
    }

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        TODO("Not needed")
    }
}
