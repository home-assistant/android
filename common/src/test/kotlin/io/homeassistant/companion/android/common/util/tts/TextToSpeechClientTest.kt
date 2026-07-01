package io.homeassistant.companion.android.common.util.tts

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class TextToSpeechClientTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var textToSpeechEngine: TextToSpeechEngine
    private lateinit var client: TextToSpeechClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        textToSpeechEngine = mockk(relaxed = true)
        client = TextToSpeechClient(context, textToSpeechEngine)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @Config(sdk = [26])
    fun `Given focus granted when speakText on SDK 26 then plays utterance`() = runTest {
        coEvery { textToSpeechEngine.initialize() } returns Result.success(Unit)
        coEvery { textToSpeechEngine.play(any()) } returns Result.success(Unit)

        client.speakText(mapOf(TextToSpeechData.TTS_TEXT to "Hello World"))
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        // Verifies the engine played the text (plus the initial empty space utterance)
        coVerify(exactly = 1) { textToSpeechEngine.initialize() }
        coVerify(atLeast = 1) { textToSpeechEngine.play(any()) }
    }

    @Test
    @Config(sdk = [23])
    fun `Given focus granted when speakText on SDK 23 then plays utterance`() = runTest {
        coEvery { textToSpeechEngine.initialize() } returns Result.success(Unit)
        coEvery { textToSpeechEngine.play(any()) } returns Result.success(Unit)

        client.speakText(mapOf(TextToSpeechData.TTS_TEXT to "Hello World"))
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { textToSpeechEngine.initialize() }
        coVerify(atLeast = 1) { textToSpeechEngine.play(any()) }
    }

    @Test
    @Config(sdk = [26])
    fun `Given focus denied when speakText then shows error toast and aborts`() = runTest {
        val shadowAudioManager = shadowOf(audioManager)
        shadowAudioManager.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        client.speakText(mapOf(TextToSpeechData.TTS_TEXT to "Hello World"))
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { textToSpeechEngine.initialize() }
        coVerify(exactly = 0) { textToSpeechEngine.play(any()) }

        val latestToastText = ShadowToast.getTextOfLatestToast()
        assertEquals("Could not obtain audio focus for text to speech.", latestToastText)
    }

    @Test
    @Config(sdk = [26])
    fun `Given engine init fails when speakText then shows error toast`() = runTest {
        coEvery { textToSpeechEngine.initialize() } returns Result.failure(Exception("Init error"))

        client.speakText(mapOf(TextToSpeechData.TTS_TEXT to "Hello World"))
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { textToSpeechEngine.initialize() }
        coVerify(exactly = 0) { textToSpeechEngine.play(any()) }

        val latestToastText = ShadowToast.getTextOfLatestToast()
        assertEquals("Failed to initialize a text to speech engine.", latestToastText)
    }

    @Test
    @Config(sdk = [26])
    fun `Given playing when stopTTS called then stops and clears queue`() = runTest {
        coEvery { textToSpeechEngine.initialize() } returns Result.success(Unit)

        client.speakText(mapOf(TextToSpeechData.TTS_TEXT to "Hello World"))
        client.stopTTS()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { textToSpeechEngine.release() }
    }
}
