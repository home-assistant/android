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
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class SleepSensorWorkerTest {

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

        mockkObject(SensorReceiver)
        justRun { SensorReceiver.updateAllSensors(any()) }
    }

    @Test
    fun `Given sleep classify event when doWork then sleep confidence is updated and returns success`() = runTest {
        val worker = buildWorker(
            Data.Builder()
                .putInt("classify_confidence", 85)
                .putInt("classify_light", 2)
                .putInt("classify_motion", 1)
                .putLong("classify_timestamp", 1000L)
                .build(),
        )
        mockSensorEnabled("sleep_confidence", enabled = true)
        mockSensorExists("sleep_confidence")

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { sensorDao.update(match { it.state == "85" }) }
        coVerify(exactly = 1) { SensorReceiver.updateAllSensors(any()) }
    }

    @Test
    fun `Given sleep segment event when doWork then sleep segment is updated and returns success`() = runTest {
        val worker = buildWorker(
            Data.Builder()
                .putLong("segment_duration", 28800000L)
                .putLong("segment_start", 1000L)
                .putLong("segment_end", 28801000L)
                .putString("segment_status", "successful")
                .build(),
        )
        mockSensorEnabled("sleep_segment", enabled = true)
        mockSensorExists("sleep_segment")

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { sensorDao.update(match { it.state == "28800000" }) }
    }

    @Test
    fun `Given both sleep events when doWork then both sensors are updated and returns success`() = runTest {
        val worker = buildWorker(
            Data.Builder()
                .putInt("classify_confidence", 90)
                .putInt("classify_light", 1)
                .putInt("classify_motion", 0)
                .putLong("classify_timestamp", 500L)
                .putLong("segment_duration", 14400000L)
                .putLong("segment_start", 100L)
                .putLong("segment_end", 14400100L)
                .putString("segment_status", "successful")
                .build(),
        )
        mockSensorEnabled("sleep_confidence", enabled = true)
        mockSensorEnabled("sleep_segment", enabled = true)
        mockSensorExists("sleep_confidence")
        mockSensorExists("sleep_segment")

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { sensorDao.update(match { it.state == "90" }) }
        coVerify(exactly = 1) { sensorDao.update(match { it.state == "14400000" }) }
        coVerify(exactly = 1) { SensorReceiver.updateAllSensors(any()) }
    }

    @Test
    fun `Given disabled sleep confidence sensor when doWork then sensor is not updated and returns success`() = runTest {
        val worker = buildWorker(
            Data.Builder()
                .putInt("classify_confidence", 85)
                .putInt("classify_light", 2)
                .putInt("classify_motion", 1)
                .putLong("classify_timestamp", 1000L)
                .build(),
        )
        mockSensorEnabled("sleep_confidence", enabled = false)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { sensorDao.update(any()) }
        coVerify(exactly = 0) { SensorReceiver.updateAllSensors(any()) }
    }

    @Test
    fun `Given empty input data when doWork then returns success without updates`() = runTest {
        val worker = buildWorker(Data.EMPTY)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { sensorDao.update(any()) }
    }

    private fun buildWorker(inputData: Data): SleepSensorWorker {
        val workerParams: WorkerParameters = mockk(relaxed = true)
        every { workerParams.inputData } returns inputData
        return SleepSensorWorker(context, workerParams)
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
