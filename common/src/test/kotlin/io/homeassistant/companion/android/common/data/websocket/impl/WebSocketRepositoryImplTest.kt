package io.homeassistant.companion.android.common.data.websocket.impl

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketCore
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MessageSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.PanelResponse
import io.homeassistant.companion.android.common.util.VOICE_SAMPLE_RATE
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.common.util.toJsonObjectOrNull
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WebSocketRepositoryImplTest {

    private lateinit var webSocketCore: WebSocketCore
    private lateinit var serverManager: ServerManager
    private lateinit var repository: WebSocketRepositoryImpl

    @BeforeEach
    fun setUp() {
        webSocketCore = mockk(relaxed = true)
        serverManager = mockk(relaxed = true)
        repository = WebSocketRepositoryImpl(webSocketCore, serverManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    inner class CurrentUser {

        /** Mock a user response JSON as returned by the websocket. */
        private fun mockUserResponse(userId: String = "1") {
            val response = """
                {
                  "id": "$userId",
                  "name": "Anna",
                  "is_owner": true,
                  "is_admin": true,
                  "credentials": [
                    {
                      "auth_provider_type": "homeassistant",
                      "auth_provider_id": null
                    }
                  ],
                  "mfa_modules": [
                    {
                      "id": "totp",
                      "name": "Authenticator app",
                      "enabled": true
                    }
                  ]
                }
            """.trimIndent().toJsonObjectOrNull()
            coEvery { webSocketCore.sendMessage(mapOf("type" to "auth/current_user")) } returns MessageSocketResponse(
                id = 1,
                success = true,
                result = response,
            )
        }

        /** Create a user info object for the server matching the data in [mockUserResponse]. */
        private fun userInfo(userId: String = "1", picturePath: String? = null): ServerUserInfo = ServerUserInfo(id = userId, name = "Anna", isOwner = true, isAdmin = true, picturePath = picturePath)

        @Test
        fun `Given user info when getting current user then info is persisted`() = runTest {
            mockUserResponse()
            val server = createServer()
            coEvery { webSocketCore.server() } returns server
            val serverSlot = slot<Server>()
            coEvery { serverManager.updateServer(capture(serverSlot)) } just Runs

            repository.getCurrentUser()
            assertEquals(userInfo(), serverSlot.captured.user)
        }

        @Test
        fun `Given different user info when getting current user then picture path is cleared`() = runTest {
            val server = createServer(user = userInfo(picturePath = "/person.anna/picture.jpeg"))
            coEvery { webSocketCore.server() } returns server
            val serverSlot = slot<Server>()
            coEvery { serverManager.updateServer(capture(serverSlot)) } just Runs

            mockUserResponse(userId = "2")

            repository.getCurrentUser()
            assertEquals(userInfo(userId = "2", picturePath = null), serverSlot.captured.user)
        }

        @Test
        fun `Given no user info when getting current user then info is not updated`() = runTest {
            coEvery { webSocketCore.sendMessage(mapOf("type" to "auth/current_user")) } returns null

            repository.getCurrentUser()
            coVerify(exactly = 0) { serverManager.updateServer(any()) }
        }
    }

    @Nested
    inner class RunAssistPipelineForVoice {

        private fun captureSubscribeData(server: Server? = null): CapturingSlot<Map<String, Any?>> {
            val dataSlot = slot<Map<String, Any?>>()
            coEvery {
                webSocketCore.subscribeTo<AssistPipelineEvent>(
                    type = SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN,
                    data = capture(dataSlot),
                    timeout = any(),
                )
            } returns emptyFlow()
            coEvery { webSocketCore.server() } returns server
            return dataSlot
        }

        @Test
        fun `Given wake word When running pipeline Then wake_word_phrase is included in input`() = runTest {
            val dataSlot = captureSubscribeData()

            repository.runAssistPipelineForVoice(
                sampleRate = VOICE_SAMPLE_RATE,
                outputTts = true,
                pipelineId = null,
                conversationId = null,
                wakeWordPhrase = "okay nabu",
            )

            coVerify {
                webSocketCore.subscribeTo<AssistPipelineEvent>(
                    type = SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN,
                    data = any(),
                    timeout = any(),
                )
            }

            @Suppress("UNCHECKED_CAST")
            val input = dataSlot.captured["input"] as Map<String, Any?>
            assertEquals("okay nabu", input["wake_word_phrase"])
        }

        @Test
        fun `Given no wake word When running pipeline Then wake_word_phrase is not included`() = runTest {
            val dataSlot = captureSubscribeData()

            repository.runAssistPipelineForVoice(
                sampleRate = VOICE_SAMPLE_RATE,
                outputTts = true,
                pipelineId = null,
                conversationId = null,
                wakeWordPhrase = null,
            )

            @Suppress("UNCHECKED_CAST")
            val input = dataSlot.captured["input"] as Map<String, Any?>
            assertFalse(input.containsKey("wake_word_phrase"))
        }

        @ParameterizedTest(name = "Given outputTts={0} When running pipeline Then end_stage is {1}")
        @CsvSource("true, tts", "false, intent")
        fun `Given outputTts When running pipeline Then end_stage matches`(
            outputTts: Boolean,
            expectedEndStage: String,
        ) = runTest {
            val dataSlot = captureSubscribeData()

            repository.runAssistPipelineForVoice(
                sampleRate = VOICE_SAMPLE_RATE,
                outputTts = outputTts,
                pipelineId = null,
                conversationId = null,
                wakeWordPhrase = null,
            )

            assertEquals(expectedEndStage, dataSlot.captured["end_stage"])
        }

        @Test
        fun `Given pipelineId When running pipeline Then pipeline is included`() = runTest {
            val dataSlot = captureSubscribeData()

            repository.runAssistPipelineForVoice(
                sampleRate = VOICE_SAMPLE_RATE,
                outputTts = true,
                pipelineId = "my-pipeline-id",
                conversationId = null,
                wakeWordPhrase = null,
            )

            assertEquals("my-pipeline-id", dataSlot.captured["pipeline"])
        }

        @Test
        fun `Given no pipelineId When running pipeline Then pipeline is not included`() = runTest {
            val dataSlot = captureSubscribeData()

            repository.runAssistPipelineForVoice(
                sampleRate = VOICE_SAMPLE_RATE,
                outputTts = true,
                pipelineId = null,
                conversationId = null,
                wakeWordPhrase = null,
            )

            assertFalse(dataSlot.captured.containsKey("pipeline"))
        }

        @Test
        fun `Given server with deviceRegistryId When running pipeline Then device_id is included`() = runTest {
            val dataSlot = captureSubscribeData(server = createServer(deviceRegistryId = "device-123"))

            repository.runAssistPipelineForVoice(
                sampleRate = VOICE_SAMPLE_RATE,
                outputTts = true,
                pipelineId = null,
                conversationId = null,
                wakeWordPhrase = null,
            )

            assertEquals("device-123", dataSlot.captured["device_id"])
        }

        @Test
        fun `Given server without deviceRegistryId When running pipeline Then device_id is not included`() = runTest {
            val dataSlot = captureSubscribeData(server = createServer(deviceRegistryId = null))

            repository.runAssistPipelineForVoice(
                sampleRate = VOICE_SAMPLE_RATE,
                outputTts = true,
                pipelineId = null,
                conversationId = null,
                wakeWordPhrase = null,
            )

            assertFalse(dataSlot.captured.containsKey("device_id"))
        }

        @Test
        fun `Given conversationId When running pipeline Then conversation_id is included`() = runTest {
            val dataSlot = captureSubscribeData()

            repository.runAssistPipelineForVoice(
                sampleRate = VOICE_SAMPLE_RATE,
                outputTts = true,
                pipelineId = null,
                conversationId = "conv-456",
                wakeWordPhrase = null,
            )

            assertEquals("conv-456", dataSlot.captured["conversation_id"])
        }

        @Test
        fun `Given sampleRate When running pipeline Then sample_rate is included in input`() = runTest {
            val dataSlot = captureSubscribeData()

            repository.runAssistPipelineForVoice(
                sampleRate = 16000,
                outputTts = true,
                pipelineId = null,
                conversationId = null,
                wakeWordPhrase = null,
            )

            @Suppress("UNCHECKED_CAST")
            val input = dataSlot.captured["input"] as Map<String, Any?>
            assertEquals(16000, input["sample_rate"])
        }

        @Test
        fun `When running pipeline Then start_stage is always stt`() = runTest {
            val dataSlot = captureSubscribeData()

            repository.runAssistPipelineForVoice(
                sampleRate = VOICE_SAMPLE_RATE,
                outputTts = true,
                pipelineId = null,
                conversationId = null,
                wakeWordPhrase = null,
            )

            assertEquals("stt", dataSlot.captured["start_stage"])
        }
    }

    @Nested
    inner class RegistryForDisplay {

        private fun captureSentMessage(resultJson: String, version: String = "2025.1.0"): CapturingSlot<Map<String, Any?>> {
            coEvery { webSocketCore.server() } returns createServer(version = version)
            val messageSlot = slot<Map<String, Any?>>()
            coEvery { webSocketCore.sendMessage(capture(messageSlot)) } returns MessageSocketResponse(
                id = 1,
                success = true,
                result = kotlinJsonMapper.parseToJsonElement(resultJson),
            )
            return messageSlot
        }

        @Test
        fun `Given a display registry response When getting entity registry display Then sends list_for_display and decodes result`() = runTest {
            val messageSlot = captureSentMessage(
                """{"entity_categories": {"0": "config"}, "entities": [{"ei": "light.bed", "en": "Bed"}]}""",
            )

            val response = repository.getEntityRegistryDisplay()

            assertEquals("config/entity_registry/list_for_display", messageSlot.captured["type"])
            assertEquals("light.bed", response?.entities?.single()?.entityId)
            assertEquals("Bed", response?.entities?.single()?.name)
            assertEquals(mapOf(0 to "config"), response?.entityCategories)
        }

        @Test
        fun `Given a server older than 2024 10 When getting entity registry display Then returns null without sending`() = runTest {
            captureSentMessage("""{"entities": []}""", version = "2024.9.3")

            assertNull(repository.getEntityRegistryDisplay())

            coVerify(exactly = 0) { webSocketCore.sendMessage(any<Map<String, Any?>>()) }
        }

        @Test
        fun `Given a server older than 2024 3 When getting floor registry Then returns null without sending`() = runTest {
            captureSentMessage("""[]""", version = "2024.2.0")

            assertNull(repository.getFloorRegistry())

            coVerify(exactly = 0) { webSocketCore.sendMessage(any<Map<String, Any?>>()) }
        }

        @Test
        fun `Given a floor registry response When getting floor registry Then sends floor_registry list and decodes result`() = runTest {
            val messageSlot = captureSentMessage(
                """[{"floor_id": "ground", "name": "Ground", "level": 0}]""",
            )

            val response = repository.getFloorRegistry()

            assertEquals("config/floor_registry/list", messageSlot.captured["type"])
            assertEquals("ground", response?.single()?.floorId)
            assertEquals(0, response?.single()?.level)
        }
    }

    @Nested
    inner class Panels {

        @Test
        fun `Given a panels response When getting panels Then sends get_panels and decodes the panels`() = runTest {
            val messageSlot = slot<Map<String, Any?>>()
            coEvery { webSocketCore.sendMessage(capture(messageSlot)) } returns MessageSocketResponse(
                id = 1,
                success = true,
                result = kotlinJsonMapper.parseToJsonElement(
                    """{
                        "lovelace": {"component_name": "lovelace", "title": null, "url_path": "lovelace", "require_admin": false},
                        "config": {"component_name": "config", "title": "config", "url_path": "config", "require_admin": true}
                    }""",
                ),
            )

            val panels = repository.getPanels()

            assertEquals("get_panels", messageSlot.captured["type"])
            assertEquals(listOf(PanelResponse("lovelace"), PanelResponse("config")), panels)
        }

        @Test
        fun `Given no response When getting panels Then returns null`() = runTest {
            coEvery { webSocketCore.sendMessage(any<Map<String, Any?>>()) } returns null

            assertNull(repository.getPanels())
        }
    }

    private fun createServer(deviceRegistryId: String? = null, version: String? = null, user: ServerUserInfo? = null): Server {
        return Server(
            id = 1,
            _name = "Test Server",
            _version = version,
            deviceRegistryId = deviceRegistryId,
            connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
            ),
            session = ServerSessionInfo(),
            user = user ?: ServerUserInfo(),
        )
    }
}
