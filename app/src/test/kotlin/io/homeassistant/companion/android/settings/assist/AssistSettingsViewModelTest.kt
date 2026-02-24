package io.homeassistant.companion.android.settings.assist

import android.content.Intent
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.util.microWakeWordModelConfigs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
class AssistSettingsViewModelTest {

    private val defaultAssistantManager: DefaultAssistantManager = mockk(relaxed = true)
    private val assistConfigManager: AssistConfigManager = mockk(relaxed = true)

    private lateinit var viewModel: AssistSettingsViewModel

    @BeforeEach
    fun setUp() {
        coEvery { assistConfigManager.getAvailableModels() } returns microWakeWordModelConfigs
        coEvery { assistConfigManager.isWakeWordEnabled() } returns false
        coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]
        every { defaultAssistantManager.isDefaultAssistant() } returns true
    }

    private fun createViewModel(): AssistSettingsViewModel {
        return AssistSettingsViewModel(
            defaultAssistantManager = defaultAssistantManager,
            assistConfigManager = assistConfigManager,
        )
    }

    @Nested
    inner class InitializationTest {

        @Test
        fun `Given default assistant when initialized then load state correctly`() = runTest {
            every { defaultAssistantManager.isDefaultAssistant() } returns true
            coEvery { assistConfigManager.isWakeWordEnabled() } returns true
            coEvery { assistConfigManager.getSelectedWakeWordModel() } returns microWakeWordModelConfigs[0]

            viewModel = createViewModel()
            runCurrent()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.isDefaultAssistant)
            assertTrue(state.isWakeWordEnabled)
            assertEquals(microWakeWordModelConfigs[0], state.selectedWakeWordModel)
            assertEquals(microWakeWordModelConfigs, state.availableModels)
        }

        @Test
        fun `Given not default assistant and wake word enabled when initialized then disable wake word`() = runTest {
            every { defaultAssistantManager.isDefaultAssistant() } returns false
            coEvery { assistConfigManager.isWakeWordEnabled() } returns true

            viewModel = createViewModel()
            runCurrent()

            val state = viewModel.uiState.value
            assertFalse(state.isDefaultAssistant)
            assertFalse(state.isWakeWordEnabled)
            coVerify { assistConfigManager.setWakeWordEnabled(false) }
        }

        @Test
        fun `Given not default assistant and wake word disabled when initialized then keep disabled`() = runTest {
            every { defaultAssistantManager.isDefaultAssistant() } returns false
            coEvery { assistConfigManager.isWakeWordEnabled() } returns false

            viewModel = createViewModel()
            runCurrent()

            val state = viewModel.uiState.value
            assertFalse(state.isDefaultAssistant)
            assertFalse(state.isWakeWordEnabled)
            coVerify(exactly = 0) { assistConfigManager.setWakeWordEnabled(any()) }
        }

        @Test
        fun `Given no selected model when initialized then use first available model`() = runTest {
            coEvery { assistConfigManager.getSelectedWakeWordModel() } returns null

            viewModel = createViewModel()
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(microWakeWordModelConfigs[0], state.selectedWakeWordModel)
        }

        @Test
        fun `Given no selected model and no available models when initialized then selectedModel is null`() = runTest {
            coEvery { assistConfigManager.getSelectedWakeWordModel() } returns null
            coEvery { assistConfigManager.getAvailableModels() } returns emptyList()

            viewModel = createViewModel()
            runCurrent()

            val state = viewModel.uiState.value
            assertNull(state.selectedWakeWordModel)
        }
    }

    @Nested
    inner class RefreshDefaultAssistantStatusTest {

        @Test
        fun `Given became default assistant when refresh then update state`() = runTest {
            every { defaultAssistantManager.isDefaultAssistant() } returns false
            viewModel = createViewModel()
            runCurrent()

            every { defaultAssistantManager.isDefaultAssistant() } returns true
            viewModel.refreshDefaultAssistantStatus()
            runCurrent()

            assertTrue(viewModel.uiState.value.isDefaultAssistant)
        }

        @Test
        fun `Given no longer default assistant with wake word enabled when refresh then disable wake word`() = runTest {
            every { defaultAssistantManager.isDefaultAssistant() } returns true
            coEvery { assistConfigManager.isWakeWordEnabled() } returns true
            viewModel = createViewModel()
            runCurrent()

            every { defaultAssistantManager.isDefaultAssistant() } returns false
            viewModel.refreshDefaultAssistantStatus()
            runCurrent()

            assertFalse(viewModel.uiState.value.isDefaultAssistant)
            assertFalse(viewModel.uiState.value.isWakeWordEnabled)
            coVerify { assistConfigManager.setWakeWordEnabled(false) }
        }
    }

    @Nested
    inner class GetSetDefaultAssistantIntentTest {

        @Test
        fun `Given intent available when getSetDefaultAssistantIntent then return intent`() = runTest {
            val expectedIntent = mockk<Intent>()
            every { defaultAssistantManager.getSetDefaultAssistantIntent() } returns expectedIntent
            viewModel = createViewModel()
            runCurrent()

            val result = viewModel.getSetDefaultAssistantIntent()

            assertEquals(expectedIntent, result)
            verify { defaultAssistantManager.getSetDefaultAssistantIntent() }
        }
    }

    @Nested
    inner class ToggleWakeWordTest {

        @Test
        fun `Given wake word disabled when toggle enabled then enable and save`() = runTest {
            viewModel = createViewModel()
            runCurrent()

            viewModel.onToggleWakeWord(true)
            runCurrent()

            assertTrue(viewModel.uiState.value.isWakeWordEnabled)
            coVerify { assistConfigManager.setWakeWordEnabled(true) }
        }

        @Test
        fun `Given wake word enabled when toggle disabled then disable and save`() = runTest {
            coEvery { assistConfigManager.isWakeWordEnabled() } returns true
            viewModel = createViewModel()
            runCurrent()

            viewModel.onToggleWakeWord(false)
            runCurrent()

            assertFalse(viewModel.uiState.value.isWakeWordEnabled)
            coVerify { assistConfigManager.setWakeWordEnabled(false) }
        }
    }

    @Nested
    inner class SelectWakeWordModelTest {

        @Test
        fun `Given wake word selected when onSelectWakeWordModel then update state and save`() = runTest {
            viewModel = createViewModel()
            runCurrent()

            viewModel.onSelectWakeWordModel(microWakeWordModelConfigs[1])
            runCurrent()

            assertEquals(microWakeWordModelConfigs[1], viewModel.uiState.value.selectedWakeWordModel)
            coVerify { assistConfigManager.setSelectedWakeWordModel(microWakeWordModelConfigs[1]) }
        }
    }

    @Nested
    inner class TestWakeWordTest {

        @Test
        fun `Given not testing when setTestingWakeWord true then update state`() = runTest {
            viewModel = createViewModel()
            runCurrent()

            viewModel.setTestingWakeWord(true)

            assertTrue(viewModel.uiState.value.isTestingWakeWord)
            assertFalse(viewModel.uiState.value.wakeWordDetected)
        }

        @Test
        fun `Given testing when setTestingWakeWord false then update state`() = runTest {
            viewModel = createViewModel()
            runCurrent()

            viewModel.setTestingWakeWord(true)
            viewModel.setTestingWakeWord(false)

            assertFalse(viewModel.uiState.value.isTestingWakeWord)
        }

        @Test
        fun `Given testing when onWakeWordDetected then show detected and reset after debounce`() = runTest {
            viewModel = createViewModel()
            runCurrent()

            viewModel.setTestingWakeWord(true)
            viewModel.onWakeWordDetected()
            runCurrent()

            assertTrue(viewModel.uiState.value.wakeWordDetected)

            advanceTimeBy(WAKE_WORD_TEST_DEBOUNCE + 1.seconds)
            runCurrent()

            assertFalse(viewModel.uiState.value.wakeWordDetected)
        }

        @Test
        fun `Given not testing when onWakeWordDetected then ignore detection`() = runTest {
            viewModel = createViewModel()
            runCurrent()

            viewModel.onWakeWordDetected()
            runCurrent()

            assertFalse(viewModel.uiState.value.wakeWordDetected)
        }
    }
}
