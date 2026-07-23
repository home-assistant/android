package io.homeassistant.companion.android.common.data.websocket.impl

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketCore
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MessageSocketResponse
import io.homeassistant.companion.android.common.util.VOICE_SAMPLE_RATE
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

        private fun captureSentMessage(resultJson: String): CapturingSlot<Map<String, Any?>> {
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

    private fun createServer(deviceRegistryId: String? = null): Server {
        return Server(
            id = 1,
            _name = "Test Server",
            deviceRegistryId = deviceRegistryId,
            connection = ServerConnectionInfo(
                externalUrl = "https://example.com",
            ),
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        )
    }
}
