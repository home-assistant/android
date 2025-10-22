package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SensorManagerTest {

    @Test
    fun `Given attributes when invoking onSensorUpdated then attributes are replaced in DAO properly formatted in json`() = runTest {
        val context: Context = mockk()
        val sensorManager = FakeSensorManager()
        val appDatabase: AppDatabase = mockk()
        val sensorDao = mockk<SensorDao>()

        mockkObject(AppDatabase.Companion)

        every { AppDatabase.getInstance(context) } returns appDatabase
        every { appDatabase.sensorDao() } returns sensorDao
        coEvery { sensorDao.get("test") } returns listOf(
            Sensor(
                id = "test",
                serverId = 0,
                enabled = true,
                state = "test",
            ),
        )
        coJustRun { sensorDao.update(any()) }
        val slot = slot<List<Attribute>>()
        coJustRun { sensorDao.replaceAllAttributes(any(), capture(slot)) }

        sensorManager.onSensorUpdated(
            context,
            SensorManager.BasicSensor("test", "test"),
            "test",
            "test",
            mapOf("test" to listOf("test", "hello")),
        )

        assertEquals("""["test","hello"]""", slot.captured.first().value)

        sensorManager.onSensorUpdated(
            context,
            SensorManager.BasicSensor("test", "test"),
            "test",
            "test",
            mapOf("test" to listOf(true, false)),
        )

        assertEquals("""[true,false]""", slot.captured.first().value)

        sensorManager.onSensorUpdated(
            context,
            SensorManager.BasicSensor("test", "test"),
            "test",
            "test",
            mapOf("test" to true),
        )

        assertEquals("""true""", slot.captured.first().value)
    }
}
private class FakeSensorManager : SensorManager {
    override val name: Int = -1

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        TODO("Not needed")
    }

    override suspend fun requestSensorUpdate(context: Context) {
        TODO("Not needed")
    }

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        TODO("Not needed")
    }
}
