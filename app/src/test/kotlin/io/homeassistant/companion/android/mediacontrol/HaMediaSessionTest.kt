package io.homeassistant.companion.android.mediacontrol

import android.os.Looper
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaPlaybackState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaRepeatMode
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SERVER_ID = 1

/** Counter used to generate unique MediaSession IDs across tests within the same JVM process. */
private val sessionCounter = AtomicInteger(0)

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HaMediaSessionTest {

    private lateinit var testScope: CoroutineScope
    private lateinit var mediaControlRepository: MediaControlRepository
    private lateinit var serverManager: ServerManager
    private lateinit var integrationRepository: IntegrationRepository
    private lateinit var config: MediaControlEntityConfig

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
        mediaControlRepository = mockk()
        serverManager = mockk()
        integrationRepository = mockk(relaxed = true)

        val uniqueEntityId = "media_player.test_${sessionCounter.incrementAndGet()}"
        config = MediaControlEntityConfig(serverId = SERVER_ID, entityId = uniqueEntityId)

        coEvery { mediaControlRepository.observeEntityState(config) } returns flowOf()
        coEvery { serverManager.integrationRepository(SERVER_ID) } returns integrationRepository
    }

    private fun createState(
        playbackState: MediaPlaybackState = MediaPlaybackState.Playing,
        title: String? = "Test Title",
        entityPictureUrl: String? = null,
    ) = MediaControlState(
        entityId = config.entityId,
        serverId = SERVER_ID,
        playbackState = playbackState,
        title = title,
        artist = null,
        albumName = null,
        entityPictureUrl = entityPictureUrl,
        mediaDuration = 300.0.seconds,
        mediaPosition = 60.0.seconds,
        supportsPause = true,
        supportsPlay = true,
        supportsSeek = false,
        supportsPreviousTrack = false,
        supportsNextTrack = false,
        supportsVolumeSet = false,
        supportsStop = false,
        supportsMute = false,
        supportsShuffleSet = false,
        supportsRepeatSet = false,
        volumeLevel = null,
        isVolumeMuted = false,
        shuffle = false,
        repeatMode = MediaRepeatMode.Off,
        entityFriendlyName = null,
    )

    private fun buildSession(): HaMediaSession = HaMediaSession(
        context = ApplicationProvider.getApplicationContext(),
        config = config,
        mediaControlRepository = mediaControlRepository,
        serverManager = serverManager,
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

    /**
     * Verifies the cold-start recovery path: when `observeEntityState` emits the current state
     * first (as a REST pre-fetch inside the repository) followed by null (WebSocket not ready)
     * but stays open, the player retains the emitted state and does not drop to idle.
     *
     * Uses a `MutableSharedFlow` with `replay=1` so emissions are received by the Default
     * dispatcher collector without racing, and the flow stays open.
     */
    @Test
    fun `Given observeEntityState emits state then null when startObservingState then player retains initial state`() {
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(playbackState = MediaPlaybackState.Playing))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        val job = testScope.launch {
            session.observe { /* no-op: addSession not needed in unit tests */ }
        }
        idleMainLooper()

        val player = session.mediaSession?.player
        assertEquals(Player.STATE_READY, player?.playbackState)
        assertEquals(true, player?.playWhenReady)

        // Emitting null afterwards (simulating WebSocket-not-ready) should not clear state
        stateFlow.tryEmit(null)
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
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(playbackState = MediaPlaybackState.Playing))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        val player = session.mediaSession?.player
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
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(playbackState = MediaPlaybackState.Paused))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        val player = session.mediaSession?.player
        assertEquals(Player.STATE_READY, player?.playbackState)
        assertEquals(false, player?.playWhenReady)

        job.cancel()
    }

    /**
     * Verifies that when `observeEntityState` flow completes naturally (e.g. WebSocket disconnected),
     * the session stays alive — `observe()` keeps running via `awaitCancellation()` and
     * `mediaSession` remains non-null so the notification is not removed.
     */
    @Test
    fun `Given observeEntityState flow completes when startObservingState then session stays alive`() {
        coEvery { mediaControlRepository.observeEntityState(config) } returns flowOf(
            createState(playbackState = MediaPlaybackState.Playing),
        )

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        // The observation job completed naturally but observe() is still suspended in
        // awaitCancellation(), so the session and its notification remain active.
        assertNotNull(session.mediaSession)
        org.junit.Assert.assertTrue(job.isActive)

        job.cancel()
    }

    /**
     * Verifies that after the observation flow ends (simulating a WebSocket disconnect),
     * a successful media command restarts state observation so future state updates are received.
     *
     * This covers the reconnection path: user taps play → REST call succeeds → `callMediaAction`
     * sees `observationJob.isActive == false` and re-launches `startObservingState()`.
     */
    @Test
    fun `Given observation ended when play requested and action succeeds then observation is restarted`() {
        // First call: immediately-completing flow (simulates WebSocket disconnect).
        // Second call: open flow that stays alive so we can verify it was subscribed to.
        val reopenedFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        reopenedFlow.tryEmit(createState(playbackState = MediaPlaybackState.Paused))
        var callCount = 0
        coEvery { mediaControlRepository.observeEntityState(config) } answers {
            callCount++
            if (callCount == 1) {
                flowOf(createState(playbackState = MediaPlaybackState.Playing))
            } else {
                reopenedFlow
            }
        }

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        // After the first flow completes, observation is inactive but observe() is still alive.
        assertNotNull(session.mediaSession)

        // Trigger a play command; the action succeeds (integrationRepository is relaxed).
        session.mediaSession?.player?.play()
        shadowOf(Looper.getMainLooper()).idle()

        // observeEntityState should have been called a second time (observation restarted).
        assertEquals(2, callCount)

        job.cancel()
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
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(entityPictureUrl = null))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        val player = session.mediaSession?.player
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
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(entityPictureUrl = null, title = "Track 1"))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        stateFlow.tryEmit(createState(entityPictureUrl = null, title = "Track 2"))
        idleMainLooper()

        val player = session.mediaSession?.player
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
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(playbackState = MediaPlaybackState.Paused))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        session.mediaSession?.player?.play()
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
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(playbackState = MediaPlaybackState.Playing))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        session.mediaSession?.player?.pause()
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
     * Verifies that when `callAction` throws an exception, `callMediaAction` catches it and does
     * not propagate the crash, while still having attempted the call.
     *
     * This guards the `catch (e: Exception)` branch at the end of `callMediaAction`, which ensures
     * a transient network or server error never terminates the media session coroutine.
     */
    @Test
    fun `Given callAction throws when play requested then exception is caught and does not crash`() {
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(playbackState = MediaPlaybackState.Paused))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow
        coEvery {
            integrationRepository.callAction(any(), any(), any())
        } throws RuntimeException("Simulated server error")

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        session.mediaSession?.player?.play()
        shadowOf(Looper.getMainLooper()).idle()

        coVerify {
            integrationRepository.callAction(
                domain = MEDIA_PLAYER_DOMAIN,
                action = "media_play",
                actionData = any(),
            )
        }

        job.cancel()
    }

    // -- observe() lifecycle tests --

    /**
     * Verifies that [HaMediaSession.mediaSession] is set during observation and becomes null
     * after the observing job is cancelled, confirming Media3 resources are released.
     */
    @Test
    fun `Given observing session when job cancelled then mediaSession is null`() {
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(playbackState = MediaPlaybackState.Playing))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        val job = testScope.launch {
            session.observe { }
        }
        idleMainLooper()

        assertNotNull(session.mediaSession)

        job.cancel()
        idleMainLooper()

        assertNull(session.mediaSession)
    }

    /**
     * Verifies that [HaMediaSession.observe] calls [onSessionReady] with a non-null session
     * before starting state observation.
     */
    @Test
    fun `Given session when observe called then onSessionReady is invoked with the session`() {
        val stateFlow = MutableSharedFlow<MediaControlState?>()
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

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
