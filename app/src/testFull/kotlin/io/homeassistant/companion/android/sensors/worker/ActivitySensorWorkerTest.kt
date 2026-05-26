package io.homeassistant.companion.android.sensors.worker

import android.content.Context
import android.content.pm.PackageManager
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class ActivitySensorWorkerTest {

    private val context: Context = mockk()
    private val sensorDao: SensorDao = mockk()
    private val serverManager: ServerManager = mockk()
    private val server = mockk<Server>(relaxed = true).apply { every { id } returns 1 }

    @BeforeEach
    fun setup() {
        every { context.applicationContext } returns context
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED

        mockkStatic(EntryPointAccessors::class)
        val entryPoint = mockk<SensorManager.SensorManagerEntryPoint>()
        every {
            EntryPointAccessors.fromApplication(context, SensorManager.SensorManagerEntryPoint::class.java)
        } returns entryPoint
        every { entryPoint.sensorDao() } returns sensorDao
        every { entryPoint.serverManager() } returns serverManager

        coEvery { serverManager.servers() } returns listOf(server)
    }

    @Test
    fun `Given valid activity data when doWork then sensor is updated and returns success`() = runTest {
        val worker = buildWorker(
            Data.Builder()
                .putString("activity_type", "walking")
                .putStringArray("confidence_keys", arrayOf("walking", "still"))
                .putIntArray("confidence_values", intArrayOf(80, 20))
                .build(),
        )
        mockSensorEnabled("detected_activity", enabled = true)
        mockSensorExists("detected_activity")

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { sensorDao.update(match { it.state == "walking" }) }
    }

    @Test
    fun `Given disabled activity sensor when doWork then sensor is not updated and returns success`() = runTest {
        val worker = buildWorker(
            Data.Builder()
                .putString("activity_type", "walking")
                .putStringArray("confidence_keys", arrayOf("walking"))
                .putIntArray("confidence_values", intArrayOf(80))
                .build(),
        )
        mockSensorEnabled("detected_activity", enabled = false)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { sensorDao.update(any()) }
    }

    @Test
    fun `Given missing activity type when doWork then returns failure`() = runTest {
        val worker = buildWorker(Data.EMPTY)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    private fun buildWorker(inputData: Data): ActivitySensorWorker {
        val workerParams: WorkerParameters = mockk(relaxed = true)
        every { workerParams.inputData } returns inputData
        return ActivitySensorWorker(context, workerParams)
    }

    private fun mockSensorEnabled(sensorId: String, enabled: Boolean) {
        coEvery {
            sensorDao.getAnyIsEnabled(sensorId, any(), any(), any())
        } returns enabled
    }

    private fun mockSensorExists(sensorId: String) {
        coEvery { sensorDao.get(sensorId) } returns listOf(
            Sensor(id = sensorId, serverId = 1, enabled = true, state = ""),
        )
        coJustRun { sensorDao.update(any()) }
        coJustRun { sensorDao.replaceAllAttributes(any(), any()) }
    }
}
