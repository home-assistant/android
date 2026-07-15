package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class WebRtcEventTest {

    private inline fun <reified T> decode(json: String): T = webRtcJsonMapper.decodeFromString<T>(json)

    @Test
    fun `Given a session event When decoding Then session id is extracted`() {
        val event = decode<WebRtcEvent>("""{"type": "session", "session_id": "01JAYSESSION"}""")

        assertEquals(WebRtcEvent.Session(sessionId = "01JAYSESSION"), event)
    }

    @Test
    fun `Given an answer event When decoding Then sdp is extracted`() {
        val event = decode<WebRtcEvent>("""{"type": "answer", "answer": "v=0 fake sdp"}""")

        assertEquals(WebRtcEvent.Answer(answer = "v=0 fake sdp"), event)
    }

    @Test
    fun `Given a candidate event When decoding Then camelCase candidate fields are extracted`() {
        val event = decode<WebRtcEvent>(
            """
            {
                "type": "candidate",
                "candidate": {
                    "candidate": "candidate:1 1 UDP 2130706431 192.168.1.2 54400 typ host",
                    "sdpMid": "0",
                    "sdpMLineIndex": 0,
                    "usernameFragment": "abcd"
                }
            }
            """.trimIndent(),
        )

        assertEquals(
            WebRtcEvent.Candidate(
                candidate = WebRtcCandidate(
                    candidate = "candidate:1 1 UDP 2130706431 192.168.1.2 54400 typ host",
                    sdpMid = "0",
                    sdpMLineIndex = 0,
                    usernameFragment = "abcd",
                ),
            ),
            event,
        )
    }

    @Test
    fun `Given a candidate event without optional fields When decoding Then defaults are used`() {
        val event = decode<WebRtcEvent>(
            """{"type": "candidate", "candidate": {"candidate": "candidate:1"}}""",
        )

        assertEquals(
            WebRtcEvent.Candidate(candidate = WebRtcCandidate(candidate = "candidate:1")),
            event,
        )
    }

    @Test
    fun `Given an error event When decoding Then code and message are extracted`() {
        val event = decode<WebRtcEvent>(
            """{"type": "error", "code": "webrtc_offer_failed", "message": "Camera does not support WebRTC"}""",
        )

        assertEquals(
            WebRtcEvent.Error(code = "webrtc_offer_failed", message = "Camera does not support WebRTC"),
            event,
        )
    }

    @Test
    fun `Given an unknown event type When decoding Then Unknown is returned instead of throwing`() {
        val event = decode<WebRtcEvent>("""{"type": "brand_new_event", "value": 42}""")

        assertInstanceOf(WebRtcEvent.Unknown::class.java, event)
        assertEquals("brand_new_event", (event as WebRtcEvent.Unknown).discriminator)
    }

    @Test
    fun `Given a candidate When encoding Then camelCase keys are kept and nulls omitted`() {
        val json = webRtcJsonMapper.encodeToJsonElement(
            WebRtcCandidate(candidate = "candidate:1", sdpMid = "0", sdpMLineIndex = 0, usernameFragment = null),
        ).jsonObject

        assertEquals(setOf("candidate", "sdpMid", "sdpMLineIndex"), json.keys)
        assertFalse(json.containsKey("usernameFragment"))
    }

    @Test
    fun `Given a client config with a single url string When decoding Then urls is normalized to a list`() {
        val config = decode<CameraWebRtcClientConfigResponse>(
            """{"configuration": {"iceServers": [{"urls": "stun:stun.home-assistant.io:80"}]}}""",
        )

        assertEquals(
            CameraWebRtcClientConfigResponse(
                configuration = WebRtcConfiguration(
                    iceServers = listOf(WebRtcIceServer(urls = listOf("stun:stun.home-assistant.io:80"))),
                ),
            ),
            config,
        )
    }

    @Test
    fun `Given an empty client config When decoding Then defaults are used`() {
        val config = decode<CameraWebRtcClientConfigResponse>("""{"configuration": {}}""")

        assertEquals(CameraWebRtcClientConfigResponse(), config)
    }

    @Test
    fun `Given a capabilities response When decoding with the shared mapper Then snake_case is handled`() {
        val capabilities =
            kotlinJsonMapper.decodeFromString<CameraCapabilitiesResponse>(
                """{"frontend_stream_types": ["hls", "web_rtc"]}""",
            )

        assertEquals(listOf(CameraStreamTypes.HLS, CameraStreamTypes.WEB_RTC), capabilities.frontendStreamTypes)
    }
}
