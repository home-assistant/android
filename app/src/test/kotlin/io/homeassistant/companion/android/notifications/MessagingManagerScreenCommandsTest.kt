package io.homeassistant.companion.android.notifications

import android.app.ActivityManager
import android.app.Application
import android.os.Looper
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.ScreenOffAdminRequestActivity
import io.homeassistant.companion.android.util.ScreenOffHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers the routing of the screen on and screen off commands only, [MessagingManager] has no
 * further coverage yet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MessagingManagerScreenCommandsTest {

    private lateinit var screenOffHelper: ScreenOffHelper
    private lateinit var messagingManager: MessagingManager

    @Before
    fun setUp() {
        val serverManager = mockk<ServerManager>(relaxed = true)
        val integrationRepository = mockk<IntegrationRepository>(relaxed = true)
        coEvery { serverManager.getServer(any<Int>()) } returns mockk<Server>(relaxed = true)
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.isTrusted() } returns true
        screenOffHelper = mockk(relaxed = true)

        messagingManager = MessagingManager(
            context = ApplicationProvider.getApplicationContext<Application>(),
            okHttpClientProvider = mockk(relaxed = true),
            serverManager = serverManager,
            prefsRepository = mockk(relaxed = true),
            notificationDao = mockk(relaxed = true),
            sensorRepository = mockk(relaxed = true),
            settingsDao = mockk(relaxed = true),
            textToSpeechClient = mockk(relaxed = true),
            flashlightHelper = mockk(relaxed = true),
            screenOffHelper = screenOffHelper,
            permissionRequestMediator = mockk(relaxed = true),
            assistConfigManager = mockk(relaxed = true),
            defaultAssistantManager = mockk(relaxed = true),
            bluetoothSensorManager = mockk(relaxed = true),
        )
    }

    private fun handleMessage(message: String) {
        messagingManager.handleMessage(mapOf(NotificationData.MESSAGE to message), "FCM")
        // handleMessage launches on the main dispatcher, run the enqueued work before verifying
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `Given screen off is possible when receiving screen off command then the screen is turned off`() {
        every { screenOffHelper.canTurnScreenOff() } returns true
        every { screenOffHelper.turnScreenOff() } returns true

        handleMessage(MessagingManager.COMMAND_SCREEN_OFF)

        verify(exactly = 1) { screenOffHelper.turnScreenOff() }
    }

    @Test
    fun `Given screen off is not possible when receiving screen off command then the screen is not turned off`() {
        every { screenOffHelper.canTurnScreenOff() } returns false

        handleMessage(MessagingManager.COMMAND_SCREEN_OFF)

        verify(exactly = 0) { screenOffHelper.turnScreenOff() }
    }

    @Test
    fun `Given screen was turned off when receiving screen on command then the screen is turned back on`() {
        handleMessage(MessagingManager.COMMAND_SCREEN_ON)

        verify(exactly = 1) { screenOffHelper.turnScreenOn() }
    }

    @Test
    fun `Given the app in the foreground when screen off is not possible then the device admin activation is opened`() {
        every { screenOffHelper.canTurnScreenOff() } returns false
        val application = ApplicationProvider.getApplicationContext<Application>()
        val processInfo = ActivityManager.RunningAppProcessInfo(
            application.applicationInfo.processName,
            Process.myPid(),
            null,
        ).apply { importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
        shadowOf(application.getSystemService(ActivityManager::class.java)).setProcesses(listOf(processInfo))

        handleMessage(MessagingManager.COMMAND_SCREEN_OFF)

        assertEquals(
            ScreenOffAdminRequestActivity::class.java.name,
            shadowOf(application).nextStartedActivity?.component?.className,
        )
    }
}
