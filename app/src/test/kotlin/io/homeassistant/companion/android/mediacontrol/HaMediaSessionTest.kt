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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
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

    @Before
    fun setUp() {
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        mediaControlRepository = mockk()
        serverManager = mockk()
        integrationRepository = mockk(relaxed = true)

        val uniqueEntityId = "media_player.test_${sessionCounter.incrementAndGet()}"
        config = MediaControlEntityConfig(serverId = SERVER_ID, entityId = uniqueEntityId)

        coEvery { mediaControlRepository.getEntityState(config) } returns null
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
        scope = testScope,
        mediaControlRepository = mediaControlRepository,
        serverManager = serverManager,
    )

    /**
     * Waits for `Dispatchers.Default` coroutines (launched via `testScope`) to settle,
     * then drains the Robolectric main looper so that `player.updateState` calls take effect.
     *
     * `testScope` uses `Dispatchers.Default`, which is not controlled by `runTest`'s fake
     * scheduler. A short real-time wait is required before draining the main looper.
     */
    private fun drainDefaultDispatcherAndMainLooper() {
        Thread.sleep(REAL_DISPATCHER_SETTLE_MS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    // -- State observation tests --

    /**
     * Verifies the cold-start recovery path: when `getEntityState` returns state on the first
     * attempt and `observeEntityState` emits null (subscription not ready) but stays open,
     * the player retains the REST-fetched state and does not drop to idle.
     *
     * Uses a `MutableSharedFlow` with `replay=1` so the null emission is received by the
     * Default dispatcher collector without racing, and the flow stays open so the observation
     * loop does not call `setConnecting()` and overwrite the state.
     */
    @Test
    fun `Given getEntityState returns state and observeEntityState emits null when startObservingState then player retains state from REST`() {
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(null)
        coEvery { mediaControlRepository.getEntityState(config) } returns createState(
            playbackState = MediaPlaybackState.Playing,
        )
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        testScope.launch { session.startObservingState() }
        drainDefaultDispatcherAndMainLooper()

        val player = session.mediaSession.player
        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(true, player.playWhenReady)

        session.release()
    }

    /**
     * Verifies that when `observeEntityState` emits a playing state, the player transitions
     * to STATE_READY with `playWhenReady = true`.
     *
     * Uses `replay=1` so the emission is cached and replayed to the collector on
     * `Dispatchers.Default` regardless of when it subscribes. The flow stays open so the
     * observation loop does not immediately call `setConnecting()` and overwrite the READY state.
     */
    @Test
    fun `Given observeEntityState emits playing state when startObservingState then player is ready and playing`() {
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(playbackState = MediaPlaybackState.Playing))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        testScope.launch { session.startObservingState() }
        drainDefaultDispatcherAndMainLooper()

        val player = session.mediaSession.player
        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(true, player.playWhenReady)

        session.release()
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
        testScope.launch { session.startObservingState() }
        drainDefaultDispatcherAndMainLooper()

        val player = session.mediaSession.player
        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(false, player.playWhenReady)

        session.release()
    }

    /**
     * Verifies that when `observeEntityState` flow completes (entity disconnects), the player
     * enters STATE_BUFFERING (the "connecting" state) and exposes an empty command set so
     * that the notification remains visible but controls are disabled.
     *
     * Both flows complete immediately so the observation loop calls `setConnecting()` right after
     * the first iteration without needing the retry delay. Two settle cycles are used: the first
     * allows the playing-state update to reach the Main looper, and the second allows the
     * `setConnecting()` call (which follows on the Default dispatcher) to reach the Main looper.
     */
    @Test
    fun `Given observeEntityState flow completes when startObservingState then player enters connecting state`() {
        coEvery { mediaControlRepository.observeEntityState(config) } returns flowOf(
            createState(playbackState = MediaPlaybackState.Playing),
        ) andThen emptyFlow()

        val session = buildSession()
        testScope.launch { session.startObservingState() }
        drainDefaultDispatcherAndMainLooper()
        drainDefaultDispatcherAndMainLooper()

        val player = session.mediaSession.player
        assertEquals(Player.STATE_BUFFERING, player.playbackState)
        assertEquals(Player.Commands.EMPTY, player.availableCommands)

        session.release()
    }

    // -- Artwork caching tests --

    /**
     * Verifies that when the emitted state has a null artwork URL, the player's media metadata
     * contains no artwork bytes.
     *
     * Uses `replay=1` so the emission is available when the Default dispatcher starts collecting.
     */
    @Test
    fun `Given state with null artwork URL when startObservingState then player artwork is null`() {
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(entityPictureUrl = null))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        testScope.launch { session.startObservingState() }
        drainDefaultDispatcherAndMainLooper()

        val player = session.mediaSession.player
        assertNull(player.mediaMetadata.artworkData)

        session.release()
    }

    /**
     * Verifies that when a second state emission has a null artwork URL, old artwork is not
     * retained — the player's metadata artwork is null and the second state's title is applied.
     *
     * This exercises the branch in `loadArtworkAndUpdatePlayer` where the URL is null:
     * `currentArtworkUrl` is cleared and `currentArtworkBytes` is passed through (which is
     * null here because no real image was ever loaded in this test).
     *
     * Uses `replay=1` for reliable delivery to the late-starting Default dispatcher collector.
     * The second emission is made after the first is confirmed to be processed.
     */
    @Test
    fun `Given two consecutive states where second has null artwork URL when startObservingState then artwork is null`() {
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(entityPictureUrl = null, title = "Track 1"))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        testScope.launch { session.startObservingState() }
        drainDefaultDispatcherAndMainLooper()

        stateFlow.tryEmit(createState(entityPictureUrl = null, title = "Track 2"))
        drainDefaultDispatcherAndMainLooper()

        val player = session.mediaSession.player
        assertNull(player.mediaMetadata.artworkData)
        assertEquals("Track 2", player.mediaMetadata.title?.toString())

        session.release()
    }

    // -- callMediaAction tests --

    /**
     * Verifies that triggering play on the media session player causes `callMediaAction` to
     * dispatch a `media_play` action to the integration repository for the configured entity.
     *
     * Uses `replay=1` so the paused state is reliably received by the Default dispatcher
     * collector before `player.play()` is invoked. `callMediaAction` also runs on the Default
     * dispatcher, so a real-time wait is required after triggering the player command.
     */
    @Test
    fun `Given paused player when play requested then media_play action is called`() {
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(playbackState = MediaPlaybackState.Paused))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        testScope.launch { session.startObservingState() }
        drainDefaultDispatcherAndMainLooper()

        session.mediaSession.player.play()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(REAL_DISPATCHER_SETTLE_MS)

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

        session.release()
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
        testScope.launch { session.startObservingState() }
        drainDefaultDispatcherAndMainLooper()

        session.mediaSession.player.pause()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(REAL_DISPATCHER_SETTLE_MS)

        val capturedAction = slot<String>()
        coVerify {
            integrationRepository.callAction(
                domain = any(),
                action = capture(capturedAction),
                actionData = any(),
            )
        }
        assertEquals("media_pause", capturedAction.captured)

        session.release()
    }

    /**
     * Verifies that calling `reconnect()` while an observation is already running cancels the
     * previous observation job and starts a fresh one, re-calling `observeEntityState`.
     *
     * This is the recovery path for a stuck WebSocket subscription (flow never completes after
     * network disconnect). The test simulates the stuck case with a `MutableSharedFlow` that
     * never completes, then verifies that `reconnect()` triggers a second subscription call.
     */
    @Test
    fun `Given running observation when reconnect called then observation is restarted`() {
        var observeCallCount = 0
        coEvery { mediaControlRepository.observeEntityState(config) } answers {
            observeCallCount++
            MutableSharedFlow()
        }

        val session = buildSession()
        testScope.launch { session.startObservingState() }
        drainDefaultDispatcherAndMainLooper()
        assertEquals(1, observeCallCount)

        session.reconnect()
        drainDefaultDispatcherAndMainLooper()
        assertEquals(2, observeCallCount)

        session.release()
    }

    /**
     * Verifies that calling `release()` cancels the coroutine scope, preventing
     * any further observation or action dispatch.
     */
    @Test
    fun `Given observing session when release called then internal scope is cancelled`() {
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createState(playbackState = MediaPlaybackState.Playing))
        coEvery { mediaControlRepository.observeEntityState(config) } returns stateFlow

        val session = buildSession()
        testScope.launch { session.startObservingState() }
        drainDefaultDispatcherAndMainLooper()

        val scopeField = HaMediaSession::class.java.getDeclaredField("scope")
        scopeField.isAccessible = true
        val scope = scopeField.get(session) as CoroutineScope

        session.release()

        org.junit.Assert.assertFalse(scope.isActive)
    }

    private companion object {
        /**
         * Milliseconds to allow `HaMediaSession`'s `Dispatchers.Default` coroutines to
         * complete before inspecting state or verifying mock calls.
         *
         * `HaMediaSession` uses a real `Dispatchers.Default` scope that is not controlled by
         * the test. 1 second is used to provide a stable budget on slower CI machines.
         */
        const val REAL_DISPATCHER_SETTLE_MS = 1000L
    }
}
