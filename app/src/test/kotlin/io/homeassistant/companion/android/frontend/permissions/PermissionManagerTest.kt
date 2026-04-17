package io.homeassistant.companion.android.frontend.permissions

import android.os.Build
import android.webkit.PermissionRequest as WebViewPermissionRequest
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.NotificationStatusProvider
import io.homeassistant.companion.android.common.util.PermissionChecker
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.Setting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.util.FailFastExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ConsoleLogExtension::class, FailFastExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
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

    private fun createManager(
        hasFcmPushSupport: Boolean = false,
        sdkInt: Int = 0,
    ): PermissionManager {
        return PermissionManager(
            serverManager = serverManager,
            settingsDao = settingsDao,
            fcmSupport = hasFcmPushSupport,
            notificationStatusProvider = notificationStatusProvider,
            permissionChecker = permissionChecker,
            sdkInt = sdkInt,
        )
    }

    private fun mockPermissionRequest(vararg resources: String): WebViewPermissionRequest {
        return mockk(relaxed = true) {
            every { getResources() } returns resources.toList().toTypedArray()
        }
    }

    // region Notification permission

    @Nested
    inner class CheckNotificationPermission {

        // hasFcm | notifEnabled | storedPref | expectedPending
        @ParameterizedTest(name = "hasFcm={0}, notifEnabled={1}, storedPref={2} -> shouldAsk={3}")
        @CsvSource(
            "true,true,,false", // FCM + granted -> auto-dismiss, no need to prompt
            "true,false,,true", // FCM + not granted + fresh install -> prompt
            "true,false,false,false", // FCM + not granted + user already answered -> skip
            "false,true,,true", // No FCM + granted + fresh install -> prompt to configure websocket
            "false,false,,true", // No FCM + not granted + fresh install -> prompt
            "false,false,false,false", // No FCM + not granted + user already answered -> skip
            "false,true,false,false", // No FCM + granted + user already answered -> skip
        )
        fun `Given inputs then sets pending request only if it should be asked`(
            hasFcm: Boolean,
            notifEnabled: Boolean,
            storedPref: String?,
            expectedPending: Boolean,
        ) = runTest {
            every { notificationStatusProvider.areNotificationsEnabled() } returns notifEnabled
            coEvery { integrationRepository.shouldAskNotificationPermission() } returns storedPref?.toBooleanStrictOrNull()

            val manager = createManager(hasFcmPushSupport = hasFcm, sdkInt = Build.VERSION_CODES.TIRAMISU)
            manager.checkNotificationPermission(serverId)

            if (expectedPending) {
                assertInstanceOf(PermissionRequest.Notification::class.java, manager.pendingPermissionRequest.value)
            } else {
                assertNull(manager.pendingPermissionRequest.value)
            }
        }

        @Test
        fun `Given hasFcmPushSupport and notifications already granted then persists false`() = runTest {
            every { notificationStatusProvider.areNotificationsEnabled() } returns true
            coEvery { integrationRepository.shouldAskNotificationPermission() } returns null

            val manager = createManager(hasFcmPushSupport = true, sdkInt = Build.VERSION_CODES.TIRAMISU)
            manager.checkNotificationPermission(serverId)

            coVerify { integrationRepository.setAskNotificationPermission(false) }
        }

        @Test
        fun `Given pre-TIRAMISU device then does nothing`() = runTest {
            val manager = createManager(sdkInt = Build.VERSION_CODES.S)
            manager.checkNotificationPermission(serverId)

            assertNull(manager.pendingPermissionRequest.value)
        }
    }

    @Nested
    inner class OnPermissionResultNotification {

        private fun mockShouldAsk() {
            every { notificationStatusProvider.areNotificationsEnabled() } returns false
            coEvery { integrationRepository.shouldAskNotificationPermission() } returns true
        }

        private suspend fun resolveNotificationPermission(manager: PermissionManager, granted: Boolean) {
            val pending = manager.pendingPermissionRequest.value as PermissionRequest.Notification
            manager.clearPendingPermissionRequest()
            pending.onResult(PermissionRequest.Result.Single(granted = granted))
        }

        @Test
        fun `Given permission granted when doesn't have FcmPushSupport then inserts websocket settings`() = runTest {
            mockShouldAsk()
            val manager = createManager(hasFcmPushSupport = false, sdkInt = Build.VERSION_CODES.TIRAMISU)
            manager.checkNotificationPermission(serverId)

            resolveNotificationPermission(manager, granted = true)

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
            mockShouldAsk()
            val manager = createManager(hasFcmPushSupport = hasFcm, sdkInt = Build.VERSION_CODES.TIRAMISU)
            manager.checkNotificationPermission(serverId)

            resolveNotificationPermission(manager, granted = granted)

            coVerify(exactly = 0) { settingsDao.insert(any()) }
        }

        @ParameterizedTest(name = "granted={0} -> persists do not ask again")
        @ValueSource(booleans = [true, false])
        fun `Given any result then persists do not ask again`(granted: Boolean) = runTest {
            mockShouldAsk()
            val manager = createManager(hasFcmPushSupport = false, sdkInt = Build.VERSION_CODES.TIRAMISU)
            manager.checkNotificationPermission(serverId)

            resolveNotificationPermission(manager, granted = granted)

            coVerify { integrationRepository.setAskNotificationPermission(false) }
        }
    }

    // endregion

    // region WebView permissions (camera/microphone)

    @Nested
    inner class OnWebViewPermissionRequest {

        @Test
        fun `Given camera already granted when video capture requested then auto-grants without pending request`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns true
            val request = mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            verify { request.grant(arrayOf(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given camera not granted when video capture requested then creates pending request`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns false
            val request = mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            verify(exactly = 0) { request.grant(any()) }
            val pending = manager.pendingPermissionRequest.value
            assertInstanceOf(PermissionRequest.WebView::class.java, pending)
            assertEquals(listOf(android.Manifest.permission.CAMERA), pending?.permissions)
        }

        @Test
        fun `Given mic not granted when audio capture requested then creates pending request for RECORD_AUDIO`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns false
            val request = mockPermissionRequest(WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE)

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            val pending = manager.pendingPermissionRequest.value
            assertInstanceOf(PermissionRequest.WebView::class.java, pending)
            assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), pending?.permissions)
        }

        @Test
        fun `Given camera granted but mic not when both requested then defers grant and creates pending for mic`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns true
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns false
            val request = mockPermissionRequest(
                WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE,
                WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE,
            )

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            verify(exactly = 0) { request.grant(any()) }
            val pending = manager.pendingPermissionRequest.value
            assertInstanceOf(PermissionRequest.WebView::class.java, pending)
            val webView = pending as PermissionRequest.WebView
            assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), webView.permissions)
            assertEquals(listOf(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE), webView.alreadyGrantedResources)
        }

        @Test
        fun `Given both already granted when both requested then auto-grants both without pending request`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns true
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns true
            val request = mockPermissionRequest(
                WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE,
                WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE,
            )

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            verify {
                request.grant(
                    arrayOf(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE, WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE),
                )
            }
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given null request then does nothing`() = runTest {
            val manager = createManager()
            manager.onWebViewPermissionRequest(null)

            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given unknown resource then ignores it`() = runTest {
            val request = mockPermissionRequest("android.webkit.resource.UNKNOWN")

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)

            verify(exactly = 0) { request.grant(any()) }
            assertNull(manager.pendingPermissionRequest.value)
        }
    }

    @Nested
    inner class OnPermissionResultWebView {

        @Test
        fun `Given pending request when camera granted then grants WebView resource`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns false
            val request = mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)
            val pending = manager.pendingPermissionRequest.value as PermissionRequest.WebView
            manager.clearPendingPermissionRequest()
            pending.onResult(PermissionRequest.Result.Multiple(permissions = mapOf(android.Manifest.permission.CAMERA to true)))

            verify { request.grant(arrayOf(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given pending request when permission denied then denies WebView request`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns false
            val request = mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)
            val pending = manager.pendingPermissionRequest.value as PermissionRequest.WebView
            manager.clearPendingPermissionRequest()
            pending.onResult(PermissionRequest.Result.Multiple(permissions = mapOf(android.Manifest.permission.CAMERA to false)))

            verify { request.deny() }
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given camera already granted and mic pending when mic granted then grants both in single call`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns true
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns false
            val request = mockPermissionRequest(
                WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE,
                WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE,
            )

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)
            val pending = manager.pendingPermissionRequest.value as PermissionRequest.WebView
            manager.clearPendingPermissionRequest()
            pending.onResult(
                PermissionRequest.Result.Multiple(permissions = mapOf(android.Manifest.permission.RECORD_AUDIO to true)),
            )

            verify {
                request.grant(
                    arrayOf(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE, WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE),
                )
            }
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given camera already granted and mic pending when mic denied then grants only camera`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns true
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns false
            val request = mockPermissionRequest(
                WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE,
                WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE,
            )

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)
            val pending = manager.pendingPermissionRequest.value as PermissionRequest.WebView
            manager.clearPendingPermissionRequest()
            pending.onResult(
                PermissionRequest.Result.Multiple(permissions = mapOf(android.Manifest.permission.RECORD_AUDIO to false)),
            )

            verify { request.grant(arrayOf(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given pending with both permissions when only mic granted then grants only audio resource`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns false
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns false
            val request = mockPermissionRequest(
                WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE,
                WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE,
            )

            val manager = createManager()
            manager.onWebViewPermissionRequest(request)
            val pending = manager.pendingPermissionRequest.value as PermissionRequest.WebView
            manager.clearPendingPermissionRequest()
            pending.onResult(
                PermissionRequest.Result.Multiple(
                    permissions = mapOf(
                        android.Manifest.permission.CAMERA to false,
                        android.Manifest.permission.RECORD_AUDIO to true,
                    ),
                ),
            )

            verify { request.grant(arrayOf(WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE)) }
            assertNull(manager.pendingPermissionRequest.value)
        }
    }

    // endregion

    // region Storage permission for downloads (pre-Q)

    @Nested
    inner class CheckStoragePermissionForDownload {

        @Test
        fun `Given Q+ device then returns false without checking permission`() = runTest {
            val manager = createManager(sdkInt = Build.VERSION_CODES.Q)
            val result = manager.checkStoragePermissionForDownload {}

            assertFalse(result)
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given pre-Q device when storage permission already granted then returns false`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } returns true

            val manager = createManager(sdkInt = Build.VERSION_CODES.P)
            val result = manager.checkStoragePermissionForDownload {}

            assertFalse(result)
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given pre-Q device when storage permission not granted then returns true and emits pending request`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } returns false

            val manager = createManager(sdkInt = Build.VERSION_CODES.P)
            val result = manager.checkStoragePermissionForDownload {}

            assertTrue(result)
            val pending = manager.pendingPermissionRequest.value
            assertInstanceOf(PermissionRequest.ExternalStorage::class.java, pending)
            assertEquals(
                listOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                pending?.permissions,
            )
        }
    }

    @Nested
    inner class OnPermissionResultStorage {

        @Test
        fun `Given pending download when permission granted then calls onGranted`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } returns false

            var onGrantedCalled = false
            val manager = createManager(sdkInt = Build.VERSION_CODES.P)
            manager.checkStoragePermissionForDownload { onGrantedCalled = true }

            val pending = manager.pendingPermissionRequest.value as PermissionRequest.ExternalStorage
            manager.clearPendingPermissionRequest()
            pending.onResult(
                PermissionRequest.Result.Single(granted = true),
            )

            assertTrue(onGrantedCalled)
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given pending download when permission denied then does not call onGranted`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } returns false

            var onGrantedCalled = false
            val manager = createManager(sdkInt = Build.VERSION_CODES.P)
            manager.checkStoragePermissionForDownload { onGrantedCalled = true }

            val pending = manager.pendingPermissionRequest.value as PermissionRequest.ExternalStorage
            manager.clearPendingPermissionRequest()
            pending.onResult(
                PermissionRequest.Result.Single(granted = false),
            )

            assertFalse(onGrantedCalled)
            assertNull(manager.pendingPermissionRequest.value)
        }
    }

    // endregion

    // region Dismiss

    @Nested
    inner class DismissPendingPermission {

        @Test
        fun `Given no pending request when clearPendingPermissionRequest called then triggers FailFast`() = runTest {
            var failFastTriggered = false
            FailFast.setHandler { _, _ -> failFastTriggered = true }

            val manager = createManager()
            assertNull(manager.pendingPermissionRequest.value)

            manager.clearPendingPermissionRequest()

            assertTrue(failFastTriggered, "FailFast should trigger when clearing without a pending request")
        }

        @Test
        fun `Given pending request when dismissed then clears without calling callbacks`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            } returns false
            every { notificationStatusProvider.areNotificationsEnabled() } returns false
            coEvery { integrationRepository.shouldAskNotificationPermission() } returns true

            val manager = createManager(sdkInt = Build.VERSION_CODES.TIRAMISU)
            manager.checkNotificationPermission(1)

            assertNotNull(manager.pendingPermissionRequest.value)

            manager.clearPendingPermissionRequest()

            assertNull(manager.pendingPermissionRequest.value)
        }
    }

    // endregion

    // region Guard against concurrent requests

    @Nested
    inner class ConcurrentRequestQueuing {

        @Test
        fun `Given pending storage request when WebView permission requested then waits and sets after resolution`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } returns false
            every {
                permissionChecker.hasPermission(android.Manifest.permission.CAMERA)
            } returns false

            val manager = createManager(sdkInt = Build.VERSION_CODES.P)
            manager.checkStoragePermissionForDownload {}

            assertNotNull(manager.pendingPermissionRequest.value)

            val webViewRequest = mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)
            val job = launch { manager.onWebViewPermissionRequest(webViewRequest) }
            advanceUntilIdle()

            // Still waiting — storage request is pending
            assertInstanceOf(PermissionRequest.ExternalStorage::class.java, manager.pendingPermissionRequest.value)

            // Resolve storage
            val pending = manager.pendingPermissionRequest.value as PermissionRequest.ExternalStorage
            manager.clearPendingPermissionRequest()
            pending.onResult(PermissionRequest.Result.Single(granted = true))
            advanceUntilIdle()
            job.join()

            // Now WebView request is pending
            assertInstanceOf(PermissionRequest.WebView::class.java, manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given pending WebView request when storage permission required then waits and sets after resolution`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.CAMERA)
            } returns false
            every {
                permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } returns false

            val manager = createManager(sdkInt = Build.VERSION_CODES.P)
            launch { manager.onWebViewPermissionRequest(mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
            advanceUntilIdle()

            val job = launch {
                val result = manager.checkStoragePermissionForDownload {}
                assertTrue(result)
            }
            advanceUntilIdle()

            // Still waiting — WebView request is pending
            assertInstanceOf(PermissionRequest.WebView::class.java, manager.pendingPermissionRequest.value)

            // Resolve WebView
            val pending = manager.pendingPermissionRequest.value as PermissionRequest.WebView
            manager.clearPendingPermissionRequest()
            pending.onResult(PermissionRequest.Result.Multiple(permissions = mapOf(android.Manifest.permission.CAMERA to true)))
            advanceUntilIdle()
            job.join()

            // Now storage request is pending
            assertInstanceOf(PermissionRequest.ExternalStorage::class.java, manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given two concurrent requests waiting when each is cleared then both are served sequentially`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns false

            val manager = createManager(sdkInt = Build.VERSION_CODES.P)

            turbineScope {
                val turbine = manager.pendingPermissionRequest.testIn(backgroundScope)
                assertNull(turbine.awaitItem())

                manager.checkStoragePermissionForDownload {}
                assertInstanceOf(PermissionRequest.ExternalStorage::class.java, turbine.awaitItem())

                launch(Dispatchers.Default) {
                    manager.onWebViewPermissionRequest(mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE))
                }
                launch(Dispatchers.Default) {
                    manager.onWebViewPermissionRequest(mockPermissionRequest(WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE))
                }

                // Clear storage — first waiter fills the slot, second stays suspended
                manager.clearPendingPermissionRequest()
                assertNull(turbine.awaitItem())
                val firstPending = turbine.awaitItem()
                assertInstanceOf(PermissionRequest.WebView::class.java, firstPending)
                turbine.expectNoEvents()

                // Clear first waiter — second waiter now fills the slot
                manager.clearPendingPermissionRequest()
                assertNull(turbine.awaitItem())
                val secondPending = turbine.awaitItem()
                assertInstanceOf(PermissionRequest.WebView::class.java, secondPending)

                // Both were served, and they are different permissions
                assertNotEquals(firstPending?.permissions, secondPending?.permissions)

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given pending WebView request when notification permission checked then waits and sets after resolution`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.CAMERA)
            } returns false
            every { notificationStatusProvider.areNotificationsEnabled() } returns false
            coEvery { integrationRepository.shouldAskNotificationPermission() } returns true

            val manager = createManager(sdkInt = Build.VERSION_CODES.TIRAMISU)
            launch { manager.onWebViewPermissionRequest(mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
            advanceUntilIdle()

            // checkNotificationPermission suspends until pending is null
            val job = launch { manager.checkNotificationPermission(serverId) }
            advanceUntilIdle()

            // Still waiting — WebView request is pending
            assertInstanceOf(PermissionRequest.WebView::class.java, manager.pendingPermissionRequest.value)

            // Resolve the WebView request
            val pending = manager.pendingPermissionRequest.value as PermissionRequest.WebView
            manager.clearPendingPermissionRequest()
            pending.onResult(PermissionRequest.Result.Multiple(permissions = mapOf(android.Manifest.permission.CAMERA to true)))
            advanceUntilIdle()

            // Now the notification request should be set
            job.join()
            assertInstanceOf(PermissionRequest.Notification::class.java, manager.pendingPermissionRequest.value)
        }
    }

    // endregion
}
