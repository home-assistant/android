package io.homeassistant.companion.android.websocket

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltTestApplication
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
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class WebsocketManagerTest {

    private val powerManager = mockk<PowerManager>()
    private val context = mockk<Context>(relaxed = true) {
        every { applicationContext } returns this
        every { getSystemService(NotificationManager::class.java) } returns mockk<NotificationManager>(relaxed = true)
        every { getSystemService(PowerManager::class.java) } returns powerManager
    }

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
        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any(), WebsocketManager.WebsocketManagerEntryPoint::class.java)
        } returns
            entryPoint
    }

    @Test
    fun `Given setting NEVER when job runs then finishes without checks`() = runTest {
        mockSetting(WebsocketSetting.NEVER)
        val worker = TestListenableWorkerBuilder<WebsocketManager>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting
        coVerify(exactly = 1) { entryPoint.dao.get(any()) }

        // Has not run other settings' checks
        verify(exactly = 0) {
            context.hasActiveConnection()
            context.getSystemService(PowerManager::class.java)
        }
        coVerify(exactly = 0) {
            entryPoint.serverManager.isRegistered()
            entryPoint.serverManager.connectionStateProvider(any())
        }
    }

    @Test
    fun `Given setting ALWAYS when job runs without connection then finishes without screen and network checks`() = runTest {
        mockSetting(WebsocketSetting.ALWAYS)
        every { context.hasActiveConnection() } returns false

        val worker = TestListenableWorkerBuilder<WebsocketManager>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting
        coVerify(exactly = 1) { entryPoint.dao.get(any()) }
        verify(exactly = 1) { context.hasActiveConnection() }

        // Has not run other settings' checks
        verify(exactly = 0) { context.getSystemService(PowerManager::class.java) }
        coVerify(exactly = 0) { entryPoint.serverManager.connectionStateProvider(any()) }
    }

    @Test
    fun `Given setting ALWAYS when job runs without registration then finishes without screen and network checks`() = runTest {
        mockSetting(WebsocketSetting.ALWAYS)
        every { context.hasActiveConnection() } returns true
        coEvery { entryPoint.serverManager.isRegistered() } returns false

        val worker = TestListenableWorkerBuilder<WebsocketManager>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting
        coVerify(exactly = 1) {
            entryPoint.dao.get(any())
            entryPoint.serverManager.isRegistered()
        }

        // Has not run other settings' checks
        verify(exactly = 0) { context.getSystemService(PowerManager::class.java) }
        coVerify(exactly = 0) { entryPoint.serverManager.connectionStateProvider(any()) }
    }

    @Test
    fun `Given setting SCREEN_ON when job runs with screen off then finishes without other checks`() = runTest {
        mockSetting(WebsocketSetting.SCREEN_ON)
        every { context.hasActiveConnection() } returns true
        coEvery { entryPoint.serverManager.isRegistered() } returns true
        every { powerManager.isInteractive } returns false

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
    fun `Given setting HOME_WIFI when job runs without home Wi-Fi then finishes without other checks`() = runTest {
        mockSetting(WebsocketSetting.HOME_WIFI)
        every { context.hasActiveConnection() } returns true
        coEvery { entryPoint.serverManager.isRegistered() } returns true
        coEvery { entryPoint.serverManager.connectionStateProvider(any()).isInternal(any()) } returns false

        val worker = spyk(TestListenableWorkerBuilder<WebsocketManager>(context).build())
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting
        coVerify(exactly = 1) {
            entryPoint.dao.get(any())
            entryPoint.serverManager.connectionStateProvider(any()).isInternal(any())
        }

        // Has not run other settings' checks or tried to run worker
        verify(exactly = 0) { context.getSystemService(PowerManager::class.java) }
        coVerify(exactly = 0) { worker.setForeground(any()) }
    }

    @Test
    fun `Given setting ALWAYS when job runs then does not run other setting checks`() = runTest {
        mockSetting(WebsocketSetting.ALWAYS)
        every { context.hasActiveConnection() } returns true
        coEvery { entryPoint.serverManager.isRegistered() } returns true

        mockkConstructor(NotificationCompat.Builder::class)
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk<Notification>()

        val worker = spyk(TestListenableWorkerBuilder<WebsocketManager>(context).build())
        coEvery { worker.setForeground(any()) } throws CancellationException() // Prevent worker from running
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Has checked setting and tried to run worker
        coVerify(exactly = 1) {
            entryPoint.dao.get(any())
            worker.setForeground(any())
        }

        // Has not run other settings' checks
        verify(exactly = 0) { context.getSystemService(PowerManager::class.java) }
        coVerify(exactly = 0) { entryPoint.serverManager.connectionStateProvider(any()) }
    }
}
