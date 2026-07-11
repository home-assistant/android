package io.homeassistant.companion.android.notifications

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.util.ReloadRequestMediator
import io.homeassistant.companion.android.util.WebViewNavigationMediator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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

    private lateinit var navigationMediator: WebViewNavigationMediator
    private lateinit var reloadMediator: ReloadRequestMediator
    private val visibleServerId = MutableStateFlow<Int?>(null)
    private lateinit var server: Server
    private lateinit var messagingManager: MessagingManager

    @Before
    fun setUp() {
        val serverManager = mockk<ServerManager>(relaxed = true)
        val integrationRepository = mockk<IntegrationRepository>(relaxed = true)
        server = mockk<Server>(relaxed = true) {
            every { id } returns SERVER_ID
            every { version } returns HomeAssistantVersion(2025, 6, 0)
        }
        coEvery { serverManager.getServer(WEBHOOK_ID) } returns server
        coEvery { serverManager.getServer(SERVER_ID) } returns server
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.isTrusted() } returns true

        navigationMediator = mockk(relaxed = true)
        every { navigationMediator.visibleServerId } returns visibleServerId
        reloadMediator = mockk(relaxed = true)

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
            permissionRequestMediator = mockk(relaxed = true),
            reloadRequestMediator = reloadMediator,
            webViewNavigationMediator = navigationMediator,
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

    @Test
    fun `Given the frontend shows the server when receiving webview command then it navigates in place`() {
        visibleServerId.value = SERVER_ID

        handleWebViewCommand("/lovelace/cameras")

        verify(exactly = 1) { navigationMediator.requestNavigation(FrontendTarget.Path("/lovelace/cameras")) }
    }

    @Test
    fun `Given an entity target when receiving webview command then it opens more info in place`() {
        visibleServerId.value = SERVER_ID

        handleWebViewCommand("entityId:sun.sun")

        verify(exactly = 1) { navigationMediator.requestNavigation(FrontendTarget.EntityMoreInfo("sun.sun")) }
    }

    @Test
    fun `Given an empty command when receiving webview command then it navigates to the default dashboard in place`() {
        visibleServerId.value = SERVER_ID

        handleWebViewCommand("")

        verify(exactly = 1) { navigationMediator.requestNavigation(FrontendTarget.Default) }
    }

    @Test
    fun `Given a server without the navigate command when receiving webview command then it does not navigate in place`() {
        visibleServerId.value = SERVER_ID
        every { server.version } returns HomeAssistantVersion(2025, 5, 0)
        ShadowSettings.setCanDrawOverlays(true)

        handleWebViewCommand("/lovelace/cameras")

        verify(exactly = 0) { navigationMediator.requestNavigation(any()) }
    }

    private fun assertCommandDoesNotNavigateInPlace(path: String) {
        visibleServerId.value = SERVER_ID
        ShadowSettings.setCanDrawOverlays(true)

        handleWebViewCommand(path)

        verify(exactly = 0) { navigationMediator.requestNavigation(any()) }
    }

    @Test
    fun `Given an app settings path when receiving webview command then it does not navigate in place`() {
        assertCommandDoesNotNavigateInPlace("settings://notification_history")
    }

    @Test
    fun `Given an app launch path with surrounding spaces when receiving webview command then it does not navigate in place`() {
        assertCommandDoesNotNavigateInPlace(" app://com.example.app ")
    }

    @Test
    fun `Given the frontend shows the server when receiving reload command then it reloads in place`() {
        visibleServerId.value = SERVER_ID

        handleWebViewCommand("reload")

        verify(exactly = 1) { reloadMediator.emitReloadRequestEvent() }
        verify(exactly = 0) { navigationMediator.requestNavigation(any()) }
    }

    @Test
    fun `Given the frontend is not visible when receiving reload command then it launches the frontend instead`() {
        visibleServerId.value = null
        ShadowSettings.setCanDrawOverlays(true)

        handleWebViewCommand("reload")

        verify(exactly = 0) { reloadMediator.emitReloadRequestEvent() }
        assertEquals(
            LaunchActivity::class.java.name,
            shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity?.component?.className,
        )
    }

    @Test
    fun `Given the frontend is not visible when receiving webview command then it does not navigate in place`() {
        visibleServerId.value = null
        ShadowSettings.setCanDrawOverlays(true)

        handleWebViewCommand("/lovelace/cameras")

        verify(exactly = 0) { navigationMediator.requestNavigation(any()) }
    }

    @Test
    fun `Given an absolute URL when receiving webview command then it does not navigate in place`() {
        assertCommandDoesNotNavigateInPlace("https://example.com")
    }

    @Test
    fun `Given an uppercase absolute URL with surrounding spaces when receiving webview command then it does not navigate in place`() {
        assertCommandDoesNotNavigateInPlace(" HTTPS://example.com")
    }

    @Test
    fun `Given a path with surrounding spaces when receiving webview command then it navigates to the trimmed path`() {
        visibleServerId.value = SERVER_ID

        handleWebViewCommand(" /lovelace/cameras ")

        verify(exactly = 1) { navigationMediator.requestNavigation(FrontendTarget.Path("/lovelace/cameras")) }
    }
}
