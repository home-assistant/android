package io.homeassistant.companion.android.common.util

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.M])
class AudioUrlPlayerTest {
    private lateinit var audioManager: AudioManager
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var player: AudioUrlPlayer

    @Before
    fun setUp() {
        audioManager = mockk(relaxed = true)
        mediaPlayer = mockk(relaxed = true)

        val mediaPlayerCreator: () -> MediaPlayer = { mediaPlayer }
        player = AudioUrlPlayer(audioManager, mediaPlayerCreator)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given volume equal to 0 when playAudio then returns false and no audio is played`() {
        every { audioManager.getStreamVolume(any()) } returns 0

        runTest {
            assertFalse(player.playAudio(""))
            verify(exactly = 0) { mediaPlayer.start() }
        }
    }

    @Test
    fun `Given no audio manager when playAudio then returns false and no audio is played`() {
        val player = AudioUrlPlayer(null)

        runTest {
            assertFalse(player.playAudio(""))
            verify(exactly = 0) { mediaPlayer.start() }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M, Build.VERSION_CODES.VANILLA_ICE_CREAM])
    fun `Given volume above 0 and url when playAudio then play audio returns true`() = runTest {
        val onCompletionListener = slot<MediaPlayer.OnCompletionListener>()
        val onPreparedListener = slot<MediaPlayer.OnPreparedListener>()

        every { audioManager.getStreamVolume(any()) } returns 1

        every { mediaPlayer.setOnCompletionListener(capture(onCompletionListener)) } just Runs
        every { mediaPlayer.setOnPreparedListener(capture(onPreparedListener)) } just Runs
        // We replicate the happy path of a media player while calling prepare async by invoking onPrepared and then onCompletion
        every { mediaPlayer.prepareAsync() } answers {
            onPreparedListener.captured.onPrepared(mediaPlayer)
            onCompletionListener.captured.onCompletion(mediaPlayer)
        }

        val result = player.playAudio("test_url")

        verifyOrder {
            mediaPlayer.setDataSource("test_url")
            mediaPlayer.prepareAsync()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.requestAudioFocus(any())
            } else {
                audioManager.requestAudioFocus(any(), any(), any())
            }
            mediaPlayer.start()
            mediaPlayer.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(any())
            } else {
                audioManager.abandonAudioFocus(any())
            }
        }

        verify { mediaPlayer.setAudioAttributes(any()) }
        verify { mediaPlayer.setOnPreparedListener(any()) }
        verify { mediaPlayer.setOnErrorListener(any()) }
        verify { mediaPlayer.setOnCompletionListener(any()) }

        assertTrue(result)
    }

    @Test
    fun `Given wrong URL and sound above 0 when playAudio then no audio is played and returns false`() = runTest {
        val onErrorCompletionListener = slot<MediaPlayer.OnErrorListener>()

        every { audioManager.getStreamVolume(any()) } returns 1

        every { mediaPlayer.setOnErrorListener(capture(onErrorCompletionListener)) } just Runs
        every { mediaPlayer.prepareAsync() } answers {
            onErrorCompletionListener.captured.onError(mediaPlayer, 0, 0)
        }

        assertFalse(player.playAudio("test_url"))
        verify(exactly = 0) { mediaPlayer.start() }
    }

    @Test
    fun `Given wrong player state and sound above 0 when playAudio then no audio is played and returns false`() = runTest {
        every { audioManager.getStreamVolume(any()) } returns 1
        every { mediaPlayer.setDataSource("test_url") } throws IllegalStateException()

        assertFalse(player.playAudio("test_url"))
        verify(exactly = 0) { mediaPlayer.start() }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M, Build.VERSION_CODES.VANILLA_ICE_CREAM])
    fun `Given a player already started when playAudio then the previous player is stopped and focus abandoned`() = runTest {
        every { audioManager.getStreamVolume(any()) } returns 1

        val onCompletionListener = slot<MediaPlayer.OnCompletionListener>()
        val onPreparedListener = slot<MediaPlayer.OnPreparedListener>()

        every { audioManager.getStreamVolume(any()) } returns 1

        every { mediaPlayer.setOnPreparedListener(capture(onPreparedListener)) } just Runs
        every { mediaPlayer.setOnCompletionListener(capture(onCompletionListener)) } just Runs

        every { mediaPlayer.prepareAsync() } answers {
            onPreparedListener.captured.onPrepared(mediaPlayer)
            onCompletionListener.captured.onCompletion(mediaPlayer)
        }

        val playerToStop = mockk<MediaPlayer>(relaxed = true)
        player.player = playerToStop
        player.focusRequest = mockk(relaxed = true)

        // create a new player
        assertTrue(player.playAudio("test_url"))

        verifyOrder {
            playerToStop.stop()
            playerToStop.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(any())
            } else {
                audioManager.abandonAudioFocus(any())
            }
            mediaPlayer.setDataSource("test_url")
            mediaPlayer.prepareAsync()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.requestAudioFocus(any())
            } else {
                audioManager.requestAudioFocus(any(), any(), any())
            }
            mediaPlayer.start()
            mediaPlayer.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(any())
            } else {
                audioManager.abandonAudioFocus(any())
            }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M, Build.VERSION_CODES.VANILLA_ICE_CREAM])
    fun `Given a URL and is assistant when playAudio then set proper audio attributes`() = runTest {
        every { audioManager.getStreamVolume(any()) } returns 1
        val audioAttributes = slot<AudioAttributes>()
        every { mediaPlayer.setAudioAttributes(capture(audioAttributes)) } just Runs

        // We don't need to play anything to set the attributes but we still need to get out of the suspendCoroutine
        every { mediaPlayer.setDataSource("test_url") } throws IllegalStateException()

        player.playAudio("test_url", true)

        with(audioAttributes.captured) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                assertEquals(AudioAttributes.USAGE_ASSISTANT, usage)
            } else {
                assertEquals(AudioAttributes.USAGE_MEDIA, usage)
            }
            assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, contentType)
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M, Build.VERSION_CODES.VANILLA_ICE_CREAM])
    fun `Given a URL and is not assistant when playAudio then set proper audio attributes`() = runTest {
        every { audioManager.getStreamVolume(any()) } returns 1
        val audioAttributes = slot<AudioAttributes>()
        every { mediaPlayer.setAudioAttributes(capture(audioAttributes)) } just Runs

        // We don't need to play anything to set the attributes but we still need to get out of the suspendCoroutine
        every { mediaPlayer.setDataSource("test_url") } throws IllegalStateException()

        player.playAudio("test_url", false)

        with(audioAttributes.captured) {
            assertEquals(AudioAttributes.USAGE_MEDIA, usage)
            assertEquals(AudioAttributes.CONTENT_TYPE_MUSIC, contentType)
        }
    }

    @Test
    fun `Given anything that happens when invoking playAudio then it does not throw IllegalStateException Already resumed`() = runTest {
        every { audioManager.getStreamVolume(any()) } returns 1

        val onCompletionListener = slot<MediaPlayer.OnCompletionListener>()
        val onErrorListener = slot<MediaPlayer.OnErrorListener>()
        every { mediaPlayer.setOnCompletionListener(capture(onCompletionListener)) } answers {
            onCompletionListener.captured.onCompletion(mediaPlayer)
        }
        every { mediaPlayer.setOnErrorListener(capture(onErrorListener)) } answers {
            onErrorListener.captured.onError(mediaPlayer, 1, 42)
        }
        every { mediaPlayer.prepareAsync() } throws IllegalStateException("dummy")

        player.playAudio("test_url", false)
    }
}
