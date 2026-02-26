package io.homeassistant.companion.android.assist

import app.cash.turbine.test
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.assist.wakeword.WakeWordListener
import io.homeassistant.companion.android.assist.wakeword.WakeWordListenerFactory
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import io.homeassistant.companion.android.settings.assist.AssistConfigManager
import io.homeassistant.companion.android.util.microWakeWordModelConfigs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WakeWordAssistAudioStrategyTest {

    private lateinit var voiceAudioRecorder: VoiceAudioRecorder
    private lateinit var wakeWordListenerFactory: WakeWordListenerFactory
    private lateinit var assistConfigManager: AssistConfigManager
    private lateinit var listener: WakeWordListener
    private lateinit var strategy: WakeWordAssistAudioStrategy
    private lateinit var audioFlow: MutableSharedFlow<ShortArray>

    private val onWakeWordDetectedSlot = slot<(MicroWakeWordModelConfig) -> Unit>()

    private val availableModels = microWakeWordModelConfigs

    @BeforeEach
    fun setUp() {
        audioFlow = MutableSharedFlow()
        voiceAudioRecorder = mockk {
            coEvery { audioData() } returns audioFlow
        }
        listener = mockk(relaxed = true)
        wakeWordListenerFactory = mockk {
            every {
                create(
                    onWakeWordDetected = capture(onWakeWordDetectedSlot),
                    onListenerReady = any(),
                    onListenerStopped = any(),
                )
            } returns listener
        }
        assistConfigManager = mockk()

        strategy = WakeWordAssistAudioStrategy(
            voiceAudioRecorder = voiceAudioRecorder,
            wakeWordListenerFactory = wakeWordListenerFactory,
            assistConfigManager = assistConfigManager,
            wakeWordPhrase = "",
        )
    }


    @Test
    fun `Given recorder emitting audio When audioData collected Then emits ShortArray directly`() = runTest {
        val expected = shortArrayOf(1, 2, 3)

        val collected = mutableListOf<ShortArray>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            strategy.audioData().toList(collected)
        }

        audioFlow.emit(expected)
        job.cancel()

        assertTrue(collected.size == 1, "Expected exactly one emission")
        assertArrayEquals(expected, collected[0])
    }

    @Test
    fun `Given strategy When requestFocus called Then does nothing`() {
        // Should not throw
        strategy.requestFocus()
    }

    @Test
    fun `Given strategy When abandonFocus called Then does nothing`() {
        // Should not throw
        strategy.abandonFocus()
    }

    @Test
    fun `Given no phrase When wakeWordDetected collected Then listener starts with first available model`() = runTest {
        coEvery { assistConfigManager.getAvailableModels() } returns availableModels

        strategy.wakeWordDetected.test {
            coVerify { listener.start(coroutineScope = any(), modelConfig = availableModels[0]) }
        }
    }

    @Test
    fun `Given phrase matches available model When wakeWordDetected collected Then listener starts with matched model`() = runTest {
        coEvery { assistConfigManager.getAvailableModels() } returns availableModels

        val strategyWithPhrase = WakeWordAssistAudioStrategy(
            voiceAudioRecorder = voiceAudioRecorder,
            wakeWordListenerFactory = wakeWordListenerFactory,
            assistConfigManager = assistConfigManager,
            wakeWordPhrase = availableModels[1].wakeWord,
        )

        strategyWithPhrase.wakeWordDetected.test {
            coVerify { listener.start(coroutineScope = any(), modelConfig = availableModels[1]) }
        }
    }

    @Test
    fun `Given unknown phrase When wakeWordDetected collected Then listener starts with first available model as fallback`() = runTest {
        coEvery { assistConfigManager.getAvailableModels() } returns availableModels

        val strategyWithUnknownPhrase = WakeWordAssistAudioStrategy(
            voiceAudioRecorder = voiceAudioRecorder,
            wakeWordListenerFactory = wakeWordListenerFactory,
            assistConfigManager = assistConfigManager,
            wakeWordPhrase = "unknown_wake_word",
        )

        strategyWithUnknownPhrase.wakeWordDetected.test {
            coVerify { listener.start(coroutineScope = any(), modelConfig = availableModels[0]) }
        }
    }

    @Test
    fun `Given listener running When wake word detected Then flow emits wake word phrase`() = runTest {
        coEvery { assistConfigManager.getAvailableModels() } returns availableModels


        strategy.wakeWordDetected.test {
            // Simulate the listener detecting a wake word
            onWakeWordDetectedSlot.captured.invoke(availableModels[0])
            val emittedPhrase = awaitItem()
            assertEquals(availableModels[0].wakeWord, emittedPhrase)
        }
    }

    @Test
    fun `Given onListenerStopped callback When strategy created Then passes it to listener factory`() = runTest {
        val onListenerStoppedSlot = slot<() -> Unit>()
        val factoryWithStoppedCapture = mockk<WakeWordListenerFactory> {
            every {
                create(
                    onWakeWordDetected = any(),
                    onListenerReady = any(),
                    onListenerStopped = capture(onListenerStoppedSlot),
                )
            } returns listener
        }

        var stoppedCalled = false
        val strategyUnderTest = WakeWordAssistAudioStrategy(
            voiceAudioRecorder = voiceAudioRecorder,
            wakeWordListenerFactory = factoryWithStoppedCapture,
            assistConfigManager = assistConfigManager,
            wakeWordPhrase = "",
            onListenerStopped = { stoppedCalled = true },
        )

        coEvery { assistConfigManager.getAvailableModels() } returns availableModels

        strategyUnderTest.wakeWordDetected.test {
            onListenerStoppedSlot.captured.invoke()
            assertTrue(stoppedCalled, "onListenerStopped should be forwarded to the listener factory")
        }
    }
}
