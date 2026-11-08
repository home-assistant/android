package io.homeassistant.companion.android.notifications.callscreen.webrtc

import io.homeassistant.companion.android.notifications.callscreen.Go2RtcSource
import java.net.URL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Go2RtcSignalingTest {

    @Test
    fun `Given https base and stream url when building ws url then scheme is wss and url is encoded`() {
        assertEquals(
            "wss://ha.example:8123/api/webrtc/ws?authSig=abc&url=rtsp%3A%2F%2Fa+b",
            buildGo2RtcWsUrl(
                URL("https://ha.example:8123"),
                "/api/webrtc/ws?authSig=abc",
                Go2RtcSource.Url("rtsp://a b"),
                null,
            ),
        )
    }

    @Test
    fun `Given http base with prefix and entity and server when building ws url then prefix and params are kept`() {
        assertEquals(
            "ws://ha.local/prefix/api/webrtc/ws?authSig=abc&entity=camera.x&server=http%3A%2F%2Fg%3A1984",
            buildGo2RtcWsUrl(
                URL("http://ha.local/prefix/"),
                "/api/webrtc/ws?authSig=abc",
                Go2RtcSource.Entity("camera.x"),
                "http://g:1984",
            ),
        )
    }

    @Test
    fun `Given go2rtc frames when parsing then known types map and unknown or malformed are null`() {
        assertEquals(Go2RtcMessage.Answer("v=0"), parseGo2RtcMessage("""{"type":"webrtc/answer","value":"v=0"}"""))
        assertEquals(
            Go2RtcMessage.Candidate("candidate:1"),
            parseGo2RtcMessage("""{"type":"webrtc/candidate","value":"candidate:1"}"""),
        )
        assertEquals(Go2RtcMessage.Error("boom"), parseGo2RtcMessage("""{"type":"error","value":"boom"}"""))
        assertNull(parseGo2RtcMessage("""{"type":"mse","value":"x"}"""))
        assertNull(parseGo2RtcMessage("not json"))
    }

    @Test
    fun `Given sdp when encoding then frames carry type and value`() {
        assertEquals("""{"type":"webrtc/offer","value":"v=0\r\n"}""", encodeOffer("v=0\r\n"))
        assertEquals("""{"type":"webrtc/candidate","value":""}""", encodeCandidate(""))
    }
}
