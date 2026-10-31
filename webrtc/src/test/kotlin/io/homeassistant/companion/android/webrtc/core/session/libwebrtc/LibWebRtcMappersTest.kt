package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.session.RtcConnectionState
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import io.homeassistant.companion.android.webrtc.core.signaling.RtcIceServer
import livekit.org.webrtc.PeerConnection
import livekit.org.webrtc.RtpTransceiver.RtpTransceiverDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LibWebRtcMappersTest {

    @Nested
    inner class AudioTransceiverDirection {

        @Test
        fun `Given audio in and out When mapping the direction Then it is send-recv`() {
            val direction = MediaOptions(audioIn = true, audioOut = true).audioTransceiverDirection()
            assertEquals(RtpTransceiverDirection.SEND_RECV, direction)
        }

        @Test
        fun `Given only audio out When mapping the direction Then it is send-only`() {
            val direction = MediaOptions(audioIn = false, audioOut = true).audioTransceiverDirection()
            assertEquals(RtpTransceiverDirection.SEND_ONLY, direction)
        }

        @Test
        fun `Given only audio in When mapping the direction Then it is recv-only`() {
            val direction = MediaOptions(audioIn = true, audioOut = false).audioTransceiverDirection()
            assertEquals(RtpTransceiverDirection.RECV_ONLY, direction)
        }

        @Test
        fun `Given no audio When mapping the direction Then it is null`() {
            val direction = MediaOptions(videoIn = true, audioIn = false, audioOut = false).audioTransceiverDirection()
            assertNull(direction)
        }
    }

    @Nested
    inner class ToIceCandidate {

        @Test
        fun `Given a candidate with an sdpMid Then that mid and m-line index are used`() {
            val result = IceCandidateInit(candidate = "cand", sdpMid = "audio", sdpMLineIndex = 1).toIceCandidate()

            assertEquals("audio", result.sdpMid)
            assertEquals(1, result.sdpMLineIndex)
            assertEquals("cand", result.sdp)
        }

        @Test
        fun `Given a candidate with an sdpMid but no m-line index Then the index defaults to zero`() {
            val result = IceCandidateInit(candidate = "cand", sdpMid = "video").toIceCandidate()

            assertEquals("video", result.sdpMid)
            assertEquals(0, result.sdpMLineIndex)
        }

        @Test
        fun `Given a candidate with only an m-line index Then the mid is left empty so the index resolves it`() {
            val result = IceCandidateInit(candidate = "cand", sdpMLineIndex = 2).toIceCandidate()

            assertEquals("", result.sdpMid)
            assertEquals(2, result.sdpMLineIndex)
        }

        @Test
        fun `Given a candidate with neither mid nor index Then it targets the first m-section`() {
            val result = IceCandidateInit(candidate = "cand").toIceCandidate()

            assertEquals("0", result.sdpMid)
            assertEquals(0, result.sdpMLineIndex)
        }
    }

    @Nested
    inner class ToRtcConnectionState {

        @Test
        fun `Given each libwebrtc connection state Then it maps to the matching RtcConnectionState`() {
            assertEquals(RtcConnectionState.NEW, PeerConnection.PeerConnectionState.NEW.toRtcConnectionState())
            assertEquals(RtcConnectionState.CONNECTING, PeerConnection.PeerConnectionState.CONNECTING.toRtcConnectionState())
            assertEquals(RtcConnectionState.CONNECTED, PeerConnection.PeerConnectionState.CONNECTED.toRtcConnectionState())
            assertEquals(RtcConnectionState.DISCONNECTED, PeerConnection.PeerConnectionState.DISCONNECTED.toRtcConnectionState())
            assertEquals(RtcConnectionState.FAILED, PeerConnection.PeerConnectionState.FAILED.toRtcConnectionState())
            assertEquals(RtcConnectionState.CLOSED, PeerConnection.PeerConnectionState.CLOSED.toRtcConnectionState())
        }
    }

    @Nested
    inner class ToRtcConfiguration {

        @Test
        fun `Given ice servers When building the configuration Then servers are mapped and empty ones dropped`() {
            val config = RtcClientConfig(
                iceServers = listOf(
                    RtcIceServer(urls = listOf("stun:stun.example.org"), username = "user", credential = "pass"),
                    RtcIceServer(urls = emptyList()),
                ),
            )

            val rtc = config.toRtcConfiguration()

            assertEquals(1, rtc.iceServers.size)
            assertEquals(listOf("stun:stun.example.org"), rtc.iceServers[0].urls)
            assertEquals("user", rtc.iceServers[0].username)
            assertEquals("pass", rtc.iceServers[0].password)
            assertEquals(PeerConnection.SdpSemantics.UNIFIED_PLAN, rtc.sdpSemantics)
            assertEquals(PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY, rtc.continualGatheringPolicy)
        }
    }
}
