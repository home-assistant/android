package io.homeassistant.companion.android.common.data.mediacontrol

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class)
class MediaControlRepositoryImplTest {

    private val prefsRepository: PrefsRepository = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val webSocketRepository: WebSocketRepository = mockk(relaxed = true)

    private lateinit var repository: MediaControlRepositoryImpl

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.webSocketRepository(any()) } returns webSocketRepository
        repository = MediaControlRepositoryImpl(
            prefsRepository = prefsRepository,
            serverManager = serverManager,
        )
    }

    @Nested
    inner class ObserveMediaControlStateTest {

        @Test
        fun `Given no configured entity when observing then emit null`() = runTest {
            coEvery { prefsRepository.getMediaControlServerId() } returns null
            coEvery { prefsRepository.getMediaControlEntityId() } returns null

            repository.observeMediaControlState().test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

        @Test
        fun `Given configured entity when state arrives then emit MediaControlState`() = runTest {
            coEvery { prefsRepository.getMediaControlServerId() } returns 1
            coEvery { prefsRepository.getMediaControlEntityId() } returns "media_player.test"

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

            repository.observeMediaControlState().test {
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
        fun `Given configured entity when entity removed then emit null`() = runTest {
            coEvery { prefsRepository.getMediaControlServerId() } returns 1
            coEvery { prefsRepository.getMediaControlEntityId() } returns "media_player.test"

            val event = CompressedStateChangedEvent(
                removed = listOf("media_player.test"),
            )
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(listOf("media_player.test"))
            } returns flowOf(event)

            repository.observeMediaControlState().test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

        @Test
        fun `Given configured entity when websocket returns null then emit null`() = runTest {
            coEvery { prefsRepository.getMediaControlServerId() } returns 1
            coEvery { prefsRepository.getMediaControlEntityId() } returns "media_player.test"
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(listOf("media_player.test"))
            } returns null

            repository.observeMediaControlState().test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }
    }

    @Nested
    inner class PlaybackStateMappingTest {

        private fun configureEntity() {
            coEvery { prefsRepository.getMediaControlServerId() } returns 1
            coEvery { prefsRepository.getMediaControlEntityId() } returns "media_player.test"
        }

        private fun entityWithState(state: String, attributes: Map<String, Any?> = emptyMap()) = CompressedEntityState(
            state = JsonPrimitive(state),
            attributes = attributes,
            lastChanged = 1000.0,
            lastUpdated = 1000.0,
        )

        @Test
        fun `Given paused state then maps to Paused`() = runTest {
            configureEntity()
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityWithState("paused"))))

            repository.observeMediaControlState().test {
                assertEquals(MediaPlaybackState.Paused, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given buffering state then maps to Buffering`() = runTest {
            configureEntity()
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityWithState("buffering"))))

            repository.observeMediaControlState().test {
                assertEquals(MediaPlaybackState.Buffering, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given idle state then maps to Idle`() = runTest {
            configureEntity()
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityWithState("idle"))))

            repository.observeMediaControlState().test {
                assertEquals(MediaPlaybackState.Idle, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given standby state then maps to Idle`() = runTest {
            configureEntity()
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityWithState("standby"))))

            repository.observeMediaControlState().test {
                assertEquals(MediaPlaybackState.Idle, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given off state then maps to Off`() = runTest {
            configureEntity()
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityWithState("off"))))

            repository.observeMediaControlState().test {
                assertEquals(MediaPlaybackState.Off, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given unknown state then maps to Off`() = runTest {
            configureEntity()
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityWithState("unavailable"))))

            repository.observeMediaControlState().test {
                assertEquals(MediaPlaybackState.Off, awaitItem()?.playbackState)
                awaitComplete()
            }
        }

        @Test
        fun `Given entity with partial attributes then null fields are null`() = runTest {
            configureEntity()
            val entityState = entityWithState("playing", attributes = mapOf("media_title" to "Only Title"))
            coEvery {
                webSocketRepository.getCompressedStateAndChanges(any())
            } returns flowOf(CompressedStateChangedEvent(added = mapOf("media_player.test" to entityState)))

            repository.observeMediaControlState().test {
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
            configureEntity()
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

            repository.observeMediaControlState().test {
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
    inner class DistinctUntilChangedTest {

        @Test
        fun `Given duplicate state emissions then only first is emitted`() = runTest {
            coEvery { prefsRepository.getMediaControlServerId() } returns 1
            coEvery { prefsRepository.getMediaControlEntityId() } returns "media_player.test"

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

            repository.observeMediaControlState().test {
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
        fun `Given server id when getConfiguredServerId then delegates to prefs`() = runTest {
            coEvery { prefsRepository.getMediaControlServerId() } returns 42

            assertEquals(42, repository.getConfiguredServerId())
        }

        @Test
        fun `Given entity id when getConfiguredEntityId then delegates to prefs`() = runTest {
            coEvery { prefsRepository.getMediaControlEntityId() } returns "media_player.kitchen"

            assertEquals("media_player.kitchen", repository.getConfiguredEntityId())
        }

        @Test
        fun `Given values when setConfiguredEntity then updates both prefs`() = runTest {
            repository.setConfiguredEntity(serverId = 5, entityId = "media_player.office")

            coVerify { prefsRepository.setMediaControlServerId(5) }
            coVerify { prefsRepository.setMediaControlEntityId("media_player.office") }
        }

        @Test
        fun `Given null values when setConfiguredEntity then clears both prefs`() = runTest {
            repository.setConfiguredEntity(serverId = null, entityId = null)

            coVerify { prefsRepository.setMediaControlServerId(null) }
            coVerify { prefsRepository.setMediaControlEntityId(null) }
        }
    }
}
