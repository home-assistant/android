package io.homeassistant.companion.android.mediacontrols

import android.os.Looper
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.MediaPlaybackState
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.mediacontrols.MediaControlsEntityConfig
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.util.loadedState
import io.homeassistant.companion.android.util.mediaDisplayItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SERVER_ID = 1

/** Counter used to generate unique MediaSession IDs across tests within the same JVM process. */
private val sessionCounter = AtomicInteger(0)

@OptIn(ExperimentalTime::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HaMediaSessionTest {

    private lateinit var testScope: CoroutineScope
    private lateinit var entitiesForDisplayManager: EntitiesForDisplayManager
    private lateinit var serverManager: ServerManager
    private lateinit var integrationRepository: IntegrationRepository
    private lateinit var config: MediaControlsEntityConfig
    private val fakeClock = FakeClock()

    @After
    fun tearDown() {
        // Cancel all test coroutines and drain the main looper so that the observe() finally
        // block's withContext(NonCancellable + Dispatchers.Main) call completes and
        // session.release() runs. Without this, MediaSession IDs linger in Media3's global
        // registry and cause "Session ID must be unique" failures in subsequent test classes.
        testScope.cancel()
        idleMainLooper()
    }

    @Before
    fun setUp() {
        @OptIn(ExperimentalCoroutinesApi::class)
        testScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        entitiesForDisplayManager = mockk()
        serverManager = mockk()
        integrationRepository = mockk(relaxed = true)

        val uniqueEntityId = "media_player.test_${sessionCounter.incrementAndGet()}"
        config = MediaControlsEntityConfig(serverId = SERVER_ID, entityId = uniqueEntityId)

        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns flowOf()
        coEvery { serverManager.integrationRepository(SERVER_ID) } returns integrationRepository
    }

    private fun createState(
        playbackState: MediaPlaybackState = MediaPlaybackState.Playing,
        title: String? = "Test Title",
        entityPictureUrl: String? = null,
    ) = mediaDisplayItem(
        entityId = config.entityId,
        playbackState = playbackState,
        title = title,
        artist = null,
        albumName = null,
        entityPicturePath = entityPictureUrl,
        mediaDuration = 300.0.seconds,
        mediaPosition = 60.0.seconds,
        supportsSeek = false,
        supportsPreviousTrack = false,
        supportsNextTrack = false,
    )

    private fun buildSession(): HaMediaSession = HaMediaSession(
        context = ApplicationProvider.getApplicationContext(),
        config = config,
        entitiesForDisplayManager = entitiesForDisplayManager,
        serverManager = serverManager,
        clock = fakeClock,
    )

    /**
     * Drains the Robolectric main looper so that `player.updateState` calls dispatched via
     * `withContext(Dispatchers.Main)` take effect.
     *
     * `testScope` uses [UnconfinedTestDispatcher], so coroutines run eagerly on the calling
     * thread until they reach a `withContext(Dispatchers.Main)` suspension point. A single
     * `idle()` is enough to flush those pending main-looper tasks and resume the coroutine.
     */
    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    // -- State observation tests --

    @Test
    fun `Given observeEntityState emits state then null when startObservingState then player retains initial state`() {
        val stateFlow = MutableSharedFlow<EntityDisplayState<EntityDisplayWithoutContext>>(replay = 1)
        stateFlow.tryEmit(loadedState(createState(playbackState = MediaPlaybackState.Playing)))
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns stateFlow

        val session = buildSession()
        var capturedSession: androidx.media3.session.MediaSession? = null
        val job = testScope.launch {
            session.observe { capturedSession = it }
        }
        idleMainLooper()

        val player = capturedSession?.player
        assertEquals(Player.STATE_READY, player?.playbackState)
        assertEquals(true, player?.playWhenReady)

        // Emitting null afterwards (simulating WebSocket-not-ready) should not clear state
        stateFlow.tryEmit(loadedState())
        idleMainLooper()

        assertEquals(Player.STATE_READY, player?.playbackState)
        assertEquals(true, player?.playWhenReady)

        job.cancel()
    }

    /**
     * Verifies that when `observeEntityState` emits a playing state, the player transitions
     * to STATE_READY with `playWhenReady = true`.
     *
     * Uses `replay=1` so the emission is cached and replayed to the collector on
     * [UnconfinedTestDispatcher] regardless of when it subscribes. The flow stays open.
     */
    @Test
    fun `Given observeEntityState emits playing state when startObservingState then player is ready and playing`() {
        val stateFlow = MutableSharedFlow<EntityDisplayState<EntityDisplayWithoutContext>>(replay = 1)
        stateFlow.tryEmit(loadedState(createState(playbackState = MediaPlaybackState.Playing)))
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns stateFlow

        val session = buildSession()
        var capturedSession: androidx.media3.session.MediaSession? = null
        val job = testScope.launch {
            session.observe { capturedSession = it }
        }
        idleMainLooper()

        val player = capturedSession?.player
        assertEquals(Player.STATE_READY, player?.playbackState)
        assertEquals(true, player?.playWhenReady)

        job.cancel()
    }

    /**
     * Verifies that when `observeEntityState` emits a paused state, the player transitions
     * to STATE_READY with `playWhenReady = false`.
     *
     * Uses `replay=1` so the emission is cached and replayed to the late collector.
     */
    @Test
    fun `Given observeEntityState emits paused state when startObservingState then player is ready and not playing`() {
        val stateFlow = MutableSharedFlow<EntityDisplayState<EntityDisplayWithoutContext>>(replay = 1)
        stateFlow.tryEmit(loadedState(createState(playbackState = MediaPlaybackState.Paused)))
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns stateFlow

        val session = buildSession()
        var capturedSession: androidx.media3.session.MediaSession? = null
        val job = testScope.launch {
            session.observe { capturedSession = it }
        }
        idleMainLooper()

        val player = capturedSession?.player
        assertEquals(Player.STATE_READY, player?.playbackState)
        assertEquals(false, player?.playWhenReady)

        job.cancel()
    }

    /**
     * Verifies that when `observeEntityState` flow completes naturally (e.g. WebSocket subscription
     * ended), `observe()` returns normally and tears down the session. `mediaSession` becomes null
     * and `buildNotification()` returns null, preventing a stale notification from remaining.
     */
    @Test
    fun `Given observeEntityState flow completes when startObservingState then session is torn down`() {
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns flowOf(
            loadedState(createState(playbackState = MediaPlaybackState.Playing)),
        )

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        // The flow completed, so observe() exited via its finally block — session is torn down.
        assertNull(session.buildNotification())
        org.junit.Assert.assertFalse(job.isActive)
    }

    // -- Artwork caching tests --

    /**
     * Verifies that when the emitted state has a null artwork URL, the player's media metadata
     * contains no artwork bytes.
     *
     * Uses `replay=1` so the emission is available immediately when the collector starts.
     */
    @Test
    fun `Given state with null artwork URL when startObservingState then player artwork is null`() {
        val stateFlow = MutableSharedFlow<EntityDisplayState<EntityDisplayWithoutContext>>(replay = 1)
        stateFlow.tryEmit(loadedState(createState(entityPictureUrl = null)))
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns stateFlow

        val session = buildSession()
        var capturedSession: androidx.media3.session.MediaSession? = null
        val job = testScope.launch {
            session.observe { capturedSession = it }
        }
        idleMainLooper()

        val player = capturedSession?.player
        assertNull(player?.mediaMetadata?.artworkData)

        job.cancel()
    }

    /**
     * Verifies that when a second state emission arrives with a null artwork URL, the player
     * state still updates — the second state's title is applied and artwork stays null.
     *
     * Uses `replay=1` for reliable delivery to the collector. The second emission is made after
     * the first is confirmed to be processed.
     */
    @Test
    fun `Given two consecutive states both with null artwork URL when startObservingState then title updates and artwork stays null`() {
        val stateFlow = MutableSharedFlow<EntityDisplayState<EntityDisplayWithoutContext>>(replay = 1)
        stateFlow.tryEmit(loadedState(createState(entityPictureUrl = null, title = "Track 1")))
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns stateFlow

        val session = buildSession()
        var capturedSession: androidx.media3.session.MediaSession? = null
        val job = testScope.launch {
            session.observe { capturedSession = it }
        }
        idleMainLooper()

        stateFlow.tryEmit(loadedState(createState(entityPictureUrl = null, title = "Track 2")))
        idleMainLooper()

        val player = capturedSession?.player
        assertNull(player?.mediaMetadata?.artworkData)
        assertEquals("Track 2", player?.mediaMetadata?.title?.toString())

        job.cancel()
    }

    // -- callMediaAction tests --

    /**
     * Verifies that triggering play on the media session player causes `callMediaAction` to
     * dispatch a `media_play` action to the integration repository for the configured entity.
     *
     * Uses `replay=1` so the paused state is reliably received by the collector before
     * `player.play()` is invoked. `callMediaAction` launches on [UnconfinedTestDispatcher] and
     * runs eagerly inside the main looper drain, so no additional wait is required.
     */
    @Test
    fun `Given paused player when play requested then media_play action is called`() {
        val stateFlow = MutableSharedFlow<EntityDisplayState<EntityDisplayWithoutContext>>(replay = 1)
        stateFlow.tryEmit(loadedState(createState(playbackState = MediaPlaybackState.Paused)))
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns stateFlow

        val session = buildSession()
        var capturedSession: androidx.media3.session.MediaSession? = null
        val job = testScope.launch {
            session.observe { capturedSession = it }
        }
        idleMainLooper()

        capturedSession?.player?.play()
        shadowOf(Looper.getMainLooper()).idle()

        val capturedDomain = slot<String>()
        val capturedAction = slot<String>()
        coVerify {
            integrationRepository.callAction(
                domain = capture(capturedDomain),
                action = capture(capturedAction),
                actionData = any(),
            )
        }
        assertEquals(MEDIA_PLAYER_DOMAIN, capturedDomain.captured)
        assertEquals("media_play", capturedAction.captured)

        job.cancel()
    }

    /**
     * Verifies that triggering pause dispatches a `media_pause` action to the integration
     * repository.
     *
     * Uses `replay=1` so the playing state is reliably received before `player.pause()` is called.
     */
    @Test
    fun `Given playing player when pause requested then media_pause action is called`() {
        val stateFlow = MutableSharedFlow<EntityDisplayState<EntityDisplayWithoutContext>>(replay = 1)
        stateFlow.tryEmit(loadedState(createState(playbackState = MediaPlaybackState.Playing)))
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns stateFlow

        val session = buildSession()
        var capturedSession: androidx.media3.session.MediaSession? = null
        val job = testScope.launch {
            session.observe { capturedSession = it }
        }
        idleMainLooper()

        capturedSession?.player?.pause()
        shadowOf(Looper.getMainLooper()).idle()

        val capturedAction = slot<String>()
        coVerify {
            integrationRepository.callAction(
                domain = any(),
                action = capture(capturedAction),
                actionData = any(),
            )
        }
        assertEquals("media_pause", capturedAction.captured)

        job.cancel()
    }

    /**
     * Verifies that a failed action does not leave the player showing it as if it had succeeded.
     *
     * Media3 holds the optimistic placeholder state until the command future completes, and a
     * failed action changes nothing on the server, so no state update arrives to complete it.
     * `callMediaAction` therefore rethrows, which fails the future and makes Media3 re-read the
     * real state.
     */
    @Test
    fun `Given callAction throws when play requested then the player reverts to the real state`() {
        val stateFlow = MutableSharedFlow<EntityDisplayState<EntityDisplayWithoutContext>>(replay = 1)
        stateFlow.tryEmit(loadedState(createState(playbackState = MediaPlaybackState.Paused)))
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns stateFlow
        coEvery {
            integrationRepository.callAction(any(), any(), any())
        } throws RuntimeException("Simulated server error")

        val session = buildSession()
        var capturedSession: androidx.media3.session.MediaSession? = null
        val job = testScope.launch {
            session.observe { capturedSession = it }
        }
        idleMainLooper()

        val player = capturedSession?.player as HaRemoteMediaPlayer
        player.play()
        shadowOf(Looper.getMainLooper()).idle()

        coVerify {
            integrationRepository.callAction(
                domain = MEDIA_PLAYER_DOMAIN,
                action = "media_play",
                actionData = any(),
            )
        }
        // The future is resolved rather than waiting for a state update that will never come, so
        // the optimistic "playing" placeholder is dropped for the entity's real paused state
        assertTrue(player.pendingCommandFuture?.isDone ?: false)
        assertFalse(player.playWhenReady)

        job.cancel()
    }

    // -- observe() lifecycle tests --

    /**
     * Verifies that the session is active (produces a notification) during observation and
     * becomes inactive after the observing job is cancelled, confirming Media3 resources are released.
     */
    @Test
    fun `Given observing session when job cancelled then session is no longer active`() {
        val stateFlow = MutableSharedFlow<EntityDisplayState<EntityDisplayWithoutContext>>(replay = 1)
        stateFlow.tryEmit(loadedState(createState(playbackState = MediaPlaybackState.Playing)))
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns stateFlow

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        assertNotNull(session.buildNotification())

        job.cancel()
        idleMainLooper()

        assertNull(session.buildNotification())
    }

    /**
     * Verifies that [HaMediaSession.observe] calls [onSessionReady] with a non-null session
     * before starting state observation.
     */
    @Test
    fun `Given session when observe called then onSessionReady is invoked with the session`() {
        val stateFlow = MutableSharedFlow<EntityDisplayState<EntityDisplayWithoutContext>>()
        every { entitiesForDisplayManager.observe(SERVER_ID, listOf(config.entityId)) } returns stateFlow

        val session = buildSession()
        var capturedSession: androidx.media3.session.MediaSession? = null
        val job = testScope.launch {
            session.observe { capturedSession = it }
        }
        idleMainLooper()

        assertNotNull(capturedSession)

        job.cancel()
    }
}
