package io.homeassistant.companion.android.websocket

import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.hilt.android.EntryPointAccessors
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.notifications.MessagingManager
import io.homeassistant.companion.android.util.hasActiveConnection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebsocketManagerTest {

    private lateinit var realContext: Context
    private lateinit var context: Context

    private val entryPoint = object : WebsocketManager.WebsocketManagerEntryPoint {
        val dao = mockk<SettingsDao>()
        val messagingManager = mockk<MessagingManager>()
        val serverManager = mockk<ServerManager>(relaxed = true).apply {
            coEvery { servers() } returns listOf(mockk<Server>(relaxed = true))
            // Test does not cover websocket monitoring right now, failsafe to end quickly if it tries
            coEvery { webSocketRepository(any()) } throws IllegalStateException("Test should not interact with WS")
        }

        override fun serverManager(): ServerManager = serverManager
        override fun messagingManager(): MessagingManager = messagingManager
        override fun settingsDao(): SettingsDao = dao
    }

    private fun mockSetting(setting: WebsocketSetting) {
        coEvery { entryPoint.dao.get(any()) } returns Setting(
            id = 1,
            websocketSetting = setting,
            sensorUpdateFrequency = SensorUpdateFrequencySetting.NORMAL,
        )
    }

    @Before
    fun setup() {
        realContext = ApplicationProvider.getApplicationContext()
        context = spyk(realContext)
        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any(), WebsocketManager.WebsocketManagerEntryPoint::class.java)
        } returns
            entryPoint
    }

    @Test
    fun givenSettingNever_whenJobRuns_thenFinishesWithoutChecks() = runTest {
        mockSetting(WebsocketSetting.NEVER)
        val worker = TestListenableWorkerBuilder<WebsocketManager>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting
        coVerify(exactly = 1) { entryPoint.dao.get(any()) }

        // Has not run other settings' checks
        verify(exactly = 0) { context.hasActiveConnection() }
        coVerify(exactly = 0) { entryPoint.serverManager.isRegistered() }
        verify(exactly = 0) { context.getSystemService(Context.POWER_SERVICE) }
        coVerify(exactly = 0) { entryPoint.serverManager.connectionStateProvider(any()) }
    }

    @Test
    fun givenSettingNotNever_whenJobRunsWithoutConnection_thenFinishesWithoutScreenNetworkChecks() = runTest {
        mockSetting(WebsocketSetting.ALWAYS)
        every { context.hasActiveConnection() } returns false

        val worker = TestListenableWorkerBuilder<WebsocketManager>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting
        coVerify(exactly = 1) { entryPoint.dao.get(any()) }
        verify(exactly = 1) { context.hasActiveConnection() }

        // Has not run other settings' checks
        verify(exactly = 0) { context.getSystemService(Context.POWER_SERVICE) }
        coVerify(exactly = 0) { entryPoint.serverManager.connectionStateProvider(any()) }
    }

    @Test
    fun givenSettingNotNever_whenJobRunsWithoutRegistration_thenFinishesWithoutScreenNetworkChecks() = runTest {
        mockSetting(WebsocketSetting.ALWAYS)
        every { context.hasActiveConnection() } returns true
        coEvery { entryPoint.serverManager.isRegistered() } returns false

        val worker = spyk(TestListenableWorkerBuilder<WebsocketManager>(context).build())
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting
        coVerify(exactly = 1) { entryPoint.dao.get(any()) }
        coVerify(exactly = 1) { entryPoint.serverManager.isRegistered() }

        // Has not run other settings' checks
        verify(exactly = 0) { context.getSystemService(Context.POWER_SERVICE) }
        coVerify(exactly = 0) { entryPoint.serverManager.connectionStateProvider(any()) }
    }

    @Test
    fun givenSettingScreenOn_whenJobRunsWithScreenOff_thenFinishesWithoutOtherChecks() = runTest {
        mockSetting(WebsocketSetting.SCREEN_ON)
        every { context.hasActiveConnection() } returns true
        coEvery { entryPoint.serverManager.isRegistered() } returns true
        val powerManager = mockk<PowerManager>().apply {
            every { isInteractive } returns false
        }
        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager

        val worker = spyk(TestListenableWorkerBuilder<WebsocketManager>(context).build())
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting
        coVerify(exactly = 1) { entryPoint.dao.get(any()) }
        verify(exactly = 1) { powerManager.isInteractive }

        // Has not run other settings' checks or tried to run worker
        coVerify(exactly = 0) { entryPoint.serverManager.connectionStateProvider(any()) }
        coVerify(exactly = 0) { worker.setForeground(any()) }
    }

    @Test
    fun givenSettingHomeWifi_whenJobRunsWithoutHomeWifi_thenFinishesWithoutOtherChecks() = runTest {
        mockSetting(WebsocketSetting.HOME_WIFI)
        every { context.hasActiveConnection() } returns true
        coEvery { entryPoint.serverManager.isRegistered() } returns true
        coEvery { entryPoint.serverManager.connectionStateProvider(any()).isInternal(any()) } returns false

        val worker = spyk(TestListenableWorkerBuilder<WebsocketManager>(context).build())
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting
        coVerify(exactly = 1) { entryPoint.dao.get(any()) }
        coVerify(exactly = 1) { entryPoint.serverManager.connectionStateProvider(any()).isInternal(any()) }

        // Has not run other settings' checks or tried to run worker
        verify(exactly = 0) { context.getSystemService(Context.POWER_SERVICE) }
        coVerify(exactly = 0) { worker.setForeground(any()) }
    }

    @Test
    fun givenSettingAlways_whenJobRuns_thenDoesNotRunOtherSettingChecks() = runTest {
        mockSetting(WebsocketSetting.ALWAYS)
        every { context.hasActiveConnection() } returns true
        coEvery { entryPoint.serverManager.isRegistered() } returns true

        val worker = spyk(TestListenableWorkerBuilder<WebsocketManager>(context).build())
        coEvery { worker.setForeground(any()) } throws CancellationException() // Prevent worker from running
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting
        coVerify(exactly = 1) { entryPoint.dao.get(any()) }

        // Has not run other settings' checks
        verify(exactly = 0) { context.getSystemService(Context.POWER_SERVICE) }
        coVerify(exactly = 0) { entryPoint.serverManager.connectionStateProvider(any()) }

        // Has tried to run worker
        coVerify(exactly = 1) { worker.setForeground(any()) }
    }
}
