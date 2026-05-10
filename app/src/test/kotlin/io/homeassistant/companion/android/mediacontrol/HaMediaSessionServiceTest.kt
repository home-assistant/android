package io.homeassistant.companion.android.mediacontrol

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
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
 * emissions, rather than through [onCreate], which is intentionally not called in tests: [onCreate]
 * triggers Hilt's component injection, which requires a fully-initialized Hilt application
 * component that is not available in this test setup. Instead, all constructor-injected dependencies
 * ([MediaControlRepository], [HaMediaSession.Factory], and [serviceScope]) are replaced via
 * reflection after construction, and the private [startObservingEntities] method is invoked via
 * reflection through [startObserving].
 *
 * The service is created via [Robolectric.buildService] (using [get] rather than [create]) so that
 * the service is properly attached to an Android context without triggering [onCreate]. The three
 * constructor val fields are then replaced via reflection with test doubles: [mediaControlRepository]
 * and [haMediaSessionFactory] receive MockK mocks, and [serviceScope] receives [observationScope]
 * (backed by [UnconfinedTestDispatcher]) so that flow collection and session coroutines run eagerly
 * and synchronously on the test dispatcher.
 *
 * Each test pre-populates [configuredEntitiesFlow] (replay=1) before starting observation, so
 * the subscriber receives the value immediately upon subscribing. Subsequent emissions are
 * delivered to the active subscriber.
 *
 * Main-looper tasks (such as [HaRemoteMediaPlayer.updateState] dispatched by [HaMediaSession])
 * are flushed with [idleMainLooper].
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

    // A non-completing SharedFlow: observeEntityState() suspends indefinitely by default so that
    // HaMediaSession.observe() doesn't exit normally, keeping the session alive in getSessions().
    // MediaSession.release() auto-removes the session from getSessions(), so we need it alive.
    private val entityStateFlow = MutableSharedFlow<MediaControlState?>(replay = 0)

    private lateinit var observationScope: CoroutineScope
    private lateinit var service: HaMediaSessionService

    @Before
    fun setUp() {
        configuredEntitiesFlow = MutableSharedFlow(replay = 1)
        observationScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

        every { mediaControlRepository.observeConfiguredEntities() } returns configuredEntitiesFlow
        coEvery { mediaControlRepository.observeEntityState(any()) } returns entityStateFlow

        // Each session is created without a scope — HaMediaSession.observe() derives its scope
        // from the coroutine that calls it (observationScope with UnconfinedTestDispatcher).
        every { haMediaSessionFactory.create(any()) } answers {
            HaMediaSession(
                context = ApplicationProvider.getApplicationContext(),
                config = firstArg(),
                mediaControlRepository = mediaControlRepository,
                serverManager = serverManager,
            )
        }

        service = Robolectric.buildService(HaMediaSessionService::class.java).get()

        // mediaControlRepository, haMediaSessionFactory, and serviceScope are constructor val
        // parameters (private final fields). Reflection is required to inject test doubles because
        // the service is built without calling onCreate() (which would trigger Hilt injection) and
        // the fields are immutable from Kotlin's perspective.
        fun setField(name: String, value: Any) {
            val field = HaMediaSessionService::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(service, value)
        }
        setField("mediaControlRepository", mediaControlRepository)
        setField("haMediaSessionFactory", haMediaSessionFactory)
        setField("serviceScope", observationScope)
    }

    @After
    fun tearDown() {
        // Cancelling observationScope cancels all session observation coroutines, which triggers
        // each HaMediaSession.observe() finally block → session.release() → auto-removed from
        // getSessions(). onDestroy() is not called here to avoid double-calling it in tests that
        // explicitly invoke it (e.g. the onDestroy lifecycle test).
        observationScope.cancel()
        // Drain the main looper so that the withContext(NonCancellable + Dispatchers.Main) calls
        // in the observe() finally blocks complete and session.release() runs before the next test
        // class starts. Without this, MediaSession IDs linger in Media3's global registry and
        // cause "Session ID must be unique" failures in subsequent test classes.
        idleMainLooper()
    }

    /**
     * Starts entity observation on the service using the test-controlled [observationScope]
     * (already set via reflection in [setUp]) as the service scope. Because [configuredEntitiesFlow]
     * uses replay=1 and [observationScope] uses [UnconfinedTestDispatcher], the subscriber receives
     * any pre-emitted value immediately and reconciliation runs synchronously.
     * Call [idleMainLooper] after this to flush any Main-thread tasks posted by [HaMediaSession]
     * (e.g. [HaRemoteMediaPlayer.updateState]).
     *
     * Invoked via reflection because [HaMediaSessionService.startObservingEntities] is private —
     * this avoids calling [onCreate], which triggers Hilt field injection unavailable in this setup.
     */
    private fun startObserving() {
        val m = HaMediaSessionService::class.java.getDeclaredMethod("startObservingEntities")
        m.isAccessible = true
        m.invoke(service)
    }

    /**
     * Drains the Robolectric main looper so that tasks posted via [withContext(Dispatchers.Main)]
     * from within [HaMediaSession] (e.g. [HaRemoteMediaPlayer.updateState] dispatched by
     * [HaMediaSession.startObservingState]) take effect before assertions.
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

        assertEquals(1, service.getSessions().size)
        assertTrue(service.getSessions().any { it.id == "1:${config.entityId}" })
    }

    @Test
    fun `Given two entities in config when flow emits then sessions are added for each`() {
        val configA = uniqueConfig()
        val configB = uniqueConfig()
        configuredEntitiesFlow.tryEmit(listOf(configA, configB))
        startObserving()
        idleMainLooper()

        assertEquals(2, service.getSessions().size)
        assertTrue(service.getSessions().any { it.id == "1:${configA.entityId}" })
        assertTrue(service.getSessions().any { it.id == "1:${configB.entityId}" })
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

        assertEquals(1, service.getSessions().size)
        assertTrue(service.getSessions().any { it.id == "1:${configB.entityId}" })
    }

    @Test
    fun `Given existing session when entity remains in config then session is not recreated`() {
        val config = uniqueConfig()
        configuredEntitiesFlow.tryEmit(listOf(config))
        startObserving()
        idleMainLooper()
        val sessionBefore = service.getSessions().first()

        configuredEntitiesFlow.tryEmit(listOf(config))
        idleMainLooper()

        assertEquals(1, service.getSessions().size)
        assertSame(sessionBefore, service.getSessions().first())
    }

    @Test
    fun `Given empty config when flow emits then service stops itself`() {
        configuredEntitiesFlow.tryEmit(emptyList())
        startObserving()
        idleMainLooper()

        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
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
        assertEquals(1, service.getSessions().size)

        // onDestroy() calls removeSession() explicitly for each active session before cancelling
        // the observation jobs, so getSessions() is empty immediately after the call.
        service.onDestroy()
        idleMainLooper()

        assertTrue(service.getSessions().isEmpty())
    }
}
