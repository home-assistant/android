package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.test.core.app.ApplicationProvider
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.CheckLocalNetworkPermissionUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SensorWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sensorUpdater = mockk<SensorUpdater>(relaxed = true)
    private val foregroundUpdater = mockk<ForegroundUpdater> {
        every { setForegroundAsync(any(), any(), any()) } returns
            CallbackToFutureAdapter.getFuture { completer -> completer.set(null) }
    }
    private var isAlreadyInForeground = false

    private val entryPoint = object : SensorWorker.SensorWorkerEntryPoint {
        val serverManager = mockk<ServerManager> {
            coEvery { servers() } returns emptyList()
        }
        val checkLocalNetworkPermission = mockk<CheckLocalNetworkPermissionUseCase> {
            coEvery { this@mockk.invoke() } returns true
        }
        val sensorRepository = mockk<SensorRepository>().also { repository ->
            coEvery { repository.getEnabledCount() } returns 1
            coEvery { repository.get(LastUpdateManager.lastUpdate.id) } returns emptyList()
            coJustRun { repository.removeSensorsExceptServers(any()) }
        }

        override fun serverManager(): ServerManager = serverManager
        override fun checkLocalNetworkPermission(): CheckLocalNetworkPermissionUseCase = checkLocalNetworkPermission
        override fun lastUpdateManager(): LastUpdateManager = mockk()
        override fun sensorRepository(): SensorRepository = sensorRepository
        override fun sensorUpdater(): SensorUpdater = sensorUpdater
        override fun workerForegroundStateProvider(): WorkerForegroundStateProvider = WorkerForegroundStateProvider { isAlreadyInForeground }
    }

    @Before
    fun setup() {
        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any(), SensorWorker.SensorWorkerEntryPoint::class.java)
        } returns entryPoint
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given already in foreground when doing work then updates sensors without starting a foreground service`() = runTest {
        isAlreadyInForeground = true

        val result = sensorWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { foregroundUpdater.setForegroundAsync(any(), any(), any()) }
        coVerify(exactly = 1) { sensorUpdater.updateSensors() }
    }

    @Test
    fun `Given not in foreground when doing work then updates sensors in a dataSync foreground service`() = runTest {
        isAlreadyInForeground = false

        val result = sensorWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) {
            foregroundUpdater.setForegroundAsync(
                any(),
                any(),
                match { it.foregroundServiceType == ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC },
            )
        }
        coVerify(exactly = 1) { sensorUpdater.updateSensors() }
    }

    private fun sensorWorker(): SensorWorker = TestListenableWorkerBuilder<SensorWorker>(context)
        .setForegroundUpdater(foregroundUpdater)
        .build()
}
