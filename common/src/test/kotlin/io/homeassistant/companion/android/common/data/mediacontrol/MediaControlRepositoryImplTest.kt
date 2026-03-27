package io.homeassistant.companion.android.common.data.mediacontrol

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.database.mediacontrol.MediaControlConfig
import io.homeassistant.companion.android.database.mediacontrol.MediaControlDao
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class MediaControlRepositoryImplTest {

    private val dao: MediaControlDao = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val webSocketRepository: WebSocketRepository = mockk(relaxed = true)

    private lateinit var repository: MediaControlRepositoryImpl

    private val testConfig = MediaControlEntityConfig(serverId = 1, entityId = "media_player.test")

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
        repository = MediaControlRepositoryImpl(
            dao = dao,
            serverManager = serverManager,
        )
    }

    @Nested
    inner class ObserveEntityStateTest {

        @Test
        fun `Given entity when state arrives then emit MediaControlState`() = runTest {
            val entityState = CompressedEntityState(
                state = JsonPrimitive("playing"),
                attributes = mapOf(
                    "friendly_name" to "Test Player",
                    "media_title" to "Test Song",
                    "media_artist" to "Test Artist",
                    "supported_features" to
                        (EntityExt.MEDIA_PLAYER_SUPPORT_PLAY or EntityExt.MEDIA_PLAYER_SUPPORT_PAUSE),
                ),
                lastChanged = 1000.0,
                lastUpdated = 1000.0,
            )
            val event = CompressedStateChangedEvent(
                added = mapOf("media_player.test" to entityState),
            )
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(listOf("media_player.test"))
            } returns flowOf(event)

            repository.observeEntityState(testConfig).test {
                val state = awaitItem()
                assertEquals("media_player.test", state?.entityId)
                assertEquals(1, state?.serverId)
                assertEquals(MediaPlaybackState.Playing, state?.playbackState)
                assertEquals("Test Song", state?.title)
                assertEquals("Test Artist", state?.artist)
                awaitComplete()
            }
        }

        @Test
        fun `Given entity when entity removed then emit null`() = runTest {
            val event = CompressedStateChangedEvent(
                removed = listOf("media_player.test"),
            )
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(listOf("media_player.test"))
            } returns flowOf(event)

            repository.observeEntityState(testConfig).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

        @Test
        fun `Given entity when websocket returns null then emit null`() = runTest {
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(listOf("media_player.test"))
            } returns null

            repository.observeEntityState(testConfig).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }
    }

    @Nested
    inner class ObserveMediaControlStatesTest {

        @Test
        fun `Given no configured entities when observing then emit empty list`() = runTest {
            coEvery { dao.getAllFlow() } returns flowOf(emptyList())

            repository.observeMediaControlStates().test {
                assertEquals(emptyList<MediaControlState>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given two configured entities when states arrive then emit combined list`() = runTest {
            coEvery { dao.getAllFlow() } returns flowOf(
                listOf(
                    MediaControlConfig(id = 1, serverId = 1, entityId = "media_player.living_room", position = 0),
                    MediaControlConfig(id = 2, serverId = 1, entityId = "media_player.bedroom", position = 1),
                ),
            )

            val state1 = CompressedEntityState(
                state = JsonPrimitive("playing"),
                attributes = mapOf("media_title" to "Song A"),
                lastChanged = 1000.0,
                lastUpdated = 1000.0,
            )
            val state2 = CompressedEntityState(
                state = JsonPrimitive("paused"),
                attributes = mapOf("media_title" to "Song B"),
                lastChanged = 1000.0,
                lastUpdated = 1000.0,
            )
            coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(listOf("media_player.living_room"))
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.living_room" to state1)))
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(listOf("media_player.bedroom"))
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.bedroom" to state2)))

            repository.observeMediaControlStates().test {
                val states = awaitItem()
                assertEquals(2, states.size)
                assertTrue(states.any { it.entityId == "media_player.living_room" && it.title == "Song A" })
                assertTrue(states.any { it.entityId == "media_player.bedroom" && it.title == "Song B" })
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class PlaybackStateMappingTest {

        private fun entityWithState(state: String, attributes: Map<String, Any?> = emptyMap()) = CompressedEntityState(
            state = JsonPrimitive(state),
            attributes = attributes,
            lastChanged = 1000.0,
            lastUpdated = 1000.0,
        )

        private fun configureWebSocketWith(state: String) {
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityWithState(state))))
        }

        @Test
        fun `Given paused state then maps to Paused`() = runTest {
            configureWebSocketWith("paused")

            repository.observeEntityState(testConfig).test {
                assertEquals(MediaPlaybackState.Paused, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given buffering state then maps to Buffering`() = runTest {
            configureWebSocketWith("buffering")

            repository.observeEntityState(testConfig).test {
                assertEquals(MediaPlaybackState.Buffering, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given idle state then maps to Idle`() = runTest {
            configureWebSocketWith("idle")

            repository.observeEntityState(testConfig).test {
                assertEquals(MediaPlaybackState.Idle, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given standby state then maps to Idle`() = runTest {
            configureWebSocketWith("standby")

            repository.observeEntityState(testConfig).test {
                assertEquals(MediaPlaybackState.Idle, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given off state then maps to Off`() = runTest {
            configureWebSocketWith("off")

            repository.observeEntityState(testConfig).test {
                assertEquals(MediaPlaybackState.Off, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given unknown state then maps to Off`() = runTest {
            configureWebSocketWith("unavailable")

            repository.observeEntityState(testConfig).test {
                assertEquals(MediaPlaybackState.Off, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given entity with partial attributes then null fields are null`() = runTest {
            val entityState = entityWithState("playing", attributes = mapOf("media_title" to "Only Title"))
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityState)))

            repository.observeEntityState(testConfig).test {
                val state = awaitItem()!!
                assertEquals("Only Title", state.title)
                assertNull(state.artist)
                assertNull(state.albumName)
                assertNull(state.entityPictureUrl)
                assertNull(state.mediaDurationSeconds)
                assertNull(state.mediaPositionSeconds)
                awaitComplete()
            }
        }

        @Test
        fun `Given entity with all attributes then all fields populated`() = runTest {
            val entityState = entityWithState(
                "playing",
                attributes = mapOf(
                    "media_title" to "Song",
                    "media_artist" to "Artist",
                    "media_album_name" to "Album",
                    "entity_picture" to "/api/picture",
                    "media_duration" to 300.0,
                    "media_position" to 120.5,
                    "supported_features" to (
                        EntityExt.MEDIA_PLAYER_SUPPORT_PAUSE or
                            EntityExt.MEDIA_PLAYER_SUPPORT_SEEK or
                            EntityExt.MEDIA_PLAYER_SUPPORT_PREVIOUS_TRACK or
                            EntityExt.MEDIA_PLAYER_SUPPORT_NEXT_TRACK or
                            EntityExt.MEDIA_PLAYER_SUPPORT_PLAY
                        ),
                ),
            )
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityState)))

            repository.observeEntityState(testConfig).test {
                val state = awaitItem()!!
                assertEquals("Song", state.title)
                assertEquals("Artist", state.artist)
                assertEquals("Album", state.albumName)
                assertEquals("/api/picture", state.entityPictureUrl)
                assertEquals(300.0, state.mediaDurationSeconds)
                assertEquals(120.5, state.mediaPositionSeconds)
                assertTrue(state.supportsPause)
                assertTrue(state.supportsPlay)
                assertTrue(state.supportsSeek)
                assertTrue(state.supportsPreviousTrack)
                assertTrue(state.supportsNextTrack)
                awaitComplete()
            }
        }
    }

    @Nested
    inner class VolumeMappingTest {

        private fun entityWithVolumeAttributes(attributes: Map<String, Any?>) = CompressedEntityState(
            state = JsonPrimitive("playing"),
            attributes = attributes,
            lastChanged = 1000.0,
            lastUpdated = 1000.0,
        )

        @Test
        fun `Given entity with volume support and volume_level then volumeLevel and supportsVolumeSet are set`() = runTest {
            val entityState = entityWithVolumeAttributes(
                mapOf(
                    "supported_features" to EntityExt.MEDIA_PLAYER_SUPPORT_VOLUME_SET,
                    "volume_level" to 0.7,
                ),
            )
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityState)))

            repository.observeEntityState(testConfig).test {
                val state = awaitItem()!!
                assertTrue(state.supportsVolumeSet)
                assertEquals(0.7f, state.volumeLevel)
                awaitComplete()
            }
        }

        @Test
        fun `Given entity without volume support then volumeLevel is null and supportsVolumeSet is false`() = runTest {
            val entityState = entityWithVolumeAttributes(
                mapOf("supported_features" to EntityExt.MEDIA_PLAYER_SUPPORT_PLAY),
            )
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityState)))

            repository.observeEntityState(testConfig).test {
                val state = awaitItem()!!
                assertFalse(state.supportsVolumeSet)
                assertNull(state.volumeLevel)
                awaitComplete()
            }
        }

        @Test
        fun `Given entity with is_volume_muted true then isVolumeMuted is true`() = runTest {
            val entityState = entityWithVolumeAttributes(
                mapOf(
                    "supported_features" to EntityExt.MEDIA_PLAYER_SUPPORT_VOLUME_SET,
                    "volume_level" to 0.5,
                    "is_volume_muted" to true,
                ),
            )
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityState)))

            repository.observeEntityState(testConfig).test {
                val state = awaitItem()!!
                assertTrue(state.isVolumeMuted)
                awaitComplete()
            }
        }

        @Test
        fun `Given entity with friendly_name then entityFriendlyName is set`() = runTest {
            val entityState = entityWithVolumeAttributes(
                mapOf("friendly_name" to "Living Room TV"),
            )
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityState)))

            repository.observeEntityState(testConfig).test {
                val state = awaitItem()!!
                assertEquals("Living Room TV", state.entityFriendlyName)
                awaitComplete()
            }
        }
    }

    @Nested
    inner class DistinctUntilChangedTest {

        @Test
        fun `Given duplicate state emissions then only first is emitted`() = runTest {
            val entityState = CompressedEntityState(
                state = JsonPrimitive("playing"),
                attributes = mapOf("media_title" to "Song"),
                lastChanged = 1000.0,
                lastUpdated = 1000.0,
            )
            val stateFlow = MutableSharedFlow<CompressedStateChangedEvent>()
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns stateFlow

            repository.observeEntityState(testConfig).test {
                // Emit the same entity state twice — distinctUntilChanged should filter the duplicate
                stateFlow.emit(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityState)))
                val first = awaitItem()
                assertEquals("Song", first?.title)

                stateFlow.emit(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityState)))
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class ConfigurationTest {

        @Test
        fun `Given entities in database when getConfiguredEntities then returns mapped list`() = runTest {
            coEvery { dao.getAll() } returns listOf(
                MediaControlConfig(id = 1, serverId = 1, entityId = "media_player.tv", position = 0),
            )

            assertEquals(
                listOf(MediaControlEntityConfig(serverId = 1, entityId = "media_player.tv")),
                repository.getConfiguredEntities(),
            )
        }

        @Test
        fun `Given entities when setConfiguredEntities then replaces all in database with positions`() = runTest {
            val entities = listOf(
                MediaControlEntityConfig(serverId = 1, entityId = "media_player.tv"),
                MediaControlEntityConfig(serverId = 2, entityId = "media_player.office"),
            )

            repository.setConfiguredEntities(entities)

            coVerify {
                dao.replaceAll(
                    listOf(
                        MediaControlConfig(serverId = 1, entityId = "media_player.tv", position = 0),
                        MediaControlConfig(serverId = 2, entityId = "media_player.office", position = 1),
                    ),
                )
            }
        }
    }
}
