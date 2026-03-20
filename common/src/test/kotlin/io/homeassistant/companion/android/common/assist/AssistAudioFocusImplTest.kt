package io.homeassistant.companion.android.common.assist

import android.media.AudioManager
import androidx.media.AudioManagerCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AssistAudioFocusImplTest {

    private lateinit var audioManager: AudioManager

    @BeforeEach
    fun setUp() {
        audioManager = mockk(relaxed = true)

        mockkStatic(AudioManagerCompat::class)
        every { AudioManagerCompat.requestAudioFocus(any(), any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(AudioManagerCompat::class)
    }

    @Test
    fun `Given audio manager When requestFocus called Then requests audio focus`() {
        val helper = AssistAudioFocusImpl(audioManager)

        helper.requestFocus()

        verify { AudioManagerCompat.requestAudioFocus(audioManager, any()) }
    }

    @Test
    fun `Given audio manager When abandonFocus called after requestFocus Then abandons audio focus`() {
        val helper = AssistAudioFocusImpl(audioManager)

        helper.requestFocus()
        helper.abandonFocus()

        verify { AudioManagerCompat.abandonAudioFocusRequest(audioManager, any()) }
    }

    @Test
    fun `Given no audio manager When requestFocus called Then does not request focus`() {
        val helper = AssistAudioFocusImpl(audioManager = null)

        helper.requestFocus()

        verify(exactly = 0) { AudioManagerCompat.requestAudioFocus(any(), any()) }
    }

    @Test
    fun `Given no audio manager When abandonFocus called Then does nothing`() {
        val helper = AssistAudioFocusImpl(audioManager = null)

        helper.abandonFocus()

        verify(exactly = 0) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
    }

    @Test
    fun `Given audio manager When abandonFocus called without requestFocus Then does nothing`() {
        val helper = AssistAudioFocusImpl(audioManager)

        helper.abandonFocus()

        verify(exactly = 0) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
    }
}
