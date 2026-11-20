package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.database.sensor.Sensor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val KEPT_ALIVE_ACTION = "test.KEPT_ALIVE"
private const val OTHER_ACTION = "test.OTHER"
private const val SENSOR_ID = "kept_alive_sensor"
private const val SERVER_ID = 1

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SensorReceiverBaseTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sensorUpdater: SensorUpdater = mockk(relaxed = true)
    private val sensorRepository: SensorRepository = mockk(relaxed = true)

    private val receiver = object : SensorReceiverBase() {
        override val skippableActions = emptyMap<String, List<String>>()
        override val keptAliveActions = mapOf(KEPT_ALIVE_ACTION to SENSOR_ID)
    }.also {
        it.serverManager = mockk(relaxed = true)
        it.sensorRepository = sensorRepository
        it.lastUpdateManager = mockk(relaxed = true)
        it.settingsDao = mockk(relaxed = true)
        it.sensorUpdater = sensorUpdater
    }

    private fun sensor(enabled: Boolean) = Sensor(id = SENSOR_ID, serverId = SERVER_ID, enabled = enabled, state = "")

    @Test
    fun `Given a broadcast Android can start the app for when received then only its sensor is updated`() = runTest {
        coEvery { sensorRepository.get(SENSOR_ID) } returns listOf(sensor(enabled = true))
        val updated = CompletableDeferred<Unit>()
        coEvery { sensorUpdater.updateSensor(SENSOR_ID) } coAnswers { updated.complete(Unit) }

        receiver.onReceive(context, Intent(KEPT_ALIVE_ACTION))
        updated.await()

        coVerify(exactly = 0) { sensorUpdater.updateSensors(any(), any(), any()) }
    }

    @Test
    fun `Given its sensor is disabled when such a broadcast is received then the sensor is only updated once enabled`() = runTest {
        val checked = CompletableDeferred<Unit>()
        coEvery { sensorRepository.get(SENSOR_ID) } coAnswers {
            checked.complete(Unit)
            listOf(sensor(enabled = false))
        }
        receiver.onReceive(context, Intent(KEPT_ALIVE_ACTION))
        checked.await()

        coEvery { sensorRepository.get(SENSOR_ID) } returns listOf(sensor(enabled = true))
        val updated = CompletableDeferred<Unit>()
        coEvery { sensorUpdater.updateSensor(SENSOR_ID) } coAnswers { updated.complete(Unit) }
        receiver.onReceive(context, Intent(KEPT_ALIVE_ACTION))
        updated.await()

        coVerify(exactly = 1) { sensorUpdater.updateSensor(SENSOR_ID) }
    }

    @Test
    fun `Given another broadcast when received then all the sensors are updated`() = runTest {
        val updated = CompletableDeferred<Unit>()
        coEvery { sensorUpdater.updateSensors(any(), any(), any()) } coAnswers { updated.complete(Unit) }

        receiver.onReceive(context, Intent(OTHER_ACTION))
        updated.await()

        coVerify(exactly = 0) { sensorUpdater.updateSensor(any()) }
    }
}
