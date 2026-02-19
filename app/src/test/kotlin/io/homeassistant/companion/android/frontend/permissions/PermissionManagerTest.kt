package io.homeassistant.companion.android.frontend.permissions

import android.webkit.PermissionRequest
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ConsoleLogExtension::class)
class PermissionManagerTest {

    private val serverManager: ServerManager = mockk(relaxed = true)
    private val settingsDao: SettingsDao = mockk(relaxed = true)
    private val integrationRepository: IntegrationRepository = mockk(relaxed = true)
    private val notificationStatusProvider: NotificationStatusProvider = mockk()
    private val permissionChecker: PermissionChecker = mockk()

    private val serverId = 1

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
    }

    private fun createManager(hasFcmPushSupport: Boolean = false): PermissionManager {
        return PermissionManager(
            serverManager = serverManager,
            settingsDao = settingsDao,
            hasFcmPushSupport = hasFcmPushSupport,
            notificationStatusProvider = notificationStatusProvider,
            permissionChecker = permissionChecker,
        )
    }

    private fun mockPermissionRequest(vararg resources: String): PermissionRequest {
        return mockk(relaxed = true) {
            every { getResources() } returns resources.toList().toTypedArray()
        }
    }

    // region Notification permission

    @Nested
    inner class ShouldAskNotificationPermission {

        // hasFcm | notifEnabled | storedPref | expected
        @ParameterizedTest(name = "hasFcm={0}, notifEnabled={1}, storedPref={2} -> shouldAsk={3}")
        @CsvSource(
            "true,true,,false", // FCM + granted → auto-dismiss, no need to prompt
            "true,false,,true",  // FCM + not granted + fresh install → prompt
            "true,false,false,false", // FCM + not granted + user already answered → skip
            "false,true,,true",  // No FCM + granted + fresh install → prompt to configure websocket
            "false,false,,true",  // No FCM + not granted + fresh install → prompt
            "false,false,false,false", // No FCM + not granted + user already answered → skip
            "false,true,false,false", // No FCM + granted + user already answered → skip
        )
        fun `Given inputs then returns expected result`(
            hasFcm: Boolean,
            notifEnabled: Boolean,
            storedPref: String?,
            expected: Boolean,
        ) = runTest {
            every { notificationStatusProvider.areNotificationsEnabled() } returns notifEnabled
            coEvery { integrationRepository.shouldAskNotificationPermission() } returns storedPref?.toBooleanStrictOrNull()

            val manager = createManager(hasFcmPushSupport = hasFcm)
            val result = manager.shouldAskNotificationPermission(serverId)

            assertEquals(expected, result)
        }

        @Test
        fun `Given hasFcmPushSupport and notifications already granted then persists false`() = runTest {
            every { notificationStatusProvider.areNotificationsEnabled() } returns true
            coEvery { integrationRepository.shouldAskNotificationPermission() } returns null

            val manager = createManager(hasFcmPushSupport = true)
            manager.shouldAskNotificationPermission(serverId)

            coVerify { integrationRepository.setAskNotificationPermission(false) }
        }
    }

    @Nested
    inner class OnNotificationPermissionResult {

        @Test
        fun `Given permission granted when doesn't have FcmPushSupport then inserts websocket settings`() = runTest {
            val manager = createManager(hasFcmPushSupport = false)
            manager.onNotificationPermissionResult(serverId = serverId, granted = true)

            coVerify {
                settingsDao.insert(
                    Setting(
                        id = serverId,
                        websocketSetting = WebsocketSetting.ALWAYS,
                        sensorUpdateFrequency = SensorUpdateFrequencySetting.NORMAL,
                    ),
                )
            }
        }

        // Websocket settings should NOT be inserted when FCM is available or permission denied
        @ParameterizedTest(name = "hasFcm={0}, granted={1} -> no websocket insert")
        @CsvSource(
            "true,  true",
            "true,  false",
            "false, false",
        )
        fun `Given inputs where websocket should not be configured then does not insert settings`(
            hasFcm: Boolean,
            granted: Boolean,
        ) = runTest {
            val manager = createManager(hasFcmPushSupport = hasFcm)
            manager.onNotificationPermissionResult(serverId = serverId, granted = granted)

            coVerify(exactly = 0) { settingsDao.insert(any()) }
        }

        @ParameterizedTest(name = "granted={0} -> persists do not ask again")
        @ValueSource(booleans = [true, false])
        fun `Given any result then persists do not ask again`(granted: Boolean) = runTest {
            val manager = createManager(hasFcmPushSupport = false)
            manager.onNotificationPermissionResult(serverId = serverId, granted = granted)

            coVerify { integrationRepository.setAskNotificationPermission(false) }
        }
    }

    // endregion

    // region WebView permissions (camera/microphone)

    @Nested
    inner class OnWebViewPermissionRequest {

        @Test
        fun `Given camera already granted when video capture requested then auto-grants without pending request`() {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns true
            val request = mockPermissionRequest(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            verify { request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
            assertNull(manager.pendingWebViewPermission.value)
        }

        @Test
        fun `Given camera not granted when video capture requested then creates pending request`() {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns false
            val request = mockPermissionRequest(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            verify(exactly = 0) { request.grant(any()) }
            val pending = manager.pendingWebViewPermission.value
            assertNotNull(pending)
            assertEquals(listOf(android.Manifest.permission.CAMERA), pending?.androidPermissions)
        }

        @Test
        fun `Given mic not granted when audio capture requested then creates pending request for RECORD_AUDIO`() {
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns false
            val request = mockPermissionRequest(PermissionRequest.RESOURCE_AUDIO_CAPTURE)

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            val pending = manager.pendingWebViewPermission.value
            assertNotNull(pending)
            assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), pending?.androidPermissions)
        }

        @Test
        fun `Given camera granted but mic not when both requested then auto-grants camera and creates pending for mic`() {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns true
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns false
            val request = mockPermissionRequest(
                PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
            )

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            verify { request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
            val pending = manager.pendingWebViewPermission.value
            assertNotNull(pending)
            assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), pending?.androidPermissions)
        }

        @Test
        fun `Given both already granted when both requested then auto-grants both without pending request`() {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns true
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns true
            val request = mockPermissionRequest(
                PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
            )

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            verify {
                request.grant(
                    arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE),
                )
            }
            assertNull(manager.pendingWebViewPermission.value)
        }

        @Test
        fun `Given null request then does nothing`() {
            val manager = createManager()
            manager.onWebViewPermissionRequest(null)

            assertNull(manager.pendingWebViewPermission.value)
        }

        @Test
        fun `Given unknown resource then ignores it`() {
            val request = mockPermissionRequest("android.webkit.resource.UNKNOWN")

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            verify(exactly = 0) { request.grant(any()) }
            assertNull(manager.pendingWebViewPermission.value)
        }
    }

    @Nested
    inner class OnWebViewPermissionResult {

        @Test
        fun `Given pending request when camera granted then grants WebView resource`() {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns false
            val request = mockPermissionRequest(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)
            manager.onWebViewPermissionResult(mapOf(android.Manifest.permission.CAMERA to true))

            verify { request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
            assertNull(manager.pendingWebViewPermission.value)
        }

        @Test
        fun `Given pending request when permission denied then denies WebView request`() {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns false
            val request = mockPermissionRequest(PermissionRequest.RESOURCE_VIDEO_CAPTURE)

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)
            manager.onWebViewPermissionResult(mapOf(android.Manifest.permission.CAMERA to false))

            verify { request.deny() }
            assertNull(manager.pendingWebViewPermission.value)
        }

        @Test
        fun `Given no pending request when result received then does nothing`() {
            val manager = createManager()
            manager.onWebViewPermissionResult(mapOf(android.Manifest.permission.CAMERA to true))

            assertNull(manager.pendingWebViewPermission.value)
        }

        @Test
        fun `Given pending with both permissions when only mic granted then grants only audio resource`() {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns false
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns false
            val request = mockPermissionRequest(
                PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
            )

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)
            manager.onWebViewPermissionResult(
                mapOf(
                    android.Manifest.permission.CAMERA to false,
                    android.Manifest.permission.RECORD_AUDIO to true,
                ),
            )

            verify { request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) }
            assertNull(manager.pendingWebViewPermission.value)
        }
    }

    // endregion
}
