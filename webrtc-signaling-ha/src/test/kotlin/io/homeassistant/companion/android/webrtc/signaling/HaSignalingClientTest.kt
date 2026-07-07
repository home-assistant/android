package io.homeassistant.companion.android.webrtc.signaling

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraCapabilitiesResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraWebRtcClientConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcCandidate
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcConfiguration
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcIceServer
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import io.homeassistant.companion.android.webrtc.core.signaling.RtcIceServer
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingEvent
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingException
import io.homeassistant.companion.android.webrtc.core.signaling.StreamType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val ENTITY_ID = "camera.front_door"
private const val SESSION_ID = "01JAYSESSION"

class HaSignalingClientTest {

    private val webSocketRepository: WebSocketRepository = mockk()
    private val client = HaSignalingClient(webSocketRepository)

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given a camera with streams When getting capabilities Then known types are mapped`() = runTest {
        coEvery { webSocketRepository.getCameraCapabilities(ENTITY_ID) } returns
            CameraCapabilitiesResponse(frontendStreamTypes = listOf("hls", "web_rtc", "brand_new_type"))

        val capabilities = client.getStreamCapabilities(ENTITY_ID)

        assertEquals(setOf(StreamType.HLS, StreamType.WEB_RTC), capabilities)
    }

    @Test
    fun `Given no response When getting capabilities Then a SignalingException is thrown`() = runTest {
        coEvery { webSocketRepository.getCameraCapabilities(ENTITY_ID) } returns null

        assertThrows<SignalingException> { client.getStreamCapabilities(ENTITY_ID) }
    }

    @Test
    fun `Given a client config When getting it Then it is mapped to the core model`() = runTest {
        coEvery { webSocketRepository.getCameraWebRtcClientConfig(ENTITY_ID) } returns
            CameraWebRtcClientConfigResponse(
                configuration = WebRtcConfiguration(
                    iceServers = listOf(
                        WebRtcIceServer(
                            urls = listOf("turn:example.org:3478"),
                            username = "user",
                            credential = "secret",
                        ),
                    ),
                ),
                dataChannel = "webrtc",
            )

        val config = client.getClientConfig(ENTITY_ID)

        assertEquals(
            RtcClientConfig(
                iceServers = listOf(
                    RtcIceServer(urls = listOf("turn:example.org:3478"), username = "user", credential = "secret"),
                ),
                dataChannelLabel = "webrtc",
            ),
            config,
        )
    }

    @Test
    fun `Given no response When getting the client config Then a SignalingException is thrown`() = runTest {
        coEvery { webSocketRepository.getCameraWebRtcClientConfig(ENTITY_ID) } returns null

        assertThrows<SignalingException> { client.getClientConfig(ENTITY_ID) }
    }

    @Test
    fun `Given a session When opening it Then the events are mapped and unknown ones dropped`() = runTest {
        coEvery { webSocketRepository.startCameraWebRtcSession(ENTITY_ID, "v=0 offer") } returns flowOf(
            WebRtcEvent.Session(sessionId = SESSION_ID),
            WebRtcEvent.Answer(answer = "v=0 answer"),
            WebRtcEvent.Unknown(discriminator = "brand_new_event", content = JsonNull),
            WebRtcEvent.Candidate(candidate = WebRtcCandidate(candidate = "candidate:1", sdpMid = "0")),
            WebRtcEvent.Error(code = "webrtc_offer_failed", message = "boom"),
        )

        client.openSession(ENTITY_ID, "v=0 offer").test {
            assertEquals(SignalingEvent.Session(sessionId = SESSION_ID), awaitItem())
            assertEquals(SignalingEvent.Answer(sdp = "v=0 answer"), awaitItem())
            assertEquals(
                SignalingEvent.Candidate(candidate = IceCandidateInit(candidate = "candidate:1", sdpMid = "0")),
                awaitItem(),
            )
            assertEquals(SignalingEvent.Error(code = "webrtc_offer_failed", message = "boom"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Given no subscription When opening a session Then the flow fails with a SignalingException`() = runTest {
        coEvery { webSocketRepository.startCameraWebRtcSession(ENTITY_ID, "v=0 offer") } returns null

        client.openSession(ENTITY_ID, "v=0 offer").test {
            assertTrue(awaitError() is SignalingException)
        }
    }

    @Test
    fun `Given a candidate When sending it Then it is mapped and the result returned`() = runTest {
        coEvery {
            webSocketRepository.sendCameraWebRtcCandidate(ENTITY_ID, SESSION_ID, any())
        } returns true

        val accepted = client.sendCandidate(
            entityId = ENTITY_ID,
            sessionId = SESSION_ID,
            candidate = IceCandidateInit(candidate = "candidate:1", sdpMid = "0", sdpMLineIndex = 0),
        )

        assertTrue(accepted)
        coVerify {
            webSocketRepository.sendCameraWebRtcCandidate(
                ENTITY_ID,
                SESSION_ID,
                WebRtcCandidate(candidate = "candidate:1", sdpMid = "0", sdpMLineIndex = 0),
            )
        }
    }

    @Test
    fun `Given a rejected candidate When sending it Then false is returned`() = runTest {
        coEvery { webSocketRepository.sendCameraWebRtcCandidate(ENTITY_ID, SESSION_ID, any()) } returns false

        val accepted = client.sendCandidate(
            entityId = ENTITY_ID,
            sessionId = SESSION_ID,
            candidate = IceCandidateInit(candidate = "candidate:1"),
        )

        assertFalse(accepted)
    }
}
