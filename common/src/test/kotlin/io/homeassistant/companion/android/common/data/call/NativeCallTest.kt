package io.homeassistant.companion.android.common.data.call

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NativeCallTest {
    @Test
    fun `Given a native PCM token when parsed then retain 48 kHz without narrowing it`() {
        val format = NativeCallPcmFormat.parse("48000:s16le:1:10")
        assertEquals(48000, format.sampleRate)
        assertEquals(960, format.frameBytes)
    }

    @Test
    fun `Given an invalid format when parsed then reject before opening hardware`() {
        listOf("48000:f32:1:10", "999999:s16le:1:10", "48000:s16le:8:10", "48000:s16le:1:0", "48000:s16le:2:40", "bad").forEach {
            assertThrows(IllegalArgumentException::class.java) { NativeCallPcmFormat.parse(it) }
        }
    }

    @Test
    fun `Given an API path when resolved then preserve authenticated server origin`() {
        val base = "https://ha.example:8123/".toHttpUrl()
        assertEquals("ha.example", nativeCallUrl(base, "/api/calls?id=one").host)
        listOf("https://other.example/api/call", "//other.example/api/call", "/api/../../outside", "/api/call#fragment", "/api/\\other").forEach {
            assertThrows(IllegalArgumentException::class.java) { nativeCallUrl(base, it) }
        }
    }

    @Test
    fun `Given an implicit active server when invited then reject ambiguous routing`() {
        assertThrows(IllegalArgumentException::class.java) { NativeCallInvitation(-1, "call", "/api/call") }
    }

    @ParameterizedTest
    @CsvSource(
        "full_duplex,sendrecv,true,true",
        "full_duplex,sendonly,true,false",
        "full_duplex,recvonly,false,true",
        "full_duplex,inactive,false,false",
        "mic_only,sendrecv,false,true",
        "mic_only,sendonly,false,false",
        "mic_only,recvonly,false,true",
        "speaker_only,sendrecv,true,false",
        "speaker_only,sendonly,true,false",
        "speaker_only,recvonly,false,false",
    )
    fun `Given negotiated media when selecting devices then open only permitted paths`(
        mode: String,
        direction: String,
        capture: Boolean,
        playback: Boolean,
    ) {
        assertEquals(NativeCallAudioPaths(capture, playback), NativeCallAudioPaths.from(mode, direction, false))
        assertEquals(NativeCallAudioPaths(false, false), NativeCallAudioPaths.from(mode, direction, true))
    }
}
