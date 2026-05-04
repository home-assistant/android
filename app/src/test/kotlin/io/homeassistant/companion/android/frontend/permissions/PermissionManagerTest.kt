package io.homeassistant.companion.android.frontend.permissions

import android.os.Build
import android.webkit.PermissionRequest as WebViewPermissionRequest
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
            val job = launch { manager.checkNotificationPermission(serverId) }
            advanceUntilIdle()

            if (expectedPending) {
                assertInstanceOf(PermissionRequest.Notification::class.java, manager.pendingPermissionRequest.value)
                // Dismiss to let the suspend return so the test scope can finish.
                (manager.pendingPermissionRequest.value as PermissionRequest.Notification).onDismiss()
            } else {
                assertNull(manager.pendingPermissionRequest.value)
            }
            advanceUntilIdle()
            job.join()
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
    inner class NotificationPermissionResult {

        private fun mockShouldAsk() {
            every { notificationStatusProvider.areNotificationsEnabled() } returns false
            coEvery { integrationRepository.shouldAskNotificationPermission() } returns true
        }

        @Test
        fun `Given permission granted when doesn't have FcmPushSupport then inserts websocket settings`() = runTest {
            mockShouldAsk()
            val manager = createManager(hasFcmPushSupport = false, sdkInt = Build.VERSION_CODES.TIRAMISU)

            val job = launch { manager.checkNotificationPermission(serverId) }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.Notification).onResult(true)
            advanceUntilIdle()
            job.join()

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

            val job = launch { manager.checkNotificationPermission(serverId) }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.Notification).onResult(granted)
            advanceUntilIdle()
            job.join()

            coVerify(exactly = 0) { settingsDao.insert(any()) }
        }

        @ParameterizedTest(name = "granted={0} -> persists do not ask again")
        @ValueSource(booleans = [true, false])
        fun `Given any explicit answer then persists do not ask again`(granted: Boolean) = runTest {
            mockShouldAsk()
            val manager = createManager(hasFcmPushSupport = false, sdkInt = Build.VERSION_CODES.TIRAMISU)

            val job = launch { manager.checkNotificationPermission(serverId) }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.Notification).onResult(granted)
            advanceUntilIdle()
            job.join()

            coVerify { integrationRepository.setAskNotificationPermission(false) }
        }

        @Test
        fun `Given user dismisses without answering then does not persist preference`() = runTest {
            mockShouldAsk()
            val manager = createManager(hasFcmPushSupport = false, sdkInt = Build.VERSION_CODES.TIRAMISU)

            val job = launch { manager.checkNotificationPermission(serverId) }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.Notification).onDismiss()
            advanceUntilIdle()
            job.join()

            coVerify(exactly = 0) { integrationRepository.setAskNotificationPermission(any()) }
            coVerify(exactly = 0) { settingsDao.insert(any()) }
            assertNull(manager.pendingPermissionRequest.value)
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
            val job = launch { manager.onWebViewPermissionRequest(request) }
            advanceUntilIdle()

            verify(exactly = 0) { request.grant(any()) }
            val pending = manager.pendingPermissionRequest.value
            assertInstanceOf(PermissionRequest.WebView::class.java, pending)
            assertEquals(listOf(android.Manifest.permission.CAMERA), pending?.permissions)

            (pending as PermissionRequest.WebView).onResult(emptyMap())
            advanceUntilIdle()
            job.join()
        }

        @Test
        fun `Given mic not granted when audio capture requested then creates pending request for RECORD_AUDIO`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns false
            val request = mockPermissionRequest(WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE)

            val manager = createManager()
            val job = launch { manager.onWebViewPermissionRequest(request) }
            advanceUntilIdle()

            val pending = manager.pendingPermissionRequest.value
            assertInstanceOf(PermissionRequest.WebView::class.java, pending)
            assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), pending?.permissions)

            (pending as PermissionRequest.WebView).onResult(emptyMap())
            advanceUntilIdle()
            job.join()
        }

        @Test
        fun `Given camera granted but mic not when both requested then creates pending request for mic only`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns true
            every { permissionChecker.hasPermission(android.Manifest.permission.RECORD_AUDIO) } returns false
            val request = mockPermissionRequest(
                WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE,
                WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE,
            )

            val manager = createManager()
            val job = launch { manager.onWebViewPermissionRequest(request) }
            advanceUntilIdle()

            verify(exactly = 0) { request.grant(any()) }
            val pending = manager.pendingPermissionRequest.value
            assertInstanceOf(PermissionRequest.WebView::class.java, pending)
            assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), pending?.permissions)

            (pending as PermissionRequest.WebView).onResult(emptyMap())
            advanceUntilIdle()
            job.join()
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
    inner class WebViewPermissionResult {

        @Test
        fun `Given pending request when camera granted then grants WebView resource`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns false
            val request = mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)

            val manager = createManager()
            val job = launch { manager.onWebViewPermissionRequest(request) }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.WebView)
                .onResult(mapOf(android.Manifest.permission.CAMERA to true))
            advanceUntilIdle()
            job.join()

            verify { request.grant(arrayOf(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)) }
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given pending request when permission denied then denies WebView request`() = runTest {
            every { permissionChecker.hasPermission(android.Manifest.permission.CAMERA) } returns false
            val request = mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)

            val manager = createManager()
            val job = launch { manager.onWebViewPermissionRequest(request) }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.WebView)
                .onResult(mapOf(android.Manifest.permission.CAMERA to false))
            advanceUntilIdle()
            job.join()

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
            val job = launch { manager.onWebViewPermissionRequest(request) }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.WebView)
                .onResult(mapOf(android.Manifest.permission.RECORD_AUDIO to true))
            advanceUntilIdle()
            job.join()

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
            val job = launch { manager.onWebViewPermissionRequest(request) }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.WebView)
                .onResult(mapOf(android.Manifest.permission.RECORD_AUDIO to false))
            advanceUntilIdle()
            job.join()

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
            val job = launch { manager.onWebViewPermissionRequest(request) }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.WebView).onResult(
                mapOf(
                    android.Manifest.permission.CAMERA to false,
                    android.Manifest.permission.RECORD_AUDIO to true,
                ),
            )
            advanceUntilIdle()
            job.join()

            verify { request.grant(arrayOf(WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE)) }
            assertNull(manager.pendingPermissionRequest.value)
        }
    }

    // endregion

    // region Storage permission for downloads (pre-Q)

    @Nested
    inner class CheckStoragePermissionForDownload {

        @Test
        fun `Given Q+ device then returns true without checking permission`() = runTest {
            val manager = createManager(sdkInt = Build.VERSION_CODES.Q)
            assertTrue(manager.checkStoragePermissionForDownload())
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given pre-Q device when storage permission already granted then returns true`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } returns true

            val manager = createManager(sdkInt = Build.VERSION_CODES.P)
            assertTrue(manager.checkStoragePermissionForDownload())
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given pre-Q device when storage permission not granted then emits pending request`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } returns false

            val manager = createManager(sdkInt = Build.VERSION_CODES.P)
            val result = async { manager.checkStoragePermissionForDownload() }
            advanceUntilIdle()

            val pending = manager.pendingPermissionRequest.value
            assertInstanceOf(PermissionRequest.ExternalStorage::class.java, pending)
            assertEquals(
                listOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                pending?.permissions,
            )

            (pending as PermissionRequest.ExternalStorage).onResult(false)
            advanceUntilIdle()
            assertFalse(result.await())
        }
    }

    @Nested
    inner class StoragePermissionResult {

        @Test
        fun `Given pending download when permission granted then returns true`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } returns false

            val manager = createManager(sdkInt = Build.VERSION_CODES.P)
            val result = async { manager.checkStoragePermissionForDownload() }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.ExternalStorage).onResult(true)
            advanceUntilIdle()

            assertTrue(result.await())
            assertNull(manager.pendingPermissionRequest.value)
        }

        @Test
        fun `Given pending download when permission denied then returns false`() = runTest {
            every {
                permissionChecker.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } returns false

            val manager = createManager(sdkInt = Build.VERSION_CODES.P)
            val result = async { manager.checkStoragePermissionForDownload() }
            advanceUntilIdle()
            (manager.pendingPermissionRequest.value as PermissionRequest.ExternalStorage).onResult(false)
            advanceUntilIdle()

            assertFalse(result.await())
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
            val storageJob = launch { manager.checkStoragePermissionForDownload() }
            advanceUntilIdle()

            assertInstanceOf(PermissionRequest.ExternalStorage::class.java, manager.pendingPermissionRequest.value)

            val webViewRequest = mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE)
            val webViewJob = launch { manager.onWebViewPermissionRequest(webViewRequest) }
            advanceUntilIdle()

            // Still waiting — storage request is pending
            assertInstanceOf(PermissionRequest.ExternalStorage::class.java, manager.pendingPermissionRequest.value)

            // Resolve storage
            (manager.pendingPermissionRequest.value as PermissionRequest.ExternalStorage).onResult(true)
            advanceUntilIdle()
            storageJob.join()

            // Now WebView request is pending
            assertInstanceOf(PermissionRequest.WebView::class.java, manager.pendingPermissionRequest.value)

            (manager.pendingPermissionRequest.value as PermissionRequest.WebView).onResult(emptyMap())
            advanceUntilIdle()
            webViewJob.join()
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
            val webViewJob = launch {
                manager.onWebViewPermissionRequest(mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE))
            }
            advanceUntilIdle()

            val storageJob = launch {
                val result = manager.checkStoragePermissionForDownload()
                assertFalse(result, "Storage permission was denied below")
            }
            advanceUntilIdle()

            // Still waiting — WebView request is pending
            assertInstanceOf(PermissionRequest.WebView::class.java, manager.pendingPermissionRequest.value)

            // Resolve WebView
            (manager.pendingPermissionRequest.value as PermissionRequest.WebView)
                .onResult(mapOf(android.Manifest.permission.CAMERA to true))
            advanceUntilIdle()
            webViewJob.join()

            // Now storage request is pending
            assertInstanceOf(PermissionRequest.ExternalStorage::class.java, manager.pendingPermissionRequest.value)

            (manager.pendingPermissionRequest.value as PermissionRequest.ExternalStorage).onResult(false)
            advanceUntilIdle()
            storageJob.join()
        }

        @Test
        fun `Given two concurrent requests waiting when each is resolved then both are served sequentially`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns false

            val manager = createManager(sdkInt = Build.VERSION_CODES.P)

            turbineScope {
                val turbine = manager.pendingPermissionRequest.testIn(backgroundScope)
                assertNull(turbine.awaitItem())

                val storageJob = launch { manager.checkStoragePermissionForDownload() }
                assertInstanceOf(PermissionRequest.ExternalStorage::class.java, turbine.awaitItem())

                val firstWebViewJob = launch {
                    manager.onWebViewPermissionRequest(mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE))
                }
                val secondWebViewJob = launch {
                    manager.onWebViewPermissionRequest(mockPermissionRequest(WebViewPermissionRequest.RESOURCE_AUDIO_CAPTURE))
                }
                // Ensure both waiters are parked in queue.awaitResult before we resolve storage
                runCurrent()

                // Resolve storage — slot becomes null briefly, then first waiter takes it
                (manager.pendingPermissionRequest.value as PermissionRequest.ExternalStorage).onResult(false)
                storageJob.join()
                assertNull(turbine.awaitItem())
                val firstPending = turbine.awaitItem()
                assertInstanceOf(PermissionRequest.WebView::class.java, firstPending)
                turbine.expectNoEvents()

                // Resolve first waiter — slot becomes null briefly, second waiter takes it
                (firstPending as PermissionRequest.WebView).onResult(emptyMap())
                assertNull(turbine.awaitItem())
                val secondPending = turbine.awaitItem()
                assertInstanceOf(PermissionRequest.WebView::class.java, secondPending)

                // FIFO order: first waiter gets CAMERA (video), second gets RECORD_AUDIO (audio)
                assertEquals(listOf(android.Manifest.permission.CAMERA), firstPending.permissions)
                assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), secondPending?.permissions)

                (secondPending as PermissionRequest.WebView).onResult(emptyMap())
                firstWebViewJob.join()
                secondWebViewJob.join()
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
            val webViewJob = launch {
                manager.onWebViewPermissionRequest(mockPermissionRequest(WebViewPermissionRequest.RESOURCE_VIDEO_CAPTURE))
            }
            advanceUntilIdle()

            // checkNotificationPermission suspends until pending is null
            val notificationJob = launch { manager.checkNotificationPermission(serverId) }
            advanceUntilIdle()

            // Still waiting — WebView request is pending
            assertInstanceOf(PermissionRequest.WebView::class.java, manager.pendingPermissionRequest.value)

            // Resolve the WebView request
            (manager.pendingPermissionRequest.value as PermissionRequest.WebView)
                .onResult(mapOf(android.Manifest.permission.CAMERA to true))
            advanceUntilIdle()
            webViewJob.join()

            // Now the notification request should be set
            assertInstanceOf(PermissionRequest.Notification::class.java, manager.pendingPermissionRequest.value)

            (manager.pendingPermissionRequest.value as PermissionRequest.Notification).onDismiss()
            advanceUntilIdle()
            notificationJob.join()
        }
    }

    // endregion
}
