package io.homeassistant.companion.android.common.util

import android.media.AudioManager
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.cash.turbine.test
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ConsoleLogExtension::class, MainDispatcherJUnit5Extension::class)
class AudioUrlPlayerTest {
    private lateinit var audioManager: AudioManager
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: AudioUrlPlayer

    @BeforeEach
    fun setUp() {
        audioManager = mockk(relaxed = true)
        exoPlayer = mockk(relaxed = true)

        player = AudioUrlPlayer(audioManager) { block ->
            exoPlayer.apply { block() }
            exoPlayer
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given volume equal to 0 when playAudio then emits nothing and no audio is played`() = runTest {
        every { audioManager.getStreamVolume(any()) } returns 0

        val states = player.playAudio("").toList()

        assertTrue(states.isEmpty())
        verify(exactly = 0) { exoPlayer.play() }
    }

    @Test
    fun `Given volume above 0 and url when playAudio then emits READY, PLAYING, STOP_PLAYING`() = runTest {
        val listenerSlot = slot<Player.Listener>()

        every { audioManager.getStreamVolume(any()) } returns 1
        every { exoPlayer.addListener(capture(listenerSlot)) } just Runs
        every { exoPlayer.prepare() } answers {
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_READY)
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
        }

        player.playAudio("test_url").test {
            assertEquals(PlaybackState.READY, awaitItem())
            assertEquals(PlaybackState.PLAYING, awaitItem())
            assertEquals(PlaybackState.STOP_PLAYING, awaitItem())
            expectNoEvents()
        }

        verifyOrder {
            exoPlayer.setAudioAttributes(
                eq(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .setUsage(C.USAGE_ASSISTANT)
                        .build(),
                ),
                eq(false),
            )
            exoPlayer.addListener(any())
            exoPlayer.setMediaItem(any())
            exoPlayer.prepare()
            exoPlayer.play()
            exoPlayer.release()
        }
        confirmVerified(exoPlayer)
    }

    @Test
    fun `Given player error when playAudio then releases player without emitting states`() = runTest {
        val listenerSlot = slot<Player.Listener>()

        every { audioManager.getStreamVolume(any()) } returns 1
        every { exoPlayer.addListener(capture(listenerSlot)) } just Runs
        every { exoPlayer.prepare() } answers {
            listenerSlot.captured.onPlayerError(
                PlaybackException("Test error", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
            )
        }

        val states = player.playAudio("test_url").toList()

        assertTrue(states.isEmpty())
        verify(exactly = 0) { exoPlayer.play() }
        verify { exoPlayer.release() }
    }

    @Test
    fun `Given stream ends with EOF when playAudio then emits STOP_PLAYING`() = runTest {
        val listenerSlot = slot<Player.Listener>()

        every { audioManager.getStreamVolume(any()) } returns 1
        every { exoPlayer.addListener(capture(listenerSlot)) } just Runs
        every { exoPlayer.prepare() } answers {
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_READY)
            listenerSlot.captured.onPlayerError(
                PlaybackException(
                    "Stream ended",
                    java.io.EOFException("End of stream"),
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                ),
            )
        }

        val states = player.playAudio("test_url").toList()

        assertEquals(listOf(PlaybackState.READY, PlaybackState.PLAYING, PlaybackState.STOP_PLAYING), states)
    }

    @Test
    fun `Given null audioManager when playAudio then does not play audio nor create an instance of ExoPlayer`() = runTest {
        val playerWithNullAudioManager = AudioUrlPlayer(null) { block ->
            exoPlayer.apply { block() }
            exoPlayer
        }

        val states = playerWithNullAudioManager.playAudio("test_url").toList()

        assertEquals(emptyList<PlaybackState>(), states)
        confirmVerified(exoPlayer)
    }

    @Test
    fun `Given isAssistant false when playAudio then uses media audio attributes`() = runTest {
        val listenerSlot = slot<Player.Listener>()

        every { audioManager.getStreamVolume(any()) } returns 1
        every { exoPlayer.addListener(capture(listenerSlot)) } just Runs
        every { exoPlayer.prepare() } answers {
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_READY)
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
        }

        player.playAudio("test_url", isAssistant = false).test {
            assertEquals(PlaybackState.READY, awaitItem())
            assertEquals(PlaybackState.PLAYING, awaitItem())
            assertEquals(PlaybackState.STOP_PLAYING, awaitItem())
            expectNoEvents()
        }

        verify {
            exoPlayer.setAudioAttributes(
                eq(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                ),
                eq(false),
            )
        }
    }

    @Test
    fun `Given audio focus request fails when playAudio then still plays audio`() = runTest {
        val listenerSlot = slot<Player.Listener>()

        mockkStatic(AudioManagerCompat::class)
        every { audioManager.getStreamVolume(any()) } returns 1
        every { AudioManagerCompat.requestAudioFocus(audioManager, any()) } throws SecurityException("Focus denied")
        every { exoPlayer.addListener(capture(listenerSlot)) } just Runs
        every { exoPlayer.prepare() } answers {
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_READY)
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
        }

        player.playAudio("test_url").test {
            assertEquals(PlaybackState.READY, awaitItem())
            assertEquals(PlaybackState.PLAYING, awaitItem())
            assertEquals(PlaybackState.STOP_PLAYING, awaitItem())
            expectNoEvents()
        }

        verify { exoPlayer.play() }
        verify { AudioManagerCompat.requestAudioFocus(audioManager, any()) }
        confirmVerified(AudioManagerCompat::class)
    }

    @Test
    fun `Given successful playback when flow completes then player is released and focus abandoned with same request`() = runTest {
        val listenerSlot = slot<Player.Listener>()
        val focusRequestSlot = slot<AudioFocusRequestCompat>()

        mockkStatic(AudioManagerCompat::class)
        every { AudioManagerCompat.requestAudioFocus(any(), capture(focusRequestSlot)) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { audioManager.getStreamVolume(any()) } returns 1
        every { exoPlayer.addListener(capture(listenerSlot)) } just Runs
        every { exoPlayer.prepare() } answers {
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_READY)
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
        }

        player.playAudio("test_url").test {
            awaitItem() // READY
            awaitItem() // PLAYING
            awaitItem() // STOP_PLAYING
            expectNoEvents()
        }

        verify { exoPlayer.release() }
        verify { AudioManagerCompat.requestAudioFocus(audioManager, any()) }
        verify { AudioManagerCompat.abandonAudioFocusRequest(audioManager, eq(focusRequestSlot.captured)) }
    }

    @Test
    fun `Given getStreamVolume throws RuntimeException when playAudio then still plays audio`() = runTest {
        val listenerSlot = slot<Player.Listener>()

        every { audioManager.getStreamVolume(any()) } throws RuntimeException("Permission denied")
        every { exoPlayer.addListener(capture(listenerSlot)) } just Runs
        every { exoPlayer.prepare() } answers {
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_READY)
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)
        }

        player.playAudio("test_url").test {
            assertEquals(PlaybackState.READY, awaitItem())
            assertEquals(PlaybackState.PLAYING, awaitItem())
            assertEquals(PlaybackState.STOP_PLAYING, awaitItem())
            expectNoEvents()
        }

        verify { exoPlayer.play() }
    }
}
