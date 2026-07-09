package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SdpAnswerDirectionTest {

    private fun answer(vararg mediaSections: String): String = buildString {
        append("v=0\r\no=- 1 1 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\n")
        mediaSections.forEach { append(it) }
    }

    @Test
    fun `Given an answer receiving audio Then the mic is supported`() {
        val sdp = answer(
            "m=video 9 UDP/TLS/RTP/SAVPF 100\r\na=sendonly\r\n",
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\na=recvonly\r\n",
        )
        assertTrue(sdp.answerAcceptsClientAudio())
    }

    @Test
    fun `Given an answer with two way audio Then the mic is supported`() {
        val sdp = answer("m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=sendrecv\r\n")
        assertTrue(sdp.answerAcceptsClientAudio())
    }

    @Test
    fun `Given an answer without direction attribute Then the mic is supported by default`() {
        val sdp = answer("m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\n")
        assertTrue(sdp.answerAcceptsClientAudio())
    }

    @Test
    fun `Given an answer only sending audio Then the mic is not supported`() {
        val sdp = answer(
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=sendonly\r\n",
            "m=video 9 UDP/TLS/RTP/SAVPF 100\r\na=sendonly\r\n",
        )
        assertFalse(sdp.answerAcceptsClientAudio())
    }

    @Test
    fun `Given an answer with inactive audio Then the mic is not supported`() {
        val sdp = answer("m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=inactive\r\n")
        assertFalse(sdp.answerAcceptsClientAudio())
    }

    @Test
    fun `Given an answer without audio section Then the mic is not supported`() {
        val sdp = answer("m=video 9 UDP/TLS/RTP/SAVPF 100\r\na=sendonly\r\n")
        assertFalse(sdp.answerAcceptsClientAudio())
    }

    @Test
    fun `Given a video section after the audio section Then only the audio direction counts`() {
        val sdp = answer(
            "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=recvonly\r\n",
            "m=video 9 UDP/TLS/RTP/SAVPF 100\r\na=inactive\r\n",
        )
        assertTrue(sdp.answerAcceptsClientAudio())
    }
}
