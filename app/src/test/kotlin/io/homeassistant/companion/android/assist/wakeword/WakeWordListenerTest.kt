package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import io.homeassistant.companion.android.microwakeword.MicroWakeWord
import io.homeassistant.companion.android.util.microWakeWordModelConfigs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WakeWordListenerTest {

    private lateinit var context: Context
    private lateinit var microWakeWord: MicroWakeWord
    private lateinit var voiceAudioRecorder: VoiceAudioRecorder
    private lateinit var audioFlow: MutableSharedFlow<ShortArray>

    private val testModelConfig = microWakeWordModelConfigs[0]

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        microWakeWord = mockk(relaxed = true)
        audioFlow = MutableSharedFlow()
        voiceAudioRecorder = mockk {
            coEvery { audioData() } returns audioFlow
        }
    }

    private fun createListener(
        onListenerReady: (MicroWakeWordModelConfig) -> Unit = {},
        onWakeWordDetected: (MicroWakeWordModelConfig) -> Unit = {},
        onListenerStopped: () -> Unit = {},
    ): WakeWordListener {
        return WakeWordListener(
            context = context,
            voiceAudioRecorder = voiceAudioRecorder,
            onListenerReady = onListenerReady,
            onWakeWordDetected = onWakeWordDetected,
            onListenerStopped = onListenerStopped,
            microWakeWordFactory = { microWakeWord },
        )
    }

    @Nested
    inner class StartTests {

        @Test
        fun `Given start called then creates detector with model config`() = runTest {
            var factoryCalledWithModel: MicroWakeWordModelConfig? = null
            val listener = WakeWordListener(
                context = context,
                voiceAudioRecorder = voiceAudioRecorder,
                onWakeWordDetected = {},
                microWakeWordFactory = { model ->
                    factoryCalledWithModel = model
                    microWakeWord
                },
            )

            listener.start(backgroundScope, testModelConfig, testScheduler)
            runCurrent()

            assertEquals(testModelConfig, factoryCalledWithModel)
        }

        @Test
        fun `Given start called then invokes onListenerReady callback`() = runTest {
            var readyModel: MicroWakeWordModelConfig? = null
            val listener = createListener(
                onListenerReady = { readyModel = it },
            )

            listener.start(backgroundScope, testModelConfig, testScheduler)
            runCurrent()

            assertEquals(testModelConfig, readyModel)
        }

        @Test
        fun `Given already listening then start cancels previous and starts new`() = runTest {
            var factoryCallCount = 0
            val listener = WakeWordListener(
                context = context,
                voiceAudioRecorder = voiceAudioRecorder,
                onWakeWordDetected = {},
                microWakeWordFactory = {
                    factoryCallCount++
                    microWakeWord
                },
            )

            // First start
            listener.start(backgroundScope, testModelConfig, testScheduler)
            runCurrent()

            assertEquals(1, factoryCallCount, "Factory should be called for first start")

            // Second start should cancel first and start new
            listener.start(backgroundScope, testModelConfig, testScheduler)
            runCurrent()

            assertEquals(2, factoryCallCount, "Factory should be called again for second start")
        }
    }

    @Nested
    inner class StopTests {

        @Test
        fun `Given stop called then invokes onListenerStopped callback`() = runTest {
            var stoppedCalled = false
            val listener = createListener(
                onListenerStopped = { stoppedCalled = true },
            )

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))
            runCurrent()
            listener.stop()
            runCurrent()

            assertTrue(stoppedCalled)
        }

        @Test
        fun `Given stop called then closes detector`() = runTest {
            val listener = createListener()

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))
            runCurrent()
            listener.stop()
            runCurrent()

            verify { microWakeWord.close() }
        }

        @Test
        fun `Given stop called when not listening then does nothing`() = runTest {
            var stoppedCalled = false
            val listener = createListener(
                onListenerStopped = { stoppedCalled = true },
            )

            listener.stop()

            assertFalse(stoppedCalled)
        }
    }

    @Nested
    inner class DetectionTests {

        @Test
        fun `Given wake word detected then invokes onWakeWordDetected callback`() = runTest {
            var detectedModel: MicroWakeWordModelConfig? = null
            val listener = createListener(
                onWakeWordDetected = { detectedModel = it },
            )

            every { microWakeWord.processAudio(any()) } returns true

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))
            runCurrent()

            audioFlow.emit(shortArrayOf(1, 2, 3))
            runCurrent()

            assertTrue(detectedModel === testModelConfig)
        }

        @Test
        fun `Given wake word detected then resets detector`() = runTest {
            val listener = createListener()

            every { microWakeWord.processAudio(any()) } returns true

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))
            runCurrent()

            audioFlow.emit(shortArrayOf(1, 2, 3))
            runCurrent()

            verify { microWakeWord.reset() }
        }

        @Test
        fun `Given no wake word detected then does not invoke callback`() = runTest {
            var detectedCalled = false
            val listener = createListener(
                onWakeWordDetected = { detectedCalled = true },
            )

            every { microWakeWord.processAudio(any()) } returns false

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))
            runCurrent()

            audioFlow.emit(shortArrayOf(1, 2, 3))
            runCurrent()

            assertFalse(detectedCalled)
        }
    }

    @Nested
    inner class CooldownTests {

        @Test
        fun `Given wake word detected then skips processing during cooldown period`() = runTest {
            var detectionCount = 0
            val listener = createListener(
                onWakeWordDetected = { detectionCount++ },
            )

            every { microWakeWord.processAudio(any()) } returns true

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))
            runCurrent()

            // First chunk triggers detection
            audioFlow.emit(shortArrayOf(1))
            runCurrent()
            assertEquals(1, detectionCount, "First chunk should trigger detection")

            // All chunks during cooldown should be skipped (processAudio not called again)
            repeat(POST_DETECTION_COOLDOWN_CHUNKS) {
                audioFlow.emit(shortArrayOf(1))
                runCurrent()
            }
            assertEquals(
                1,
                detectionCount,
                "No additional detections should occur during cooldown",
            )
            // processAudio called once for the initial detection, not during cooldown
            verify(exactly = 1) { microWakeWord.processAudio(any()) }
        }

        @Test
        fun `Given cooldown expired then resumes processing audio`() = runTest {
            var detectionCount = 0
            val listener = createListener(
                onWakeWordDetected = { detectionCount++ },
            )

            every { microWakeWord.processAudio(any()) } returns true

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))
            runCurrent()

            // First detection
            audioFlow.emit(shortArrayOf(1))
            runCurrent()
            assertEquals(1, detectionCount)

            // Exhaust cooldown
            repeat(POST_DETECTION_COOLDOWN_CHUNKS) {
                audioFlow.emit(shortArrayOf(1))
                runCurrent()
            }

            // Next chunk after cooldown should trigger detection again
            audioFlow.emit(shortArrayOf(1))
            runCurrent()
            assertEquals(2, detectionCount, "Detection should resume after cooldown expires")
        }

        @Test
        fun `Given cooldown active when no detection in audio then still skips processing`() = runTest {
            var detectionCount = 0
            var processAudioCallCount = 0
            val listener = createListener(
                onWakeWordDetected = { detectionCount++ },
            )

            every { microWakeWord.processAudio(any()) } answers {
                processAudioCallCount++
                true
            }

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))
            runCurrent()

            // Trigger first detection
            audioFlow.emit(shortArrayOf(1))
            runCurrent()
            assertEquals(1, processAudioCallCount, "processAudio called for first chunk")

            // Emit a few chunks during cooldown — processAudio should not be called
            for (i in 1..5) {
                audioFlow.emit(shortArrayOf(1))
                runCurrent()
            }
            assertEquals(
                1,
                processAudioCallCount,
                "processAudio should not be called during cooldown",
            )
        }
    }

    @Nested
    inner class ErrorHandlingTests {

        @Test
        fun `Given detector creation fails then throws`() = runTest {
            val expectedException = RuntimeException("Init failed")

            val listener = WakeWordListener(
                context = context,
                voiceAudioRecorder = voiceAudioRecorder,
                onWakeWordDetected = {},
                microWakeWordFactory = { throw expectedException },
            )

            try {
                listener.start(backgroundScope, testModelConfig)
            } catch (e: RuntimeException) {
                assertEquals(expectedException, e)
            }
        }
    }

    @Nested
    inner class WarmUpTests {

        @Test
        fun `Given an engine when warming it up then it processes low level noise and ignores the result`() = runTest {
            val processed = mutableListOf<ShortArray>()
            val engine = mockk<MicroWakeWord> {
                // The same buffer is reused between calls, so keep a copy of each one
                every { processAudio(any()) } answers {
                    processed += firstArg<ShortArray>().copyOf()
                    true
                }
            }

            engine.warmedUp()

            assertEquals(WARM_UP_CHUNKS, processed.size)
            assertTrue(processed.all { it.size == WARM_UP_CHUNK_SAMPLES })
            // Background noise only: far below the level of speech (a few thousand)
            assertTrue(processed.all { chunk -> chunk.all { kotlin.math.abs(it.toInt()) < 500 } })
            assertTrue(processed.any { chunk -> chunk.any { it.toInt() != 0 } })
            verify(exactly = 0) { engine.close() }
        }

        @Test
        fun `Given warm up is cancelled then the engine is closed`() = runTest {
            val engine = mockk<MicroWakeWord>(relaxed = true)
            lateinit var job: Job
            every { engine.processAudio(any()) } answers {
                job.cancel()
                false
            }

            job = launch { engine.warmedUp() }
            runCurrent()

            assertTrue(job.isCancelled)
            // Cancellation is noticed at the next chunk, long before the whole warm-up ran
            verify(atMost = 1) { engine.processAudio(any()) }
            verify(exactly = 1) { engine.close() }
        }
    }
}
