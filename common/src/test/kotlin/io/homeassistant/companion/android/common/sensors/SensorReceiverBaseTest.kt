package io.homeassistant.companion.android.common.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper.getMainLooper
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers the `goAsync()` contract of [SensorReceiverBase]: the broadcast's pending result must
 * not finish until the sensor update it triggered has completed. Without `goAsync()` the system
 * may kill the process as soon as `onReceive` returns, silently dropping the update — the
 * mechanism behind the BLE transmitter not resuming after boot or an app update (#7185).
 *
 * The receiver is exercised through a real Robolectric broadcast dispatch so a genuine
 * [BroadcastReceiver.PendingResult] is created for it, exactly as the system does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SensorReceiverBaseTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val serverManager = mockk<ServerManager>(relaxed = true)
    private val sensorRepository = mockk<SensorRepository>(relaxed = true)
    private val lastUpdateManager = mockk<LastUpdateManager>(relaxed = true)
    private val settingsDao = mockk<SettingsDao>(relaxed = true)
    private val sensorUpdater = mockk<SensorUpdater>(relaxed = true)

    private class TestSensorReceiver(
        serverManager: ServerManager,
        sensorRepository: SensorRepository,
        lastUpdateManager: LastUpdateManager,
        settingsDao: SettingsDao,
        sensorUpdater: SensorUpdater,
    ) : SensorReceiverBase() {
        init {
            this.serverManager = serverManager
            this.sensorRepository = sensorRepository
            this.lastUpdateManager = lastUpdateManager
            this.settingsDao = settingsDao
            this.sensorUpdater = sensorUpdater
        }

        override val skippableActions = emptyMap<String, List<String>>()
    }

    @Test
    fun `Given a sensor update in progress when the broadcast is received then the pending result finishes only after the update completes`() {
        coEvery { sensorRepository.get(any()) } returns emptyList()
        val updateStarted = CompletableDeferred<Unit>()
        val allowUpdateToFinish = CompletableDeferred<Unit>()
        coEvery { sensorUpdater.updateSensor(any()) } coAnswers {
            updateStarted.complete(Unit)
            allowUpdateToFinish.await()
        }

        val receiver = TestSensorReceiver(
            serverManager,
            sensorRepository,
            lastUpdateManager,
            settingsDao,
            sensorUpdater,
        )
        context.registerReceiver(
            receiver,
            IntentFilter(SensorReceiverBase.ACTION_UPDATE_SENSOR),
        )
        try {
            context.sendBroadcast(
                Intent(SensorReceiverBase.ACTION_UPDATE_SENSOR)
                    .putExtra(SensorReceiverBase.EXTRA_SENSOR_ID, "test_sensor"),
            )
            shadowOf(getMainLooper()).idle()

            // The update is running on Dispatchers.IO: the pending result must still be unfinished.
            runBlocking { withTimeout(10.seconds) { updateStarted.await() } }
            assertTrue(shadowOf(receiver).wentAsync())
            val pendingResult = shadowOf(receiver).originalPendingResult
            assertNotNull(pendingResult)
            val finished = shadowOf(pendingResult).future
            assertFalse(finished.isDone)

            allowUpdateToFinish.complete(Unit)
            finished.get(10, TimeUnit.SECONDS)
            assertTrue(finished.isDone)

            coVerify(exactly = 1) { sensorUpdater.updateSensor("test_sensor") }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
