package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import android.os.Looper
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaPlaybackState
import io.homeassistant.companion.android.common.data.mediacontrol.MediaRepeatMode
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Module-level counter for unique MediaSession IDs across tests within the same JVM process. */
private val sessionCounter = AtomicInteger(0)

/**
 * Tests for [HaMediaSessionService] session reconciliation and lifecycle behavior.
 *
 * Session management is driven through [MediaControlRepository.observeConfiguredEntities] flow
 * emissions via [HaMediaSessionService.startObservingEntities], rather than calling
 * [HaMediaSessionService.reconcileSessions] directly. This exercises the full path from
 * a DB change to session creation or removal.
 *
 * Each test pre-populates [configuredEntitiesFlow] (replay=1) before starting observation, so
 * the subscriber receives the value immediately upon subscribing — matching the pattern used in
 * [HaMediaSessionTest] where mock flows are set up before collecting. Subsequent emissions are
 * delivered to the active subscriber.
 *
 * [HaMediaSession] instances are created with [UnconfinedTestDispatcher] scopes so that
 * [HaMediaSession.reconnect] and [HaMediaSession.startObservingState] run eagerly. Main-looper
 * tasks (such as [HaRemoteMediaPlayer.updateState] dispatched by [HaMediaSession]) are flushed
 * with [idleMainLooper].
 *
 * Injected dependencies bypass Hilt by directly setting the service's lateinit fields.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HaMediaSessionServiceTest {

    @get:Rule
    val consoleLogRule = ConsoleLogRule()

    private val mediaControlRepository: MediaControlRepository = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val haMediaSessionFactory: HaMediaSession.Factory = mockk()

    // replay=1 ensures tryEmit always succeeds and the value is available to new subscribers.
    private lateinit var configuredEntitiesFlow: MutableSharedFlow<List<MediaControlEntityConfig>>
    private lateinit var observationScope: CoroutineScope
    private lateinit var service: HaMediaSessionService

    @Before
    fun setUp() {
        configuredEntitiesFlow = MutableSharedFlow(replay = 1)
        observationScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

        every { mediaControlRepository.observeConfiguredEntities() } returns configuredEntitiesFlow
        coEvery { mediaControlRepository.observeEntityState(any()) } returns flowOf(null)

        // Each session gets its own UnconfinedTestDispatcher scope so that reconnect() and
        // startObservingState() run eagerly on the calling thread without Thread.sleep.
        every { haMediaSessionFactory.create(any<Context>(), any(), any()) } answers {
            HaMediaSession(
                context = firstArg(),
                config = secondArg(),
                scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
                mediaControlRepository = mediaControlRepository,
                serverManager = serverManager,
            )
        }

        service = Robolectric.buildService(HaMediaSessionService::class.java).get()
        service.mediaControlRepository = mediaControlRepository
        service.haMediaSessionFactory = haMediaSessionFactory
    }

    @After
    fun tearDown() {
        observationScope.cancel()
        // Safe when onDestroy() was already called — activeSessions will already be empty.
        service.activeSessions.values.forEach { it.release() }
        unmockkAll()
    }

    /**
     * Starts [HaMediaSessionService.startObservingEntities] with the test scope. Because
     * [configuredEntitiesFlow] uses replay=1 and [observationScope] uses [UnconfinedTestDispatcher],
     * the subscriber receives any pre-emitted value immediately and runs the [onEach] block
     * synchronously until it hits [withContext(Dispatchers.Main)]. Call [idleMainLooper] after
     * this to flush the pending [HaMediaSessionService.reconcileSessions] task.
     */
    private fun startObserving() {
        service.startObservingEntities(observationScope)
    }

    /**
     * Drains the Robolectric main looper so that tasks posted via [withContext(Dispatchers.Main)]
     * (e.g. [HaMediaSessionService.reconcileSessions] from the flow's [onEach] block, or
     * [HaRemoteMediaPlayer.updateState] from [HaMediaSession.startObservingState]) take effect
     * before assertions.
     *
     * Robolectric's [shadowOf(Looper.getMainLooper()).idle()] processes nested posts too, so a
     * single call is sufficient even when multiple tasks are queued in sequence.
     */
    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun uniqueConfig(): MediaControlEntityConfig {
        val id = sessionCounter.incrementAndGet()
        return MediaControlEntityConfig(serverId = 1, entityId = "media_player.test_$id")
    }

    private fun createPlayingState(entityId: String) = MediaControlState(
        entityId = entityId,
        serverId = 1,
        playbackState = MediaPlaybackState.Playing,
        title = "Test Track",
        artist = null,
        albumName = null,
        entityPictureUrl = null,
        mediaDuration = null,
        mediaPosition = null,
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

    // -- Reconciliation via flow emissions --

    @Test
    fun `Given new entity in config when flow emits then session is added`() {
        val config = uniqueConfig()
        configuredEntitiesFlow.tryEmit(listOf(config))
        startObserving()
        idleMainLooper()

        assertEquals(1, service.activeSessions.size)
        assertTrue(service.activeSessions.containsKey("1:${config.entityId}"))
    }

    @Test
    fun `Given two entities in config when flow emits then sessions are added for each`() {
        val configA = uniqueConfig()
        val configB = uniqueConfig()
        configuredEntitiesFlow.tryEmit(listOf(configA, configB))
        startObserving()
        idleMainLooper()

        assertEquals(2, service.activeSessions.size)
        assertTrue(service.activeSessions.containsKey("1:${configA.entityId}"))
        assertTrue(service.activeSessions.containsKey("1:${configB.entityId}"))
    }

    @Test
    fun `Given active session when entity removed from config then session is removed`() {
        val configA = uniqueConfig()
        val configB = uniqueConfig()
        configuredEntitiesFlow.tryEmit(listOf(configA, configB))
        startObserving()
        idleMainLooper()

        configuredEntitiesFlow.tryEmit(listOf(configB))
        idleMainLooper()

        assertEquals(1, service.activeSessions.size)
        assertTrue(service.activeSessions.containsKey("1:${configB.entityId}"))
    }

    @Test
    fun `Given existing session when entity remains in config then session is not recreated`() {
        val config = uniqueConfig()
        configuredEntitiesFlow.tryEmit(listOf(config))
        startObserving()
        idleMainLooper()
        val sessionBefore = service.activeSessions["1:${config.entityId}"]

        configuredEntitiesFlow.tryEmit(listOf(config))
        idleMainLooper()

        assertEquals(1, service.activeSessions.size)
        assertSame(sessionBefore, service.activeSessions["1:${config.entityId}"])
    }

    @Test
    fun `Given empty config when flow emits then service stops itself`() {
        configuredEntitiesFlow.tryEmit(emptyList())
        startObserving()
        idleMainLooper()

        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
    }

    // -- onStartCommand --

    /**
     * Verifies that [HaMediaSessionService.onStartCommand] reconnects active sessions by
     * restarting their observation. This is the recovery path for sessions whose WebSocket
     * subscription got stuck after a network disconnect.
     *
     * With [UnconfinedTestDispatcher] session scopes, [HaMediaSession.reconnect] runs eagerly
     * and [MediaControlRepository.observeEntityState] is called synchronously — no sleep needed.
     */
    @Test
    fun `Given active sessions when onStartCommand then sessions are reconnected`() {
        var observeCallCount = 0
        coEvery { mediaControlRepository.observeEntityState(any()) } answers {
            observeCallCount++
            MutableSharedFlow()
        }
        val config = uniqueConfig()
        configuredEntitiesFlow.tryEmit(listOf(config))
        startObserving()
        idleMainLooper()
        val countAfterSetup = observeCallCount

        service.onStartCommand(intent = null, flags = 0, startId = 0)

        assertTrue(observeCallCount > countAfterSetup)
    }

    // -- onTaskRemoved --

    @Test
    fun `Given no active sessions when onTaskRemoved then service stops`() {
        service.onTaskRemoved(rootIntent = null)

        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `Given active session not playing when onTaskRemoved then service stops`() {
        val config = uniqueConfig()
        // Session starts in idle state: playWhenReady=false, mediaItemCount=0
        configuredEntitiesFlow.tryEmit(listOf(config))
        startObserving()
        idleMainLooper()

        service.onTaskRemoved(rootIntent = null)

        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `Given active session playing when onTaskRemoved then service does not stop`() {
        val config = uniqueConfig()
        // Pre-load a Playing state with replay=1 so the session's startObservingState() receives
        // it immediately when it subscribes, before idleMainLooper flushes player.updateState().
        val stateFlow = MutableSharedFlow<MediaControlState?>(replay = 1)
        stateFlow.tryEmit(createPlayingState(config.entityId))
        coEvery { mediaControlRepository.observeEntityState(any()) } returns stateFlow

        configuredEntitiesFlow.tryEmit(listOf(config))
        startObserving()
        // idleMainLooper processes both reconcileSessions (from the entities flow) and
        // player.updateState (posted back to Main by loadArtworkAndUpdatePlayer inside
        // startObservingState). Robolectric's idle() drains all queued and nested tasks.
        idleMainLooper()

        service.onTaskRemoved(rootIntent = null)

        assertFalse(Shadows.shadowOf(service).isStoppedBySelf)
    }

    // -- onDestroy --

    @Test
    fun `Given active sessions when onDestroy then all sessions are released and map is cleared`() {
        val config = uniqueConfig()
        configuredEntitiesFlow.tryEmit(listOf(config))
        startObserving()
        idleMainLooper()
        assertEquals(1, service.activeSessions.size)

        service.onDestroy()

        assertTrue(service.activeSessions.isEmpty())
    }
}
