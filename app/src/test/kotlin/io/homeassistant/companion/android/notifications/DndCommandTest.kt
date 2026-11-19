package io.homeassistant.companion.android.notifications

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.modes.HomeAssistantMode
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.common.sensors.DNDSensorManager
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SOURCE = "test"

/** Tests the `mode_on` and `mode_off` values of `command_dnd`, through [MessagingManager.handleMessage]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class DndCommandTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val serverManager: ServerManager = mockk(relaxed = true) {
        coEvery { getServer(any<Int>()) } returns mockk(relaxed = true)
        coEvery { integrationRepository(any()).isTrusted() } returns true
    }
    private val notificationDao: NotificationDao = mockk(relaxed = true)
    private val homeAssistantMode: HomeAssistantMode = mockk {
        every { isSupported() } returns true
        coEvery { setActive(any()) } returns HomeAssistantMode.ChangeResult.Applied
    }

    private val messagingManager = MessagingManager(
        context = context,
        okHttpClientProvider = mockk(relaxed = true),
        serverManager = serverManager,
        prefsRepository = mockk(relaxed = true),
        notificationDao = notificationDao,
        sensorRepository = mockk(relaxed = true),
        settingsDao = mockk(relaxed = true),
        textToSpeechClient = mockk(relaxed = true),
        flashlightHelper = mockk(relaxed = true),
        permissionRequestMediator = mockk(relaxed = true),
        assistConfigManager = mockk(relaxed = true),
        defaultAssistantManager = mockk(relaxed = true),
        bluetoothSensorManager = mockk(relaxed = true),
        homeAssistantMode = homeAssistantMode,
        clock = FakeClock(),
    )

    @Before
    fun setUp() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        // In the foreground, where a command that misses the access opens Android's screen to grant it
        val appProcess = ActivityManager.RunningAppProcessInfo().apply {
            processName = context.applicationInfo.processName
            importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
        shadowOf(context.getSystemService(ActivityManager::class.java)).setProcesses(listOf(appProcess))
        mockkObject(SensorReceiver.Companion)
        every { SensorReceiver.updateSensor(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkObject(SensorReceiver.Companion)
    }

    private fun receive(command: String) {
        messagingManager.handleMessage(
            mapOf(NotificationData.MESSAGE to MessagingManager.COMMAND_DND, NotificationData.COMMAND to command),
            SOURCE,
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** @return the action of the screen the app opened, if any */
    private fun openedScreen(): String? = shadowOf(context as Application).peekNextStartedActivity()?.action

    @Test
    fun `Given mode_on when received then the mode is turned on and its sensor reports it right away`() {
        receive(MessagingManager.DND_MODE_ON)

        coVerify(exactly = 1) { homeAssistantMode.setActive(true) }
        verify(exactly = 1) { SensorReceiver.updateSensor(any(), DNDSensorManager.homeAssistantModeSensor.id) }
    }

    @Test
    fun `Given the mode is not added when mode_on is received then its sensor has nothing to report`() {
        coEvery { homeAssistantMode.setActive(any()) } returns HomeAssistantMode.ChangeResult.NotAdded

        receive(MessagingManager.DND_MODE_ON)

        verify(exactly = 0) { SensorReceiver.updateSensor(any(), any()) }
    }

    @Test
    fun `Given mode_off when received then the mode is turned off`() {
        receive(MessagingManager.DND_MODE_OFF)

        coVerify(exactly = 1) { homeAssistantMode.setActive(false) }
    }

    @Test
    fun `Given a device without the mode when mode_on is received then it is ignored without a notification`() {
        every { homeAssistantMode.isSupported() } returns false

        receive(MessagingManager.DND_MODE_ON)

        coVerify(exactly = 0) { homeAssistantMode.setActive(any()) }
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun `Given no Do Not Disturb access when mode_on is received then the user is not asked for the access`() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)
        coEvery { homeAssistantMode.setActive(any()) } returns HomeAssistantMode.ChangeResult.NoAccess

        receive(MessagingManager.DND_MODE_ON)

        assertNull(openedScreen())
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun `Given no Do Not Disturb access when an existing DND value is received then the user is asked for the access`() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)

        receive(MessagingManager.DND_PRIORITY_ONLY)

        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, openedScreen())
        coVerify(exactly = 0) { homeAssistantMode.setActive(any()) }
    }

    @Test
    fun `Given an existing DND value when received then the mode is not touched`() {
        receive(MessagingManager.DND_PRIORITY_ONLY)

        coVerify(exactly = 0) { homeAssistantMode.setActive(any()) }
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notificationManager.currentInterruptionFilter)
    }
}
