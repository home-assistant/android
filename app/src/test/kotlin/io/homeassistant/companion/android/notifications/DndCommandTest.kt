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
import io.homeassistant.companion.android.common.modes.HomeAssistantModeManager
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val homeAssistantModeManager: HomeAssistantModeManager = mockk {
        every { isSupported() } returns true
        coEvery { setActive(any()) } returns HomeAssistantModeManager.ChangeResult.Applied
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
        homeAssistantModeManager = homeAssistantModeManager,
        clock = FakeClock(),
    )

    @Before
    fun setUp() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        // In the background, where a command that misses the access posts a notification asking for it
        setAppImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)
    }

    /** Puts the app at [importance], which decides how a command that misses the access asks for it. */
    private fun setAppImportance(importance: Int) {
        val appProcess = ActivityManager.RunningAppProcessInfo().apply {
            processName = context.applicationInfo.processName
            this.importance = importance
        }
        shadowOf(context.getSystemService(ActivityManager::class.java)).setProcesses(listOf(appProcess))
    }

    /**
     * @return whether the app started posting a notification: it creates the notification's channel first, on the main
     * thread, while the rest of the notification is built on other threads the test cannot wait for
     */
    private fun postsNotification(): Boolean = notificationManager.notificationChannels.isNotEmpty()

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
    fun `Given mode_on when received then the mode is turned on`() {
        receive(MessagingManager.DND_MODE_ON)

        coVerify(exactly = 1) { homeAssistantModeManager.setActive(true) }
    }

    @Test
    fun `Given the mode is not added when mode_on is received then no notification is posted`() {
        coEvery { homeAssistantModeManager.setActive(any()) } returns HomeAssistantModeManager.ChangeResult.NotAdded

        receive(MessagingManager.DND_MODE_ON)

        assertFalse(postsNotification())
    }

    @Test
    fun `Given mode_off when received then the mode is turned off`() {
        receive(MessagingManager.DND_MODE_OFF)

        coVerify(exactly = 1) { homeAssistantModeManager.setActive(false) }
    }

    @Test
    fun `Given a device without the mode when mode_on is received then it is ignored without a notification`() {
        every { homeAssistantModeManager.isSupported() } returns false

        receive(MessagingManager.DND_MODE_ON)

        coVerify(exactly = 0) { homeAssistantModeManager.setActive(any()) }
        assertFalse(postsNotification())
    }

    @Test
    fun `Given an invalid value when received then it is posted as a notification`() {
        receive("not_a_dnd_value")

        assertTrue(postsNotification())
    }

    @Test
    fun `Given no Do Not Disturb access when mode_on is received then the user is not asked for the access`() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)
        coEvery { homeAssistantModeManager.setActive(any()) } returns HomeAssistantModeManager.ChangeResult.NoAccess

        receive(MessagingManager.DND_MODE_ON)

        assertNull(openedScreen())
        assertFalse(postsNotification())
    }

    @Test
    fun `Given no Do Not Disturb access in the background when an existing DND value is received then a notification asks for it`() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)

        receive(MessagingManager.DND_PRIORITY_ONLY)

        assertTrue(postsNotification())
    }

    @Test
    fun `Given no Do Not Disturb access in the foreground when an existing DND value is received then the access screen opens`() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)
        setAppImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)

        receive(MessagingManager.DND_PRIORITY_ONLY)

        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, openedScreen())
        coVerify(exactly = 0) { homeAssistantModeManager.setActive(any()) }
    }

    @Test
    fun `Given an existing DND value when received then the mode is not touched`() {
        receive(MessagingManager.DND_PRIORITY_ONLY)

        coVerify(exactly = 0) { homeAssistantModeManager.setActive(any()) }
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, notificationManager.currentInterruptionFilter)
    }
}
