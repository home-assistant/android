package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
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

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SensorReceiverBaseTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sensorUpdater: SensorUpdater = mockk(relaxed = true)

    private val receiver = object : SensorReceiverBase() {
        override val skippableActions = emptyMap<String, List<String>>()
        override val keptAliveActions = mapOf(KEPT_ALIVE_ACTION to SENSOR_ID)
    }.also {
        it.serverManager = mockk(relaxed = true)
        it.sensorRepository = mockk(relaxed = true)
        it.lastUpdateManager = mockk(relaxed = true)
        it.settingsDao = mockk(relaxed = true)
        it.sensorUpdater = sensorUpdater
    }

    @Test
    fun `Given a broadcast Android can start the app for when received then only its sensor is updated`() = runTest {
        val updated = CompletableDeferred<Unit>()
        coEvery { sensorUpdater.updateSensor(SENSOR_ID) } coAnswers { updated.complete(Unit) }

        receiver.onReceive(context, Intent(KEPT_ALIVE_ACTION))
        updated.await()

        coVerify(exactly = 0) { sensorUpdater.updateSensors(any(), any(), any()) }
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
