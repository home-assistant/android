package io.homeassistant.companion.android.notifications

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.LaunchActivity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

private const val SERVER_ID = 1
private const val WEBHOOK_ID = "webhook"

/**
 * Covers the routing of the webview command only, [MessagingManager] has no further coverage yet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MessagingManagerWebViewCommandTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var messagingManager: MessagingManager

    @Before
    fun setUp() {
        ShadowSettings.setCanDrawOverlays(true)
        val serverManager = mockk<ServerManager>(relaxed = true)
        val integrationRepository = mockk<IntegrationRepository>(relaxed = true)
        val server = mockk<Server>(relaxed = true) {
            every { id } returns SERVER_ID
        }
        coEvery { serverManager.getServer(WEBHOOK_ID) } returns server
        coEvery { serverManager.getServer(SERVER_ID) } returns server
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.isTrusted() } returns true

        messagingManager = MessagingManager(
            context = application,
            okHttpClientProvider = mockk(relaxed = true),
            serverManager = serverManager,
            prefsRepository = mockk(relaxed = true),
            notificationDao = mockk(relaxed = true),
            sensorRepository = mockk(relaxed = true),
            settingsDao = mockk(relaxed = true),
            textToSpeechClient = mockk(relaxed = true),
            flashlightHelper = mockk(relaxed = true),
            permissionRequestMediator = mockk(relaxed = true),
            assistConfigManager = mockk(relaxed = true),
            defaultAssistantManager = mockk(relaxed = true),
            bluetoothSensorManager = mockk(relaxed = true),
        )
    }

    private fun handleWebViewCommand(path: String) {
        messagingManager.handleMessage(
            mapOf(
                NotificationData.MESSAGE to MessagingManager.COMMAND_WEBVIEW,
                NotificationData.COMMAND to path,
                NotificationData.WEBHOOK_ID to WEBHOOK_ID,
            ),
            "FCM",
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The deep link of the started [LaunchActivity] intent, asserting the intent shape. */
    private fun startedDeepLink(): LaunchActivity.DeepLink {
        val intent = checkNotNull(shadowOf(application).nextStartedActivity) { "No activity was started" }
        assertEquals(LaunchActivity::class.java.name, intent.component?.className)
        // Delivered to a running activity instead of recreating it
        val expectedFlags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(expectedFlags, intent.flags and expectedFlags)
        val deepLink = intent.extras?.keySet()?.firstNotNullOfOrNull {
            @Suppress("DEPRECATION")
            intent.extras?.get(it) as? LaunchActivity.DeepLink
        }
        return checkNotNull(deepLink) { "The started intent carries no deep link" }
    }

    @Test
    fun `Given a path when receiving webview command then the frontend is launched at the target`() {
        handleWebViewCommand("/lovelace/cameras")

        assertEquals(
            LaunchActivity.DeepLink.NavigateTo(FrontendTarget.Path("/lovelace/cameras"), SERVER_ID),
            startedDeepLink(),
        )
    }

    @Test
    fun `Given an entity target when receiving webview command then the frontend is launched at its more info`() {
        handleWebViewCommand("entityId:sun.sun")

        assertEquals(
            LaunchActivity.DeepLink.NavigateTo(FrontendTarget.EntityMoreInfo("sun.sun"), SERVER_ID),
            startedDeepLink(),
        )
    }

    @Test
    fun `Given an empty command when receiving webview command then the frontend is launched at the default dashboard`() {
        handleWebViewCommand("")

        assertEquals(
            LaunchActivity.DeepLink.NavigateTo(FrontendTarget.Default, SERVER_ID),
            startedDeepLink(),
        )
    }

    @Test
    fun `Given a path with surrounding spaces when receiving webview command then the target is trimmed`() {
        handleWebViewCommand(" /lovelace/cameras ")

        assertEquals(
            LaunchActivity.DeepLink.NavigateTo(FrontendTarget.Path("/lovelace/cameras"), SERVER_ID),
            startedDeepLink(),
        )
    }

    @Test
    fun `Given the reload command when receiving webview command then the frontend is reloaded`() {
        handleWebViewCommand("reload")

        assertEquals(LaunchActivity.DeepLink.ReloadFrontend(SERVER_ID), startedDeepLink())
    }

    @Test
    fun `Given the reload command in mixed case with spaces when receiving webview command then the frontend is reloaded`() {
        handleWebViewCommand(" Reload ")

        assertEquals(LaunchActivity.DeepLink.ReloadFrontend(SERVER_ID), startedDeepLink())
    }

    @Test
    fun `Given no overlay permission when receiving webview command then no activity is started`() {
        ShadowSettings.setCanDrawOverlays(false)

        handleWebViewCommand("/lovelace/cameras")

        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(shadowOf(application).broadcastIntents.isEmpty())
    }
}
