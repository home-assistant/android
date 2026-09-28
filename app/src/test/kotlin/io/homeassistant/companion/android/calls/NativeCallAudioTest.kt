package io.homeassistant.companion.android.calls

import android.media.AudioRecord
import android.media.AudioTrack
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.call.NativeCallRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class NativeCallAudioTest {
    @After fun tearDown() {
        unmockkAll()
    }

    @Test fun `Given playback initialization failure then close the socket and allow another session`() = checkFailure(false)

    @Test fun `Given capture initialization failure then release playback and allow another session`() = checkFailure(true)

    private fun checkFailure(failCapture: Boolean) = runTest {
        val repository = mockk<NativeCallRepository>()
        val socket = mockk<WebSocket>(relaxed = true)
        val track = mockk<AudioTrack>(relaxed = true)
        val trackBuilder = mockk<AudioTrack.Builder>()
        val recordBuilder = mockk<AudioRecord.Builder>()
        mockkStatic(AudioTrack::class, AudioRecord::class)
        mockkConstructor(AudioTrack.Builder::class, AudioRecord.Builder::class)
        every { AudioTrack.getMinBufferSize(any(), any(), any()) } returns 1024
        every { AudioRecord.getMinBufferSize(any(), any(), any()) } returns 1024
        every { anyConstructed<AudioTrack.Builder>().setAudioAttributes(any()) } returns trackBuilder
        every { trackBuilder.setAudioFormat(any()) } returns trackBuilder
        every { trackBuilder.setTransferMode(any()) } returns trackBuilder
        every { trackBuilder.setBufferSizeInBytes(any()) } returns trackBuilder
        if (failCapture) {
            every { trackBuilder.build() } returns track
        } else {
            every { trackBuilder.build() } throws UnsupportedOperationException("Playback unavailable")
        }
        every { track.state } returns AudioTrack.STATE_INITIALIZED
        every { track.playState } returns AudioTrack.PLAYSTATE_PLAYING
        every { anyConstructed<AudioRecord.Builder>().setAudioSource(any()) } returns recordBuilder
        every { recordBuilder.setAudioFormat(any()) } returns recordBuilder
        every { recordBuilder.setBufferSizeInBytes(any()) } returns recordBuilder
        every { recordBuilder.build() } throws UnsupportedOperationException("Capture unavailable")
        var listener: WebSocketListener? = null
        val direction = "sendrecv"
        coEvery { repository.openMedia(any(), any(), any()) } coAnswers {
            listener = thirdArg()
            listener!!.onMessage(socket, """{"tx_format":"48000:s16le:1:10","rx_format":"48000:s16le:1:10","audio_direction":"$direction"}""")
            socket
        }
        every { socket.send(any<String>()) } answers {
            listener!!.onClosed(socket, 1000, "")
            true
        }
        val audio = NativeCallAudio(repository, StandardTestDispatcher(testScheduler))
        val failed = async { runCatching { audio.run(1, "/api/example/media") } }
        advanceUntilIdle()
        val error = failed.await().exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals("Native audio device could not be opened", error?.message)
        assertTrue(generateSequence(error) { it.cause }.last() is UnsupportedOperationException)
        verify(exactly = 1) { socket.cancel() }
        verify(exactly = if (failCapture) 1 else 0) { track.release() }
        val nextTrack = mockk<AudioTrack>(relaxed = true)
        val nextRecord = mockk<AudioRecord>(relaxed = true)
        every { nextTrack.state } returns AudioTrack.STATE_INITIALIZED
        every { nextTrack.playState } returns AudioTrack.PLAYSTATE_PLAYING
        every { nextRecord.state } returns AudioRecord.STATE_INITIALIZED
        every { nextRecord.recordingState } returns AudioRecord.RECORDSTATE_RECORDING
        every { trackBuilder.build() } returns nextTrack
        every { recordBuilder.build() } returns nextRecord
        val retry = async { audio.run(1, "/api/example/next-media") }
        advanceUntilIdle()
        retry.await()
        verify(exactly = 2) { socket.cancel() }
        verify(exactly = if (failCapture) 1 else 0) { track.release() }
        verify(exactly = 1) { nextTrack.release() }
        verify(exactly = 1) { nextRecord.release() }
    }
}
