package io.homeassistant.companion.android.widgets.camera

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.PowerManager
import android.widget.RemoteViews
import androidx.core.content.getSystemService
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.di.ServerManagerModule
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.util.hasActiveConnection
import io.homeassistant.companion.android.widgets.BaseWidgetProvider.Companion.UPDATE_WIDGETS
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import java.net.URL
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SERVER_ID = 1
private const val OTHER_SERVER_ID = 2
private const val WIDGET_A = 1
private const val WIDGET_B = 2
private const val IMAGE_A = "image.front_snapshot"
private const val IMAGE_B = "image.back_snapshot"
private const val CAMERA = "camera.front_door"
private const val FIRST_IMAGE = "2026-01-01T10:00:00+00:00"
private const val SECOND_IMAGE = "2026-01-01T10:05:00+00:00"

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(ServerManagerModule::class)
@HiltAndroidTest
class CameraWidgetTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // The watches are updated on the main thread
    @get:Rule(order = 1)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    @BindValue
    @JvmField
    val serverManager: ServerManager = mockk()

    @Inject
    lateinit var dao: CameraWidgetDao

    private val context: Context = RuntimeEnvironment.getApplication()
    private val appWidgetManager = mockk<AppWidgetManager>()
    private val repositories = mapOf(SERVER_ID to mockk<IntegrationRepository>(), OTHER_SERVER_ID to mockk())
    private val webSocketRepositories = mapOf(SERVER_ID to mockk<WebSocketRepository>(), OTHER_SERVER_ID to mockk())
    private val entityChanges = mutableMapOf<String, MutableSharedFlow<CompressedStateChangedEvent>>()
    private val entityStates = mutableMapOf<String, String>()
    private val entityQueries = Channel<String>(Channel.UNLIMITED)
    private val draws = Channel<Int>(Channel.UNLIMITED)
    private var imageFetchFails = false
    private var imagePublishFails = false
    private var holdNextImageFetch = false
    private var imageFetchStarted: CompletableDeferred<Unit>? = null
    private var releaseImageFetch: CompletableDeferred<Unit>? = null
    private var blockNextEntityFetch = false
    private var entityFetchStarted: CompletableDeferred<Unit>? = null
    private var releaseEntityFetch: CompletableDeferred<Unit>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        runTest { dao.deleteAll(intArrayOf(WIDGET_A, WIDGET_B)) }
        mockkStatic("io.homeassistant.companion.android.util.ContextExtensionsKt")
        every { any<Context>().hasActiveConnection() } returns true
        mockkStatic(AppWidgetManager::class)
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager
        every { appWidgetManager.getAppWidgetIds(any()) } returns intArrayOf(WIDGET_A, WIDGET_B)
        every { appWidgetManager.getAppWidgetOptions(any()) } returns Bundle()
        every { appWidgetManager.updateAppWidget(any<Int>(), any<RemoteViews>()) } answers {
            if (imagePublishFails) throw IllegalStateException("Widget host unavailable")
            draws.trySend(firstArg())
        }

        val connectionStateProvider = mockk<ServerConnectionStateProvider>()
        every { connectionStateProvider.urlFlow(any()) } returns flowOf(UrlState.HasUrl(URL("http://ha.test:8123")))
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer(any<Int>()) } returns mockk()
        coEvery { serverManager.connectionStateProvider(any()) } returns connectionStateProvider
        repositories.forEach { (serverId, repository) ->
            coEvery { serverManager.integrationRepository(serverId) } returns repository
            coEvery { serverManager.webSocketRepository(serverId) } returns webSocketRepositories.getValue(serverId)
            coEvery { repository.getEntity(any()) } coAnswers {
                val entityId = firstArg<String>()
                if (blockNextEntityFetch) {
                    blockNextEntityFetch = false
                    entityFetchStarted?.complete(Unit)
                    releaseEntityFetch?.await()
                }
                entityQueries.trySend(entityId)
                entityStates[entityId]?.let { entity(entityId, it) }
            }
            coEvery { webSocketRepositories.getValue(serverId).getCompressedStateAndChanges(any()) } answers {
                changesOf(firstArg<EntityFilter>().entityIds.single())
            }
        }

        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).components {
                add(
                    Interceptor { chain ->
                        if (holdNextImageFetch) {
                            holdNextImageFetch = false
                            imageFetchStarted?.complete(Unit)
                            releaseImageFetch?.await()
                        }
                        if (imageFetchFails) {
                            ErrorResult(null, chain.request, IOException("Image unavailable"))
                        } else {
                            SuccessResult(
                                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImage(),
                                chain.request,
                                DataSource.NETWORK,
                            )
                        }
                    },
                )
            }.build(),
        )
    }

    @After
    fun tearDown() {
        // The watches and drawn states are shared by every instance, forget them for the next test
        runTest {
            CameraWidget(this).apply {
                onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
                onDeleted(context, intArrayOf(WIDGET_A, WIDGET_B))
            }
        }
        SingletonImageLoader.reset()
        unmockkAll()
    }

    @Test
    fun `Given image widget drawn when screen turns on with the same image then widget is not redrawn`() = runTest {
        val widget = configureImageWidget()
        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))

        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        changesOf(IMAGE_A).subscriptionCount.first { it == 1 }
        entityQueries.receive()
        changesOf(IMAGE_A).emit(stateChanged(IMAGE_A, FIRST_IMAGE))
        settle(IMAGE_A)

        assertNoDraw()
    }

    @Test
    fun `Given image widget watched when the image changes then widget is redrawn`() = runTest {
        configureImageWidget()

        changeImage(IMAGE_A, SECOND_IMAGE)

        assertEquals(WIDGET_A, draws.receive())
    }

    @Test
    fun `Given image changes while widget watch is stopped when watch restarts then latest image is drawn`() = runTest {
        val widget = configureImageWidget()
        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
        runCurrent()
        entityStates[IMAGE_A] = SECOND_IMAGE

        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        changesOf(IMAGE_A).subscriptionCount.first { it == 1 }
        entityQueries.receive()

        assertEquals(WIDGET_A, draws.receive())
        settle(IMAGE_A)
        assertNoDraw()
    }

    @Test
    fun `Given image widget watched when only the attributes change then widget is not redrawn`() = runTest {
        configureImageWidget()
        coVerify(exactly = 2) { repositories.getValue(SERVER_ID).getEntity(IMAGE_A) }

        changesOf(IMAGE_A).emit(attributeChanged(IMAGE_A))
        settle(IMAGE_A)

        assertNoDraw()
        coVerify(exactly = 2) { repositories.getValue(SERVER_ID).getEntity(IMAGE_A) }
    }

    @Test
    fun `Given image widget watched when the entity becomes unavailable then widget keeps its image`() = runTest {
        configureImageWidget()

        changeImage(IMAGE_A, STATE_UNAVAILABLE)
        settle(IMAGE_A)

        assertNoDraw()
    }

    @Test
    fun `Given image widget watched when the entity is removed then widget is not redrawn`() = runTest {
        configureImageWidget()

        changesOf(IMAGE_A).emit(removedEvent(IMAGE_A))
        settle(IMAGE_A)

        assertNoDraw()
    }

    @Test
    fun `Given image widget watched when screen turns off then later image changes are not drawn`() = runTest {
        val widget = configureImageWidget()

        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
        runCurrent()
        changeImage(IMAGE_A, SECOND_IMAGE)
        runCurrent()

        assertEquals(0, changesOf(IMAGE_A).subscriptionCount.value)
        assertNoDraw()
    }

    @Test
    fun `Given image widget watched when screen turns on again then the subscription is kept`() = runTest {
        val widget = configureImageWidget()
        val watching = backgroundScope.coroutineContext.job.children.toSet()

        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        awaitWidgetWork(except = watching)

        assertEquals(1, changesOf(IMAGE_A).subscriptionCount.value)
        coVerify(exactly = 1) { webSocketRepositories.getValue(SERVER_ID).getCompressedStateAndChanges(any()) }
    }

    @Test
    fun `Given screen off when widgets are updated then image entity is not watched`() = runTest {
        context.getSystemService<PowerManager>()?.let { shadowOf(it).turnScreenOn(false) }
        dao.add(widgetEntity(WIDGET_A, IMAGE_A))
        entityStates[IMAGE_A] = FIRST_IMAGE
        val widget = CameraWidget(backgroundScope)

        widget.onReceive(context, Intent(UPDATE_WIDGETS))
        assertEquals(WIDGET_A, draws.receive())
        awaitWidgetWork()

        coVerify(exactly = 0) { webSocketRepositories.getValue(SERVER_ID).getCompressedStateAndChanges(any()) }
    }

    @Test
    fun `Given camera widget when screen turns on then camera entity is not watched`() = runTest {
        dao.add(widgetEntity(WIDGET_A, CAMERA))
        entityStates[CAMERA] = "idle"
        val widget = CameraWidget(backgroundScope)

        widget.onReceive(context, Intent(UPDATE_WIDGETS))
        assertEquals(WIDGET_A, draws.receive())
        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        awaitWidgetWork()

        coVerify(exactly = 0) { webSocketRepositories.getValue(SERVER_ID).getCompressedStateAndChanges(any()) }
    }

    @Test
    fun `Given reconfigured image fetch fails with same timestamp when watch restarts then it retries`() = runTest {
        val widget = configureImageWidget()
        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
        changesOf(IMAGE_A).subscriptionCount.first { it == 0 }
        dao.add(widgetEntity(WIDGET_A, IMAGE_B))
        entityStates[IMAGE_B] = FIRST_IMAGE
        context.getSystemService<PowerManager>()?.let { shadowOf(it).turnScreenOn(false) }
        imageFetchFails = true
        widget.onReceive(context, Intent(UPDATE_WIDGETS))
        assertEquals(WIDGET_A, draws.receive())
        awaitWidgetWork()

        imageFetchFails = false
        context.getSystemService<PowerManager>()?.let { shadowOf(it).turnScreenOn(true) }
        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        changesOf(IMAGE_B).subscriptionCount.first { it == 1 }
        assertEquals(WIDGET_A, draws.receive())
    }

    @Test
    fun `Given image never fetched when an attribute update arrives then image fetch is retried`() = runTest {
        imageFetchFails = true
        dao.add(widgetEntity(WIDGET_A, IMAGE_A))
        entityStates[IMAGE_A] = FIRST_IMAGE
        val widget = CameraWidget(backgroundScope)
        widget.onReceive(context, Intent(UPDATE_WIDGETS))
        assertEquals(WIDGET_A, draws.receive())
        changesOf(IMAGE_A).subscriptionCount.first { it == 1 }
        assertEquals(WIDGET_A, draws.receive())

        imageFetchFails = false
        changesOf(IMAGE_A).emit(attributeChanged(IMAGE_A))

        assertEquals(WIDGET_A, draws.receive())
    }

    @Test
    fun `Given same image refetch fails after a successful draw when same state returns then image fetch is retried`() = runTest {
        val widget = configureImageWidget()
        imageFetchFails = true
        widget.onReceive(
            context,
            Intent(CameraWidget.UPDATE_IMAGE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_A),
        )
        assertEquals(WIDGET_A, draws.receive())

        imageFetchFails = false
        changesOf(IMAGE_A).emit(attributeChanged(IMAGE_A))

        assertEquals(WIDGET_A, draws.receive())
    }

    @Test
    fun `Given a render overlaps an image event when the first draw publishes then the queued event does not redraw it`() = runTest {
        val widget = configureImageWidget()
        entityStates[IMAGE_A] = SECOND_IMAGE
        imageFetchStarted = CompletableDeferred()
        releaseImageFetch = CompletableDeferred()
        holdNextImageFetch = true

        widget.onReceive(context, Intent(UPDATE_WIDGETS))
        imageFetchStarted?.await()
        val event = backgroundScope.launch { changesOf(IMAGE_A).emit(stateChanged(IMAGE_A, SECOND_IMAGE)) }
        runCurrent()

        releaseImageFetch?.complete(Unit)
        assertEquals(WIDGET_A, draws.receive())
        event.join()
        settle(IMAGE_A)

        assertNoDraw()
    }

    @Test
    fun `Given a no replay watcher receives a change during its initial state check then it draws the latest state once`() = runTest {
        val widget = configureImageWidget()
        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
        changesOf(IMAGE_A).subscriptionCount.first { it == 0 }
        entityFetchStarted = CompletableDeferred()
        releaseEntityFetch = CompletableDeferred()
        blockNextEntityFetch = true

        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        changesOf(IMAGE_A).subscriptionCount.first { it == 1 }
        entityFetchStarted?.await()
        entityStates[IMAGE_A] = SECOND_IMAGE
        val event = backgroundScope.launch { changesOf(IMAGE_A).emit(stateChanged(IMAGE_A, SECOND_IMAGE)) }
        runCurrent()

        releaseEntityFetch?.complete(Unit)
        entityQueries.receive()
        assertEquals(WIDGET_A, draws.receive())
        event.join()
        settle(IMAGE_A)
        assertNoDraw()
    }

    @Test
    fun `Given widget host rejects a state refresh when same change arrives again then watch stays alive and retries`() = runTest {
        configureImageWidget()
        entityStates[IMAGE_A] = SECOND_IMAGE
        imagePublishFails = true
        changesOf(IMAGE_A).emit(stateChanged(IMAGE_A, SECOND_IMAGE))
        settle(IMAGE_A)
        assertNoDraw()

        imagePublishFails = false
        changesOf(IMAGE_A).emit(stateChanged(IMAGE_A, SECOND_IMAGE))
        assertEquals(WIDGET_A, draws.receive())
    }

    @Test
    fun `Given image widget not drawn since the app started when screen turns on then widget is drawn once`() = runTest {
        dao.add(widgetEntity(WIDGET_A, IMAGE_A))
        entityStates[IMAGE_A] = FIRST_IMAGE
        val widget = CameraWidget(backgroundScope)

        widget.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        changesOf(IMAGE_A).subscriptionCount.first { it == 1 }
        assertEquals(WIDGET_A, draws.receive())
        changesOf(IMAGE_A).emit(stateChanged(IMAGE_A, FIRST_IMAGE))
        settle(IMAGE_A)

        assertNoDraw()
    }

    @Test
    fun `Given image widget watched when it is deleted then its subscription stops`() = runTest {
        val widget = configureImageWidget()

        widget.onReceive(
            context,
            Intent(AppWidgetManager.ACTION_APPWIDGET_DELETED).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_A),
        )
        changesOf(IMAGE_A).subscriptionCount.first { it == 0 }
        changesOf(IMAGE_A).emit(stateChanged(IMAGE_A, FIRST_IMAGE, token = "rotated"))
        runCurrent()

        assertNoDraw()
    }

    @Test
    fun `Given image widget watched when it is reconfigured to another image then the new image is watched`() = runTest {
        val widget = configureImageWidget()

        dao.add(widgetEntity(WIDGET_A, IMAGE_B))
        entityStates[IMAGE_B] = FIRST_IMAGE
        widget.onReceive(context, Intent(UPDATE_WIDGETS))
        assertEquals(WIDGET_A, draws.receive())
        changesOf(IMAGE_B).subscriptionCount.first { it == 1 }

        assertEquals(0, changesOf(IMAGE_A).subscriptionCount.value)
        changeImage(IMAGE_B, SECOND_IMAGE)
        assertEquals(WIDGET_A, draws.receive())
    }

    @Test
    fun `Given image widget watched when it is reconfigured to a camera then nothing is watched`() = runTest {
        val widget = configureImageWidget()

        dao.add(widgetEntity(WIDGET_A, CAMERA))
        entityStates[CAMERA] = "idle"
        widget.onReceive(context, Intent(UPDATE_WIDGETS))
        assertEquals(WIDGET_A, draws.receive())
        changesOf(IMAGE_A).subscriptionCount.first { it == 0 }
        runCurrent()
        changesOf(IMAGE_A).emit(stateChanged(IMAGE_A, SECOND_IMAGE))
        assertNoDraw()

        coVerify(exactly = 0) { webSocketRepositories.getValue(SERVER_ID).getCompressedStateAndChanges(EntityFilter(entityIds = setOf(CAMERA))) }
    }

    @Test
    fun `Given two image widgets on two servers when one image changes then only its widget is redrawn`() = runTest {
        dao.add(widgetEntity(WIDGET_A, IMAGE_A))
        dao.add(widgetEntity(WIDGET_B, IMAGE_B, serverId = OTHER_SERVER_ID))
        entityStates[IMAGE_A] = FIRST_IMAGE
        entityStates[IMAGE_B] = FIRST_IMAGE
        val widget = CameraWidget(backgroundScope)
        widget.onReceive(context, Intent(UPDATE_WIDGETS))
        assertEquals(setOf(WIDGET_A, WIDGET_B), setOf(draws.receive(), draws.receive()))
        changesOf(IMAGE_A).subscriptionCount.first { it == 1 }
        changesOf(IMAGE_B).subscriptionCount.first { it == 1 }

        changeImage(IMAGE_B, SECOND_IMAGE)

        assertEquals(WIDGET_B, draws.receive())
        settle(IMAGE_A)
        settle(IMAGE_B)
        assertNoDraw()
        coVerify { webSocketRepositories.getValue(OTHER_SERVER_ID).getCompressedStateAndChanges(EntityFilter(entityIds = setOf(IMAGE_B))) }
    }

    /** Configures a widget on [IMAGE_A] through the configure screen broadcast and waits for its draw and watch. */
    private suspend fun TestScope.configureImageWidget(scope: CoroutineScope = backgroundScope): CameraWidget {
        dao.add(widgetEntity(WIDGET_A, IMAGE_A))
        entityStates[IMAGE_A] = FIRST_IMAGE
        val widget = CameraWidget(scope)
        widget.onReceive(context, Intent(UPDATE_WIDGETS))
        assertEquals(WIDGET_A, draws.receive())
        assertEquals(IMAGE_A, entityQueries.receive())
        changesOf(IMAGE_A).subscriptionCount.first { it == 1 }
        assertEquals(IMAGE_A, entityQueries.receive())
        return widget
    }

    /** Waits for the widget's work, other than the watches in [except], to finish. */
    private suspend fun TestScope.awaitWidgetWork(except: Set<Job> = emptySet()) {
        do {
            val running = backgroundScope.coroutineContext.job.children.filterNot { it in except }.toList()
            running.forEach { it.join() }
        } while (running.isNotEmpty())
    }

    /**
     * Returns once the watch of [entityId] has fully handled the previous change, draw included:
     * the watch only takes the next change, here a removal it ignores, after finishing the previous one.
     */
    private suspend fun settle(entityId: String) {
        changesOf(entityId).emit(removedEvent(entityId))
    }

    private suspend fun changeImage(entityId: String, state: String) {
        entityStates[entityId] = state
        changesOf(entityId).emit(stateChanged(entityId, state))
    }

    private fun assertNoDraw() {
        assertTrue("Unexpected redraw", draws.tryReceive().isFailure)
    }

    private fun changesOf(entityId: String) = entityChanges.getOrPut(entityId) { MutableSharedFlow() }

    private fun stateChanged(entityId: String, state: String, token: String = "initial") = CompressedStateChangedEvent(
        changed = mapOf(
            entityId to CompressedStateDiff(
                plus = CompressedEntityState(
                    state = JsonPrimitive(state),
                    attributes = mapOf("token" to token),
                    lastChanged = 1_700_000_000.0,
                ),
            ),
        ),
    )

    private fun attributeChanged(entityId: String) = CompressedStateChangedEvent(
        changed = mapOf(entityId to CompressedStateDiff(plus = CompressedEntityState(attributes = mapOf("token" to "rotated")))),
    )

    private fun removedEvent(entityId: String) = CompressedStateChangedEvent(removed = listOf(entityId))

    private fun entity(entityId: String, state: String, token: String = "initial") = Entity(
        entityId = entityId,
        state = state,
        attributes = mapOf("entity_picture" to "/api/image_proxy/$entityId?token=$token"),
        lastChanged = LocalDateTime.now(),
        lastUpdated = LocalDateTime.now(),
    )

    private fun widgetEntity(id: Int, entityId: String, serverId: Int = SERVER_ID) = CameraWidgetEntity(
        id = id,
        serverId = serverId,
        entityId = entityId,
        tapAction = WidgetTapAction.REFRESH,
    )
}
