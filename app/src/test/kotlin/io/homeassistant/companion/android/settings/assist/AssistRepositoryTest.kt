package io.homeassistant.companion.android.settings.assist

import android.content.Context
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AssistRepositoryTest {

    private val context: Context = mockk(relaxed = true)
    private val prefsRepository: PrefsRepository = mockk(relaxed = true)
    private lateinit var repository: AssistRepositoryImpl

    private val testModels = listOf(
        MicroWakeWordModelConfig(
            wakeWord = "Okay Nabu",
            author = "test",
            website = "https://test.com",
            model = "okay_nabu.tflite",
            trainedLanguages = listOf("en"),
            version = 1,
            micro = MicroWakeWordModelConfig.MicroFrontendConfig(
                probabilityCutoff = 0.5f,
                featureStepSize = 10,
                slidingWindowSize = 20,
            ),
        ),
        MicroWakeWordModelConfig(
            wakeWord = "Hey Jarvis",
            author = "test",
            website = "https://test.com",
            model = "hey_jarvis.tflite",
            trainedLanguages = listOf("en"),
            version = 1,
            micro = MicroWakeWordModelConfig.MicroFrontendConfig(
                probabilityCutoff = 0.5f,
                featureStepSize = 10,
                slidingWindowSize = 20,
            ),
        ),
    )

    @BeforeEach
    fun setUp() {
        mockkObject(MicroWakeWordModelConfig.Companion)
        mockkObject(AssistVoiceInteractionService.Companion)

        coEvery { MicroWakeWordModelConfig.loadAvailableModels(any()) } returns testModels
        every { AssistVoiceInteractionService.startListening(any()) } just Runs
        every { AssistVoiceInteractionService.stopListening(any()) } just Runs

        repository = AssistRepositoryImpl(context, prefsRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    inner class GetAvailableModelsTest {

        @Test
        fun `Given models available when getAvailableModels then return all models`() = runTest {
            val result = repository.getAvailableModels()

            assertEquals(2, result.size)
            assertEquals("Okay Nabu", result[0].wakeWord)
            assertEquals("Hey Jarvis", result[1].wakeWord)
        }

        @Test
        fun `Given models already loaded when getAvailableModels called twice then load only once`() = runTest {
            repository.getAvailableModels()
            repository.getAvailableModels()

            coVerify(exactly = 1) { MicroWakeWordModelConfig.loadAvailableModels(any()) }
        }
    }

    @Nested
    inner class IsWakeWordEnabledTest {

        @Test
        fun `Given wake word enabled when isWakeWordEnabled then return true`() = runTest {
            coEvery { prefsRepository.isWakeWordEnabled() } returns true

            val result = repository.isWakeWordEnabled()

            assertTrue(result)
        }

        @Test
        fun `Given wake word disabled when isWakeWordEnabled then return false`() = runTest {
            coEvery { prefsRepository.isWakeWordEnabled() } returns false

            val result = repository.isWakeWordEnabled()

            assertFalse(result)
        }
    }

    @Nested
    inner class SetWakeWordEnabledTest {

        @Test
        fun `Given enabled true when setWakeWordEnabled then save preference and start service`() = runTest {
            coEvery { prefsRepository.setWakeWordEnabled(any()) } just Runs

            repository.setWakeWordEnabled(true)

            coVerify { prefsRepository.setWakeWordEnabled(true) }
            coVerify { AssistVoiceInteractionService.startListening(context) }
            coVerify(exactly = 0) { AssistVoiceInteractionService.stopListening(any()) }
        }

        @Test
        fun `Given enabled false when setWakeWordEnabled then save preference and stop service`() = runTest {
            coEvery { prefsRepository.setWakeWordEnabled(any()) } just Runs

            repository.setWakeWordEnabled(false)

            coVerify { prefsRepository.setWakeWordEnabled(false) }
            coVerify { AssistVoiceInteractionService.stopListening(context) }
            coVerify(exactly = 0) { AssistVoiceInteractionService.startListening(any()) }
        }
    }

    @Nested
    inner class GetSelectedWakeWordTest {

        @Test
        fun `Given wake word selected when getSelectedWakeWord then return selected wake word`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns "Okay Nabu"

            val result = repository.getSelectedWakeWord()

            assertEquals("Okay Nabu", result)
        }

        @Test
        fun `Given no wake word selected when getSelectedWakeWord then return null`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns null

            val result = repository.getSelectedWakeWord()

            assertNull(result)
        }
    }

    @Nested
    inner class SetSelectedWakeWordTest {

        @Test
        fun `Given wake word changed and enabled when setSelectedWakeWord then save and restart service`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns "Okay Nabu"
            coEvery { prefsRepository.isWakeWordEnabled() } returns true
            coEvery { prefsRepository.setSelectedWakeWord(any()) } just Runs

            repository.setSelectedWakeWord("Hey Jarvis")

            coVerify { prefsRepository.setSelectedWakeWord("Hey Jarvis") }
            coVerify { AssistVoiceInteractionService.startListening(context) }
        }

        @Test
        fun `Given wake word changed but disabled when setSelectedWakeWord then save without restart`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns "Okay Nabu"
            coEvery { prefsRepository.isWakeWordEnabled() } returns false
            coEvery { prefsRepository.setSelectedWakeWord(any()) } just Runs

            repository.setSelectedWakeWord("Hey Jarvis")

            coVerify { prefsRepository.setSelectedWakeWord("Hey Jarvis") }
            coVerify(exactly = 0) { AssistVoiceInteractionService.startListening(any()) }
        }

        @Test
        fun `Given same wake word when setSelectedWakeWord then save without restart`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns "Okay Nabu"
            coEvery { prefsRepository.isWakeWordEnabled() } returns true
            coEvery { prefsRepository.setSelectedWakeWord(any()) } just Runs

            repository.setSelectedWakeWord("Okay Nabu")

            coVerify { prefsRepository.setSelectedWakeWord("Okay Nabu") }
            coVerify(exactly = 0) { AssistVoiceInteractionService.startListening(any()) }
        }
    }
}
