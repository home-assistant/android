package io.homeassistant.companion.android.common.data.websocket.impl

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketCore
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_CAMERA_WEBRTC_OFFER
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraStreamTypes
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraWebRtcClientConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraWebRtcClientConfigResult
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MessageSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcCandidate
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcConfiguration
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcIceServer
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    inner class CameraWebRtc {

        private fun captureSentMessage(response: MessageSocketResponse?): CapturingSlot<Map<String, Any?>> {
            val messageSlot = slot<Map<String, Any?>>()
            coEvery { webSocketCore.sendMessage(capture(messageSlot)) } returns response
            return messageSlot
        }

        private fun successResponse(resultJson: String): MessageSocketResponse = MessageSocketResponse(
            id = 1,
            success = true,
            result = kotlinJsonMapper.parseToJsonElement(resultJson),
        )

        @Test
        fun `Given a camera When getting capabilities Then message is sent and response decoded`() = runTest {
            val messageSlot = captureSentMessage(
                successResponse("""{"frontend_stream_types": ["hls", "web_rtc"]}"""),
            )

            val capabilities = repository.getCameraCapabilities("camera.front_door")

            assertEquals("camera/capabilities", messageSlot.captured["type"])
            assertEquals("camera.front_door", messageSlot.captured["entity_id"])
            assertEquals(listOf(CameraStreamTypes.HLS, CameraStreamTypes.WEB_RTC), capabilities?.frontendStreamTypes)
        }

        @Test
        fun `Given a camera When getting WebRTC client config Then camelCase response is decoded`() = runTest {
            val messageSlot = captureSentMessage(
                successResponse(
                    """
                    {
                        "configuration": {
                            "iceServers": [
                                {"urls": "stun:stun.home-assistant.io:80"},
                                {"urls": ["turn:example.org:3478"], "username": "user", "credential": "secret"}
                            ]
                        },
                        "dataChannel": "webrtc"
                    }
                    """.trimIndent(),
                ),
            )

            val config = repository.getCameraWebRtcClientConfig("camera.front_door")

            assertEquals("camera/webrtc/get_client_config", messageSlot.captured["type"])
            assertEquals("camera.front_door", messageSlot.captured["entity_id"])
            assertEquals(
                CameraWebRtcClientConfigResult.Success(
                    CameraWebRtcClientConfigResponse(
                        configuration = WebRtcConfiguration(
                            iceServers = listOf(
                                WebRtcIceServer(urls = listOf("stun:stun.home-assistant.io:80")),
                                WebRtcIceServer(
                                    urls = listOf("turn:example.org:3478"),
                                    username = "user",
                                    credential = "secret",
                                ),
                            ),
                        ),
                        dataChannel = "webrtc",
                    ),
                ),
                config,
            )
        }

        @Test
        fun `Given a failing command When getting WebRTC client config Then the server error is surfaced`() = runTest {
            captureSentMessage(
                MessageSocketResponse(
                    id = 1,
                    success = false,
                    error = kotlinJsonMapper.parseToJsonElement(
                        """{"code": "webrtc_get_client_config_failed", "message": "Camera does not support WebRTC"}""",
                    ),
                ),
            )

            assertEquals(
                CameraWebRtcClientConfigResult.Failure(
                    code = "webrtc_get_client_config_failed",
                    message = "Camera does not support WebRTC",
                ),
                repository.getCameraWebRtcClientConfig("camera.front_door"),
            )
        }

        @Test
        fun `Given no response When getting WebRTC client config Then a failure without error is returned`() = runTest {
            coEvery { webSocketCore.sendMessage(any<Map<String, Any?>>()) } returns null

            assertEquals(
                CameraWebRtcClientConfigResult.Failure(code = null, message = null),
                repository.getCameraWebRtcClientConfig("camera.front_door"),
            )
        }

        @Test
        fun `Given a camera When starting a WebRTC session Then offer subscription is started`() = runTest {
            val dataSlot = slot<Map<String, Any?>>()
            coEvery {
                webSocketCore.subscribeTo<WebRtcEvent>(
                    type = SUBSCRIBE_TYPE_CAMERA_WEBRTC_OFFER,
                    data = capture(dataSlot),
                    timeout = any(),
                )
            } returns emptyFlow()

            val events = repository.startCameraWebRtcSession("camera.front_door", "v=0 fake offer")

            assertNotNull(events)
            assertEquals("camera.front_door", dataSlot.captured["entity_id"])
            assertEquals("v=0 fake offer", dataSlot.captured["offer"])
        }

        @Test
        fun `Given a session When sending a candidate Then camelCase keys are kept and nulls omitted`() = runTest {
            val messageSlot = captureSentMessage(MessageSocketResponse(id = 1, success = true))

            val accepted = repository.sendCameraWebRtcCandidate(
                entityId = "camera.front_door",
                sessionId = "01JAYSESSION",
                candidate = WebRtcCandidate(
                    candidate = "candidate:1 1 UDP 2130706431 192.168.1.2 54400 typ host",
                    sdpMid = "0",
                    sdpMLineIndex = 0,
                    usernameFragment = null,
                ),
            )

            assertTrue(accepted)
            assertEquals("camera/webrtc/candidate", messageSlot.captured["type"])
            assertEquals("camera.front_door", messageSlot.captured["entity_id"])
            assertEquals("01JAYSESSION", messageSlot.captured["session_id"])
            val candidateJson = messageSlot.captured["candidate"] as JsonObject
            assertEquals(
                "candidate:1 1 UDP 2130706431 192.168.1.2 54400 typ host",
                (candidateJson["candidate"] as JsonPrimitive).content,
            )
            assertEquals("0", (candidateJson["sdpMid"] as JsonPrimitive).content)
            assertEquals(0, (candidateJson["sdpMLineIndex"] as JsonPrimitive).intOrNull)
            assertFalse(candidateJson.containsKey("usernameFragment"))
        }

        @Test
        fun `Given a rejected candidate When sending a candidate Then false is returned`() = runTest {
            captureSentMessage(MessageSocketResponse(id = 1, success = false))

            val accepted = repository.sendCameraWebRtcCandidate(
                entityId = "camera.front_door",
                sessionId = "01JAYSESSION",
                candidate = WebRtcCandidate(candidate = "candidate:1"),
            )

            assertFalse(accepted)
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
