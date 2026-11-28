package io.homeassistant.companion.android.widgets.camera

import android.graphics.Bitmap
import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.net.URL
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SERVER_A = 1
private const val SERVER_B = 2
private const val WIDGET_ID = 17
private const val IMAGE_A = "image.front_snapshot"
private const val IMAGE_B = "image.back_snapshot"
private const val IMAGE_STATE = "2026-11-26T10:00:00+00:00"

@OptIn(ExperimentalCoroutinesApi::class)
class CameraWidgetTest {
    private val rows = MutableStateFlow<List<CameraWidgetEntity>>(emptyList())
    private val dao = mockk<CameraWidgetDao> {
        every { getAllFlow() } returns rows
    }
    private val serverManager = mockk<ServerManager>()
    private val integrationA = mockk<IntegrationRepository>()
    private val integrationB = mockk<IntegrationRepository>()
    private val imageLoader = mockk<CameraWidgetImageLoader>()
    private val updater = CameraWidgetStateUpdater(dao, serverManager, imageLoader)
    private val bitmap = mockk<Bitmap>()
    private var stateA = IMAGE_STATE
    private var failImage = false
    private val requestedImages = mutableListOf<String>()

    private fun entity(entityId: String, state: String) = Entity(
        entityId = entityId,
        state = state,
        attributes = mapOf("entity_picture" to "/api/camera_proxy/$entityId?auth=synthetic"),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )

    private fun configureServer(serverId: Int, url: String, repository: IntegrationRepository, entityId: String) {
        val provider = mockk<ServerConnectionStateProvider>()
        coEvery { serverManager.getServer(serverId) } returns mockk()
        coEvery { serverManager.integrationRepository(serverId) } returns repository
        coEvery { serverManager.connectionStateProvider(serverId) } returns provider
        every { provider.urlFlow() } returns flowOf(UrlState.HasUrl(URL(url)))
        coEvery { repository.getEntity(entityId) } coAnswers {
            entity(entityId, if (serverId == SERVER_A) stateA else IMAGE_STATE)
        }
    }

    private fun configureWidget(entityId: String = IMAGE_A, serverId: Int = SERVER_A) {
        rows.value = listOf(CameraWidgetEntity(WIDGET_ID, serverId, entityId, WidgetTapAction.REFRESH))
    }

    private fun configureImageLoader() {
        coEvery { imageLoader.fetch(any(), any()) } coAnswers {
            requestedImages += firstArg<String>()
            if (failImage) null else bitmap
        }
    }

    @Test
    fun `initial composition loads image through its configured server`() = runTest {
        configureServer(SERVER_A, "https://server-a.example/", integrationA, IMAGE_A)
        configureImageLoader()
        configureWidget()
        updater.refresh(WIDGET_ID) // No active composition: the first composition must cover the request once.

        updater.stateFlow(WIDGET_ID).test {
            assertEquals(CameraWidgetState.Loading, awaitItem())
            assertEquals(CameraWidgetState.Loading, awaitItem())
            val content = awaitItem() as CameraWidgetState.Content
            assertSame(bitmap, content.bitmap)
            assertFalse(content.error)
            assertEquals(listOf("https://server-a.example/api/camera_proxy/$IMAGE_A?auth=synthetic"), requestedImages)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `same state and attribute-only event does not fetch a displayed image again`() = runTest {
        configureServer(SERVER_A, "https://server-a.example", integrationA, IMAGE_A)
        configureImageLoader()
        configureWidget()

        updater.stateFlow(WIDGET_ID).test {
            awaitItem()
            awaitItem()
            assertSame(bitmap, (awaitItem() as CameraWidgetState.Content).bitmap)
            updater.refreshFromEntity(WIDGET_ID, IMAGE_STATE)
            runCurrent()
            expectNoEvents()
            assertEquals(1, requestedImages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `composition publish failure retries cached rendering on next unchanged event without refetching`() = runTest {
        configureServer(SERVER_A, "https://server-a.example", integrationA, IMAGE_A)
        configureImageLoader()
        configureWidget()

        updater.stateFlow(WIDGET_ID).test {
            awaitItem()
            awaitItem()
            val displayed = awaitItem() as CameraWidgetState.Content
            updater.compositionError(WIDGET_ID)
            runCurrent()
            expectNoEvents()

            updater.refreshFromEntity(WIDGET_ID, IMAGE_STATE)
            val republished = awaitItem() as CameraWidgetState.Content
            assertSame(displayed.bitmap, republished.bitmap)
            assertTrue(republished.revision > displayed.revision)
            assertEquals(1, requestedImages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed initial fetch retries on attribute change then keeps good image on later error`() = runTest {
        configureServer(SERVER_A, "https://server-a.example", integrationA, IMAGE_A)
        configureWidget()
        configureImageLoader()
        failImage = true

        updater.stateFlow(WIDGET_ID).test {
            awaitItem()
            awaitItem()
            val failed = awaitItem() as CameraWidgetState.Content
            assertEquals(null, failed.bitmap)
            assertTrue(failed.error)

            failImage = false
            updater.refreshFromEntity(WIDGET_ID, IMAGE_STATE)
            val recovered = awaitItem() as CameraWidgetState.Content
            assertSame(bitmap, recovered.bitmap)
            assertFalse(recovered.error)

            failImage = true
            updater.refresh(WIDGET_ID)
            val stale = awaitItem() as CameraWidgetState.Content
            assertSame(bitmap, stale.bitmap)
            assertTrue(stale.error)
            assertEquals(3, requestedImages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unavailable state keeps the last image without another fetch`() = runTest {
        configureServer(SERVER_A, "https://server-a.example", integrationA, IMAGE_A)
        configureImageLoader()
        configureWidget()

        updater.stateFlow(WIDGET_ID).test {
            awaitItem()
            awaitItem()
            val current = awaitItem() as CameraWidgetState.Content
            updater.refreshFromEntity(WIDGET_ID, "unavailable")
            runCurrent()
            expectNoEvents()
            assertSame(bitmap, current.bitmap)
            assertEquals(1, requestedImages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reconfigure cancels prior image state and loads only the newly selected server`() = runTest {
        configureServer(SERVER_A, "https://server-a.example", integrationA, IMAGE_A)
        configureServer(SERVER_B, "https://server-b.example/", integrationB, IMAGE_B)
        configureImageLoader()
        configureWidget()

        updater.stateFlow(WIDGET_ID).test {
            awaitItem()
            awaitItem()
            val first = awaitItem() as CameraWidgetState.Content
            assertSame(bitmap, first.bitmap)

            rows.value = listOf(CameraWidgetEntity(WIDGET_ID, SERVER_B, IMAGE_B, WidgetTapAction.OPEN))
            awaitItem() // New configuration starts loading and its initial refresh follows.
            val second = awaitItem() as CameraWidgetState.Content
            assertEquals(IMAGE_B, second.widget.entityId)
            assertEquals(SERVER_B, second.widget.serverId)
            assertEquals("https://server-b.example/api/camera_proxy/$IMAGE_B?auth=synthetic", requestedImages.last())
            coVerify(exactly = 0) { integrationA.getEntity(IMAGE_B) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changed image state fetches the updated picture`() = runTest {
        configureServer(SERVER_A, "https://server-a.example", integrationA, IMAGE_A)
        configureImageLoader()
        configureWidget()

        updater.stateFlow(WIDGET_ID).test {
            awaitItem()
            awaitItem()
            assertSame(bitmap, (awaitItem() as CameraWidgetState.Content).bitmap)
            stateA = "2026-11-26T10:05:00+00:00"
            updater.refreshFromEntity(WIDGET_ID, stateA)
            val updated = awaitItem() as CameraWidgetState.Content
            assertSame(bitmap, updated.bitmap)
            assertEquals(2, requestedImages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entity change arriving during delayed initial fetch is reconciled to the current image`() = runTest {
        configureServer(SERVER_A, "https://server-a.example", integrationA, IMAGE_A)
        configureWidget()
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val initialBitmap = mockk<Bitmap>()
        val currentBitmap = mockk<Bitmap>()
        coEvery { imageLoader.fetch(any(), any()) } coAnswers {
            requestedImages += firstArg<String>()
            if (requestedImages.size == 1) {
                fetchStarted.complete(Unit)
                releaseFetch.await()
                initialBitmap
            } else {
                currentBitmap
            }
        }

        updater.stateFlow(WIDGET_ID).test {
            awaitItem()
            awaitItem()
            fetchStarted.await()
            stateA = "2026-11-26T10:05:00+00:00"
            updater.refreshFromEntity(WIDGET_ID, stateA)
            releaseFetch.complete(Unit)

            val delayed = awaitItem() as CameraWidgetState.Content
            assertSame(initialBitmap, delayed.bitmap)
            val reconciled = awaitItem() as CameraWidgetState.Content
            assertEquals(2, requestedImages.size)
            assertSame(currentBitmap, reconciled.bitmap)
            coVerify(exactly = 2) { integrationA.getEntity(IMAGE_A) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `two active widgets stay isolated by id and configured server`() = runTest {
        configureServer(SERVER_A, "https://server-a.example", integrationA, IMAGE_A)
        configureServer(SERVER_B, "https://server-b.example", integrationB, IMAGE_B)
        val widgetB = WIDGET_ID + 1
        rows.value = listOf(
            CameraWidgetEntity(WIDGET_ID, SERVER_A, IMAGE_A, WidgetTapAction.REFRESH),
            CameraWidgetEntity(widgetB, SERVER_B, IMAGE_B, WidgetTapAction.REFRESH),
        )
        val bitmapA = mockk<Bitmap>()
        val bitmapB = mockk<Bitmap>()
        coEvery { imageLoader.fetch(any(), any()) } coAnswers {
            val url = firstArg<String>()
            requestedImages += "${secondArg<Int>()}::$url"
            if (url.startsWith("https://server-a.example")) bitmapA else bitmapB
        }

        updater.stateFlow(WIDGET_ID).test {
            awaitItem()
            awaitItem()
            assertSame(bitmapA, (awaitItem() as CameraWidgetState.Content).bitmap)
            updater.stateFlow(widgetB).test {
                awaitItem()
                awaitItem()
                assertSame(bitmapB, (awaitItem() as CameraWidgetState.Content).bitmap)
                stateA = "2026-11-26T10:05:00+00:00"
                updater.refreshFromEntity(WIDGET_ID, stateA)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            val refreshedA = awaitItem() as CameraWidgetState.Content
            assertSame(bitmapA, refreshedA.bitmap)
            assertEquals(
                listOf(
                    "$WIDGET_ID::https://server-a.example/api/camera_proxy/$IMAGE_A?auth=synthetic",
                    "$widgetB::https://server-b.example/api/camera_proxy/$IMAGE_B?auth=synthetic",
                    "$WIDGET_ID::https://server-a.example/api/camera_proxy/$IMAGE_A?auth=synthetic",
                ),
                requestedImages,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `camera image refresh ignores stable entity state`() = runTest {
        val cameraState = "recording"
        val provider = mockk<ServerConnectionStateProvider>()
        val cameraRepository = mockk<IntegrationRepository>()
        coEvery { serverManager.getServer(SERVER_A) } returns mockk()
        coEvery { serverManager.integrationRepository(SERVER_A) } returns cameraRepository
        coEvery { serverManager.connectionStateProvider(SERVER_A) } returns provider
        every { provider.urlFlow() } returns flowOf(UrlState.HasUrl(URL("https://server-a.example")))
        coEvery { cameraRepository.getEntity("camera.front_door") } returns entity("camera.front_door", cameraState)
        configureImageLoader()
        configureWidget("camera.front_door")

        updater.stateFlow(WIDGET_ID).test {
            awaitItem()
            awaitItem()
            assertSame(bitmap, (awaitItem() as CameraWidgetState.Content).bitmap)
            updater.refreshFromEntity(WIDGET_ID, cameraState)
            awaitItem()
            assertEquals(2, requestedImages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removed widget clears composition state and stops image work`() = runTest {
        configureServer(SERVER_A, "https://server-a.example", integrationA, IMAGE_A)
        configureImageLoader()
        configureWidget()

        updater.stateFlow(WIDGET_ID).test {
            awaitItem()
            awaitItem()
            assertSame(bitmap, (awaitItem() as CameraWidgetState.Content).bitmap)
            rows.value = emptyList()
            assertEquals(CameraWidgetState.Empty, awaitItem())
            updater.refresh(WIDGET_ID)
            runCurrent()
            expectNoEvents()
            assertEquals(1, requestedImages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `composition cancellation cancels an in-flight image fetch`() = runTest {
        configureServer(SERVER_A, "https://server-a.example", integrationA, IMAGE_A)
        configureWidget()
        val fetchStarted = CompletableDeferred<Unit>()
        coEvery { imageLoader.fetch(any(), any()) } coAnswers {
            fetchStarted.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }

        val collection = async { updater.stateFlow(WIDGET_ID).collect {} }
        fetchStarted.await()
        collection.cancelAndJoin()
        coVerify(exactly = 1) { imageLoader.fetch(any(), WIDGET_ID) }
    }
}
