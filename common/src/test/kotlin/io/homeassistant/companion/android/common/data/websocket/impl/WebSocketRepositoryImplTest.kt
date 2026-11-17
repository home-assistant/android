package io.homeassistant.companion.android.common.data.websocket.impl

import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.SubscriptionReconnectPolicy
import io.homeassistant.companion.android.common.data.websocket.WebSocketCore
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_CAMERA_WEBRTC_OFFER
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraStreamType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraWebRtcClientConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryDisplayEntry
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MessageSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcCandidate
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcConfiguration
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcIceServer
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    inner class FilteredStates {
        private val subscribeData = slot<Map<String, Any?>>()

        @BeforeEach
        fun setUpServer() {
            coEvery { webSocketCore.server() } returns createServer(version = "2024.9.0")
        }

        private fun givenAllStates(vararg entityIds: String): CapturingSlot<Map<String, Any?>> {
            val messageSlot = slot<Map<String, Any?>>()
            val states = entityIds.joinToString { entityId ->
                """{"entity_id": "$entityId", "state": "on", "attributes": {}, "last_changed": "2024-01-01T12:00:00+00:00", "last_updated": "2024-01-01T12:00:00+00:00"}"""
            }
            coEvery { webSocketCore.sendMessage(capture(messageSlot)) } returns MessageSocketResponse(
                id = 1,
                success = true,
                result = kotlinJsonMapper.parseToJsonElement("[$states]"),
            )
            return messageSlot
        }

        private fun givenInitialStates(event: CompressedStateChangedEvent?) {
            coEvery {
                webSocketCore.firstEventOf<CompressedStateChangedEvent>(
                    type = SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES,
                    data = capture(subscribeData),
                )
            } returns event
        }

        private fun addedEvent(vararg entityIds: String) = CompressedStateChangedEvent(
            added = entityIds.associateWith {
                CompressedEntityState(state = JsonPrimitive("on"), lastChanged = 1_700_000_000.0)
            },
        )

        @Test
        fun `Given a filter on ids When getting states Then the states a subscription starts with are returned`() = runTest {
            givenInitialStates(addedEvent("light.bed"))

            val states = repository.getStates(EntityFilter(entityIds = setOf("switch.fan", "light.bed")))

            assertEquals(listOf("light.bed"), states?.map { it.entityId })
            // Sorted, so the same selection always shares the same subscription
            assertEquals(mapOf("entity_ids" to listOf("light.bed", "switch.fan")), subscribeData.captured)
        }

        @Test
        fun `Given a filter on domains When getting states Then the include filter is sent`() = runTest {
            givenInitialStates(addedEvent())

            val states = repository.getStates(EntityFilter(domains = setOf("switch", "light"), entityIds = setOf("camera.door")))

            assertEquals(emptyList<String>(), states?.map { it.entityId })
            assertEquals(
                mapOf("include" to mapOf("domains" to listOf("light", "switch"), "entities" to listOf("camera.door"))),
                subscribeData.captured,
            )
        }

        @Test
        fun `Given no initial states When getting states Then null is returned`() = runTest {
            givenInitialStates(null)

            assertNull(repository.getStates(EntityFilter(entityIds = setOf("light.bed"))))
        }

        @Test
        fun `Given states that never arrive When getting states Then null is returned after 30 seconds`() = runTest {
            coEvery { webSocketCore.firstEventOf<CompressedStateChangedEvent>(any(), any()) } coAnswers { awaitCancellation() }

            assertNull(repository.getStates(EntityFilter(entityIds = setOf("light.bed"))))

            assertEquals(30.seconds, testScheduler.currentTime.milliseconds)
        }

        @Test
        fun `Given a server not filtering domains When getting states for domains Then every state is fetched and filtered on the device`() = runTest {
            coEvery { webSocketCore.server() } returns createServer(version = "2024.8.3")
            val messageSlot = givenAllStates("light.bed", "sensor.power")

            val states = repository.getStates(EntityFilter(domains = setOf("light")))

            assertEquals(listOf("light.bed"), states?.map { it.entityId })
            assertEquals("get_states", messageSlot.captured["type"])
            coVerify(exactly = 0) { webSocketCore.firstEventOf<CompressedStateChangedEvent>(any(), any()) }
        }

        @Test
        fun `Given no filter When getting states Then every state is fetched`() = runTest {
            val messageSlot = givenAllStates("light.bed", "sensor.power")

            val states = repository.getStates()

            assertEquals(listOf("light.bed", "sensor.power"), states?.map { it.entityId })
            assertEquals("get_states", messageSlot.captured["type"])
        }

        @Test
        fun `Given an empty filter When getting states Then every state is fetched as the server would`() = runTest {
            val messageSlot = givenAllStates("light.bed", "sensor.power")

            val states = repository.getStates(EntityFilter())

            assertEquals(listOf("light.bed", "sensor.power"), states?.map { it.entityId })
            assertEquals("get_states", messageSlot.captured["type"])
            coVerify(exactly = 0) { webSocketCore.firstEventOf<CompressedStateChangedEvent>(any(), any()) }
        }

        @Test
        fun `Given an empty filter When following state changes Then the subscription isn't limited`() = runTest {
            coEvery {
                webSocketCore.subscribeTo<CompressedStateChangedEvent>(
                    type = SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES,
                    data = capture(subscribeData),
                    timeout = any(),
                    reconnectPolicy = SubscriptionReconnectPolicy.Resubscribe,
                )
            } returns emptyFlow()

            repository.getCompressedStateAndChanges()

            assertEquals(emptyMap<String, Any?>(), subscribeData.captured)
        }

        @Test
        fun `Given a filter When following state changes Then the shared subscription is limited to it`() = runTest {
            val events = flowOf(addedEvent("light.bed"))
            coEvery {
                webSocketCore.subscribeTo<CompressedStateChangedEvent>(
                    type = SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES,
                    data = capture(subscribeData),
                    timeout = any(),
                    reconnectPolicy = SubscriptionReconnectPolicy.Resubscribe,
                )
            } returns events

            val changes = repository.getCompressedStateAndChanges(EntityFilter(domains = setOf("light")))

            assertEquals(events, changes)
            assertEquals(
                mapOf("include" to mapOf("domains" to listOf("light"), "entities" to emptyList<String>())),
                subscribeData.captured,
            )
        }
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
        fun `Given entity ids When getting entity registry display Then only their entries are requested and converted for display`() = runTest {
            val messageSlot = captureSentMessage(
                """
                {
                  "sensor.power": {
                    "entity_id": "sensor.power", "name": null, "original_name": "Power", "icon": "mdi:flash",
                    "area_id": "kitchen", "device_id": "plug", "hidden_by": "user", "entity_category": "diagnostic",
                    "labels": ["energy"], "options": {"sensor": {"suggested_display_precision": 1}}
                  },
                  "light.bed": {"entity_id": "light.bed", "name": "Bed", "original_name": "Light", "hidden_by": null},
                  "light.unknown": null
                }
                """.trimIndent(),
            )

            val response = checkNotNull(repository.getEntityRegistryDisplay(EntityFilter(entityIds = setOf("sensor.power", "light.bed", "light.unknown"))))

            assertEquals("config/entity_registry/get_entries", messageSlot.captured["type"])
            assertEquals(listOf("sensor.power", "light.bed", "light.unknown"), messageSlot.captured["entity_ids"])
            assertEquals(
                listOf(
                    EntityRegistryDisplayEntry(
                        entityId = "sensor.power",
                        areaId = "kitchen",
                        deviceId = "plug",
                        labels = listOf("energy"),
                        icon = "mdi:flash",
                        entityCategory = 0,
                        hidden = true,
                        name = "Power",
                        displayPrecision = 1,
                    ),
                    EntityRegistryDisplayEntry(entityId = "light.bed", name = "Bed"),
                ),
                response.entities,
            )
            assertEquals(mapOf(0 to "diagnostic"), response.entityCategories)
        }

        @Test
        fun `Given a server filtering the display registry When getting it for a filter Then the server limits it`() = runTest {
            val messageSlot = captureSentMessage(
                """{"entity_categories": {}, "entities": [{"ei": "light.bed", "en": "Bed"}]}""",
                version = "2026.12.0",
            )

            val response = repository.getEntityRegistryDisplay(
                EntityFilter(domains = setOf("light"), entityIds = setOf("switch.fan")),
            )

            assertEquals(
                mapOf(
                    "type" to "config/entity_registry/list_for_display",
                    "include" to mapOf("domains" to listOf("light"), "entities" to listOf("switch.fan")),
                ),
                messageSlot.captured,
            )
            assertEquals(listOf("light.bed"), response?.entities?.map { it.entityId })
        }

        @Test
        fun `Given a server filtering the display registry When getting it for ids Then the server limits it without get_entries`() = runTest {
            val messageSlot = captureSentMessage(
                """{"entity_categories": {}, "entities": [{"ei": "light.bed", "en": "Bed"}]}""",
                version = "2026.12.0",
            )

            repository.getEntityRegistryDisplay(EntityFilter(entityIds = setOf("light.bed")))

            assertEquals(
                mapOf("type" to "config/entity_registry/list_for_display", "entity_ids" to listOf("light.bed")),
                messageSlot.captured,
            )
        }

        @Test
        fun `Given a server not filtering the display registry When getting it for domains Then the whole registry is filtered on the device`() = runTest {
            val messageSlot = captureSentMessage(
                """{"entity_categories": {}, "entities": [{"ei": "light.bed"}, {"ei": "sensor.power"}]}""",
                version = "2026.11.3",
            )

            val response = repository.getEntityRegistryDisplay(EntityFilter(domains = setOf("light")))

            assertEquals(mapOf<String, Any?>("type" to "config/entity_registry/list_for_display"), messageSlot.captured)
            assertEquals(listOf("light.bed"), response?.entities?.map { it.entityId })
        }

        @Test
        fun `Given a server older than 2024 10 When getting entity registry display for ids Then returns null without sending`() = runTest {
            captureSentMessage("""{}""", version = "2024.9.3")

            assertNull(repository.getEntityRegistryDisplay(EntityFilter(entityIds = setOf("light.bed"))))

            coVerify(exactly = 0) { webSocketCore.sendMessage(any<Map<String, Any?>>()) }
        }

        @Test
        fun `Given a failed command When getting entity registry display for ids Then returns null`() = runTest {
            coEvery { webSocketCore.server() } returns createServer(version = "2025.1.0")
            coEvery { webSocketCore.sendMessage(any<Map<String, Any?>>()) } returns MessageSocketResponse(
                id = 1,
                success = false,
                error = kotlinJsonMapper.parseToJsonElement("""{"code": "unknown_command"}"""),
            )

            assertNull(repository.getEntityRegistryDisplay(EntityFilter(entityIds = setOf("light.bed"))))
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
            assertEquals(listOf(CameraStreamType.Hls, CameraStreamType.WebRtc), capabilities?.frontendStreamTypes)
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
                config,
            )
        }

        @Test
        fun `Given a failing command When getting WebRTC client config Then null is returned`() = runTest {
            captureSentMessage(MessageSocketResponse(id = 1, success = false))

            assertNull(repository.getCameraWebRtcClientConfig("camera.front_door"))
        }

        @Test
        fun `Given a camera When starting a WebRTC session Then offer subscription is started without replay`() = runTest {
            val dataSlot = slot<Map<String, Any?>>()
            coEvery {
                webSocketCore.subscribeTo<WebRtcEvent>(
                    type = SUBSCRIBE_TYPE_CAMERA_WEBRTC_OFFER,
                    data = capture(dataSlot),
                    timeout = any(),
                    reconnectPolicy = SubscriptionReconnectPolicy.Complete,
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
