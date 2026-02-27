package io.homeassistant.companion.android.common.assist

import android.media.AudioManager
import androidx.media.AudioManagerCompat
import app.cash.turbine.test
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultAssistAudioStrategyTest {

    private lateinit var voiceAudioRecorder: VoiceAudioRecorder
    private lateinit var audioManager: AudioManager
    private lateinit var strategy: DefaultAssistAudioStrategy
    private val audioDataFlow = MutableSharedFlow<ShortArray>()

    @BeforeEach
    fun setUp() {
        voiceAudioRecorder = mockk {
            coEvery { audioData() } returns audioDataFlow
        }
        audioManager = mockk(relaxed = true)

        mockkStatic(AudioManagerCompat::class)
        every { AudioManagerCompat.requestAudioFocus(any(), any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        every { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        strategy = DefaultAssistAudioStrategy(voiceAudioRecorder, audioManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(AudioManagerCompat::class)
    }

    @Test
    fun `Given strategy When audioData accessed Then returns flow from VoiceAudioRecorder`() = runTest {
        assertNotNull(strategy.audioData())
    }

    @Test
    fun `Given strategy When audioData collected Then emits samples from VoiceAudioRecorder`() = runTest {
        val samples = shortArrayOf(1, 2, 3)

        strategy.audioData().test {
            audioDataFlow.emit(samples)
            assertEquals(samples, awaitItem())
        }
    }

    @Test
    fun `Given strategy When requestFocus called Then requests audio focus`() {
        strategy.requestFocus()
        verify { AudioManagerCompat.requestAudioFocus(audioManager, any()) }
    }

    @Test
    fun `Given strategy When abandonFocus called Then abandons audio focus`() {
        // Must call requestFocus first to create the focus request
        strategy.requestFocus()

        strategy.abandonFocus()

        verify { AudioManagerCompat.abandonAudioFocusRequest(audioManager, any()) }
    }

    @Test
    fun `Given no audio manager When requestFocus called Then does not request focus`() {
        val strategyNoAudio = DefaultAssistAudioStrategy(voiceAudioRecorder, audioManager = null)

        strategyNoAudio.requestFocus()
        verify(exactly = 0) { AudioManagerCompat.requestAudioFocus(any(), any()) }
    }

    @Test
    fun `Given no audio manager When abandonFocus called Then does nothing`() {
        val strategyNoAudio = DefaultAssistAudioStrategy(voiceAudioRecorder, audioManager = null)

        // Should not throw
        strategyNoAudio.abandonFocus()
        verify(exactly = 0) { AudioManagerCompat.abandonAudioFocusRequest(any(), any()) }
    }

    @Test
    fun `Given strategy When wakeWordDetected collected Then never emits`() = runTest {
        assertTrue(strategy.wakeWordDetected.toList().isEmpty())
    }
}
