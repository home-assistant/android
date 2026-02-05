package io.homeassistant.companion.android.settings.assist

import android.content.Context
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.util.microWakeWordModelConfigs
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

    @BeforeEach
    fun setUp() {
        mockkObject(MicroWakeWordModelConfig.Companion)
        mockkObject(AssistVoiceInteractionService.Companion)

        coEvery { MicroWakeWordModelConfig.loadAvailableModels(any()) } returns microWakeWordModelConfigs
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
        fun `Given enabled true when setWakeWordEnabled then save preference and start listening`() = runTest {
            coEvery { prefsRepository.setWakeWordEnabled(any()) } just Runs

            repository.setWakeWordEnabled(true)

            coVerify { prefsRepository.setWakeWordEnabled(true) }
            coVerify { AssistVoiceInteractionService.startListening(context) }
            coVerify(exactly = 0) { AssistVoiceInteractionService.stopListening(any()) }
        }

        @Test
        fun `Given enabled false when setWakeWordEnabled then save preference and stop listening`() = runTest {
            coEvery { prefsRepository.setWakeWordEnabled(any()) } just Runs

            repository.setWakeWordEnabled(false)

            coVerify { prefsRepository.setWakeWordEnabled(false) }
            coVerify { AssistVoiceInteractionService.stopListening(context) }
            coVerify(exactly = 0) { AssistVoiceInteractionService.startListening(any()) }
        }
    }

    @Nested
    inner class GetSelectedWakeWordModelTest {

        @Test
        fun `Given wake word selected when getSelectedWakeWordModel then return selected model`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns "Okay Nabu"

            val result = repository.getSelectedWakeWordModel()

            assertEquals(microWakeWordModelConfigs[0], result)
        }

        @Test
        fun `Given no wake word selected when getSelectedWakeWordModel then return null`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns null

            val result = repository.getSelectedWakeWordModel()

            assertNull(result)
        }

        @Test
        fun `Given unknown wake word selected when getSelectedWakeWordModel then return null`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns "Unknown Model"

            val result = repository.getSelectedWakeWordModel()

            assertNull(result)
        }
    }

    @Nested
    inner class SetSelectedWakeWordModelTest {

        @Test
        fun `Given model changed and enabled when setSelectedWakeWordModel then save and restart service`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns "Okay Nabu"
            coEvery { prefsRepository.isWakeWordEnabled() } returns true
            coEvery { prefsRepository.setSelectedWakeWord(any()) } just Runs

            repository.setSelectedWakeWordModel(microWakeWordModelConfigs[1])

            coVerify { prefsRepository.setSelectedWakeWord("Hey Jarvis") }
            coVerify { AssistVoiceInteractionService.startListening(context) }
        }

        @Test
        fun `Given model changed but disabled when setSelectedWakeWordModel then save without restart`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns "Okay Nabu"
            coEvery { prefsRepository.isWakeWordEnabled() } returns false
            coEvery { prefsRepository.setSelectedWakeWord(any()) } just Runs

            repository.setSelectedWakeWordModel(microWakeWordModelConfigs[1])

            coVerify { prefsRepository.setSelectedWakeWord("Hey Jarvis") }
            coVerify(exactly = 0) { AssistVoiceInteractionService.startListening(any()) }
        }

        @Test
        fun `Given same model when setSelectedWakeWordModel then save without restart`() = runTest {
            coEvery { prefsRepository.getSelectedWakeWord() } returns "Okay Nabu"
            coEvery { prefsRepository.isWakeWordEnabled() } returns true
            coEvery { prefsRepository.setSelectedWakeWord(any()) } just Runs

            repository.setSelectedWakeWordModel(microWakeWordModelConfigs[0])

            coVerify { prefsRepository.setSelectedWakeWord("Okay Nabu") }
            coVerify(exactly = 0) { AssistVoiceInteractionService.startListening(any()) }
        }
    }
}
