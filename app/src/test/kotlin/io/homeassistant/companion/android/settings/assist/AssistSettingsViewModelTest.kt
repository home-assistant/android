package io.homeassistant.companion.android.settings.assist

import android.content.Intent
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.assist.wakeword.WakeWordListener
import io.homeassistant.companion.android.assist.wakeword.WakeWordListenerFactory
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.homeassistant.companion.android.util.microWakeWordModelConfigs
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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
    private val wakeWordListener: WakeWordListener = mockk(relaxed = true)
    private val onWakeWordDetectedSlot = slot<(MicroWakeWordModelConfig) -> Unit>()
    private val onListenerStoppedSlot = slot<() -> Unit>()
    private val wakeWordListenerFactory: WakeWordListenerFactory = mockk {
        every {
            create(
                capture(onWakeWordDetectedSlot),
                any(),
                capture(onListenerStoppedSlot),
            )
        } returns wakeWordListener
    }

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
            wakeWordListenerFactory = wakeWordListenerFactory,
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

        @Test
        fun `Given currently testing when onSelectWakeWordModel then restart listener with new model`() = runTest {
            coEvery { wakeWordListener.stop() } just Runs
            coEvery { wakeWordListener.start(any(), any(), any()) } just Runs
            viewModel = createViewModel()
            runCurrent()

            // Start testing first
            viewModel.startTestWakeWord()
            runCurrent()
            assertTrue(viewModel.uiState.value.isTestingWakeWord)

            // Change wake word while testing
            viewModel.onSelectWakeWordModel(microWakeWordModelConfigs[1])
            runCurrent()

            // Should stop and restart
            coVerify { wakeWordListener.stop() }
            coVerify(exactly = 2) { wakeWordListener.start(any(), any(), any()) }
        }
    }

    @Nested
    inner class TestWakeWordTest {

        @Test
        fun `Given valid model when startTestWakeWord then start listener`() = runTest {
            coEvery { wakeWordListener.start(any(), any(), any()) } just Runs
            viewModel = createViewModel()
            runCurrent()

            viewModel.startTestWakeWord()
            runCurrent()

            assertTrue(viewModel.uiState.value.isTestingWakeWord)
            assertFalse(viewModel.uiState.value.wakeWordDetected)
            coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[0], any()) }
        }

        @Test
        fun `Given no selected model and no available models when startTestWakeWord then do nothing`() = runTest {
            coEvery { assistConfigManager.getSelectedWakeWordModel() } returns null
            coEvery { assistConfigManager.getAvailableModels() } returns emptyList()
            viewModel = createViewModel()
            runCurrent()

            viewModel.startTestWakeWord()
            runCurrent()

            assertFalse(viewModel.uiState.value.isTestingWakeWord)
            coVerify(exactly = 0) { wakeWordListener.start(any(), any(), any()) }
        }

        @Test
        fun `Given no selected model but available models when startTestWakeWord then use first model`() = runTest {
            coEvery { assistConfigManager.getSelectedWakeWordModel() } returns null
            coEvery { wakeWordListener.start(any(), any(), any()) } just Runs
            viewModel = createViewModel()
            runCurrent()

            viewModel.startTestWakeWord()
            runCurrent()

            assertTrue(viewModel.uiState.value.isTestingWakeWord)
            coVerify { wakeWordListener.start(any(), microWakeWordModelConfigs[0], any()) }
        }

        @Test
        fun `Given testing when stopTestWakeWord then stop listener`() = runTest {
            coEvery { wakeWordListener.stop() } just Runs
            coEvery { wakeWordListener.start(any(), any(), any()) } just Runs
            viewModel = createViewModel()
            runCurrent()

            viewModel.startTestWakeWord()
            runCurrent()
            assertTrue(viewModel.uiState.value.isTestingWakeWord)

            viewModel.stopTestWakeWord()
            runCurrent()

            assertFalse(viewModel.uiState.value.isTestingWakeWord)
            coVerify { wakeWordListener.stop() }
        }

        @Test
        fun `Given wake word detected when callback invoked then show detected and reset after debounce`() = runTest {
            coEvery { wakeWordListener.start(any(), any(), any()) } just Runs
            viewModel = createViewModel()
            runCurrent()

            viewModel.startTestWakeWord()
            runCurrent()

            // Trigger wake word detected callback
            onWakeWordDetectedSlot.captured.invoke(microWakeWordModelConfigs[0])
            runCurrent()

            assertTrue(viewModel.uiState.value.wakeWordDetected)

            // Advance time past debounce
            advanceTimeBy(WAKE_WORD_TEST_DEBOUNCE + 1.seconds)
            runCurrent()

            assertFalse(viewModel.uiState.value.wakeWordDetected)
        }

        @Test
        fun `Given listener stopped externally when callback invoked then update state`() = runTest {
            coEvery { wakeWordListener.start(any(), any(), any()) } just Runs
            viewModel = createViewModel()
            runCurrent()

            viewModel.startTestWakeWord()
            runCurrent()
            assertTrue(viewModel.uiState.value.isTestingWakeWord)

            // Trigger listener stopped callback
            onListenerStoppedSlot.captured.invoke()

            assertFalse(viewModel.uiState.value.isTestingWakeWord)
        }
    }
}
