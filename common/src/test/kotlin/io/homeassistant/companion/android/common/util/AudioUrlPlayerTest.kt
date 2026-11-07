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
import java.util.stream.Stream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

private const val ANDROID_17_SDK = 37
private const val PRE_ANDROID_17_SDK = 36

@ExtendWith(MainDispatcherJUnit5Extension::class)
class AudioUrlPlayerTest {

    companion object {
        /**
         * Cases for testing volume stream + isAssistant combinations: on Android 17 this will
         * check STREAM_MUSIC or STREAM_ASSISTANT, on older Android versions this will always be
         * STREAM_MUSIC.
         */
        @JvmStatic
        fun volumeStreamCases(): Stream<Arguments> = Stream.of(
            Arguments.of(ANDROID_17_SDK, true, AudioManager.STREAM_ASSISTANT),
            Arguments.of(ANDROID_17_SDK, false, AudioManager.STREAM_MUSIC),
            Arguments.of(PRE_ANDROID_17_SDK, true, AudioManager.STREAM_MUSIC),
            Arguments.of(PRE_ANDROID_17_SDK, false, AudioManager.STREAM_MUSIC),
        )
    }

    private lateinit var audioManager: AudioManager
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: AudioUrlPlayer

    @BeforeEach
    fun setUp() {
        audioManager = mockk(relaxed = true)
        exoPlayer = mockk(relaxed = true)
        SdkVersion.sdkInt = PRE_ANDROID_17_SDK

        player = AudioUrlPlayer(audioManager) { block ->
            exoPlayer.apply { block() }
            exoPlayer
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        SdkVersion.resetSdkInt()
    }

    @ParameterizedTest(name = "SDK {0}, isAssistant {1} -> stream {2}")
    @MethodSource("volumeStreamCases")
    fun `Given volume equal to 0 when playAudio then checks correct stream, emits nothing and no audio is played`(
        sdk: Int,
        isAssistant: Boolean,
        stream: Int,
    ) = runTest {
        SdkVersion.sdkInt = sdk
        val otherStream = if (stream == AudioManager.STREAM_MUSIC) AudioManager.STREAM_ASSISTANT else AudioManager.STREAM_MUSIC
        every { audioManager.getStreamVolume(stream) } returns 0
        every { audioManager.getStreamVolume(otherStream) } returns 1

        val states = player.playAudio(url = "", isAssistant = isAssistant).toList()

        verify(exactly = 1) { audioManager.getStreamVolume(stream) }
        verify(exactly = 0) { audioManager.getStreamVolume(otherStream) }

        assertTrue(states.isEmpty())
        verify(exactly = 0) { exoPlayer.play() }
    }

    @Test
    fun `Given Android 17 and media volume equal to 0 but assistant volume above 0 when playing assistant audio then playback starts`() = runTest {
        val listenerSlot = slot<Player.Listener>()

        SdkVersion.sdkInt = ANDROID_17_SDK
        every { audioManager.getStreamVolume(AudioManager.STREAM_ASSISTANT) } returns 1
        every { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } returns 0
        every { exoPlayer.addListener(capture(listenerSlot)) } just Runs
        every { exoPlayer.prepare() } answers {
            listenerSlot.captured.onPlaybackStateChanged(Player.STATE_READY)
        }

        player.playAudio(url = "test_url", isAssistant = true).test {
            assertEquals(PlaybackState.READY, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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
        val abandonRequestSlot = slot<AudioFocusRequestCompat>()

        mockkStatic(AudioManagerCompat::class)
        every { AudioManagerCompat.requestAudioFocus(any(), capture(focusRequestSlot)) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { AudioManagerCompat.abandonAudioFocusRequest(any(), capture(abandonRequestSlot)) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
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
        verify { AudioManagerCompat.abandonAudioFocusRequest(audioManager, any()) }
        // Use reference identity rather than eq(): in androidx.media 1.8.0,
        // AudioFocusRequestCompat.hashCode() dereferences the platform AudioAttributes,
        // which is null under plain JUnit and NPEs inside MockK's EqMatcher.
        assertSame(focusRequestSlot.captured, abandonRequestSlot.captured)
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
