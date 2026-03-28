package io.homeassistant.companion.android.mediacontrol

import android.content.Context
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.testing.unit.ConsoleLogRule
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Tests for [HaMediaSessionService] session reconciliation logic.
 *
 * Dependencies are injected directly into the service's lateinit fields, bypassing Hilt.
 * Reconciliation results are asserted via [HaMediaSessionService.activeSessions], which is
 * the service's source of truth for which sessions are currently active.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = dagger.hilt.android.testing.HiltTestApplication::class)
class HaMediaSessionServiceTest {

    @get:Rule(order = 0)
    val consoleLogRule = ConsoleLogRule()

    @get:Rule(order = 1)
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    private val mediaControlRepository: MediaControlRepository = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val haMediaSessionFactory: HaMediaSession.Factory = mockk()

    private lateinit var service: HaMediaSessionService

    @Before
    fun setUp() {
        coEvery { mediaControlRepository.observeEntityState(any()) } returns flowOf(null)
        coEvery { mediaControlRepository.getEntityState(any()) } returns null
        every { haMediaSessionFactory.create(any<Context>(), any()) } answers {
            HaMediaSession(
                context = firstArg(),
                config = secondArg(),
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
        service.activeSessions.values.forEach { it.release() }
        unmockkAll()
    }

    @Test
    fun `Given new entity in config when reconcileSessions then session is added`() {
        val config = MediaControlEntityConfig(serverId = 1, entityId = "media_player.living_room")

        service.reconcileSessions(listOf(config))

        assertEquals(1, service.activeSessions.size)
        assertTrue(service.activeSessions.containsKey("1:media_player.living_room"))
    }

    @Test
    fun `Given two entities in config when reconcileSessions then sessions added for each`() {
        val configA = MediaControlEntityConfig(serverId = 1, entityId = "media_player.living_room")
        val configB = MediaControlEntityConfig(serverId = 1, entityId = "media_player.bedroom")

        service.reconcileSessions(listOf(configA, configB))

        assertEquals(2, service.activeSessions.size)
        assertTrue(service.activeSessions.containsKey("1:media_player.living_room"))
        assertTrue(service.activeSessions.containsKey("1:media_player.bedroom"))
    }

    @Test
    fun `Given active session when entity removed from config then session is removed`() {
        val configA = MediaControlEntityConfig(serverId = 1, entityId = "media_player.living_room")
        val configB = MediaControlEntityConfig(serverId = 1, entityId = "media_player.bedroom")
        service.reconcileSessions(listOf(configA, configB))

        service.reconcileSessions(listOf(configB))

        assertEquals(1, service.activeSessions.size)
        assertTrue(service.activeSessions.containsKey("1:media_player.bedroom"))
    }

    @Test
    fun `Given existing session when entity remains in config then session is not recreated`() {
        val config = MediaControlEntityConfig(serverId = 1, entityId = "media_player.living_room")
        service.reconcileSessions(listOf(config))

        val sessionBefore = service.activeSessions["1:media_player.living_room"]

        service.reconcileSessions(listOf(config))

        assertEquals(1, service.activeSessions.size)
        assertTrue(service.activeSessions["1:media_player.living_room"] === sessionBefore)
    }

    @Test
    fun `Given empty config when reconcileSessions then service stops itself`() {
        service.reconcileSessions(emptyList())

        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
    }
}
