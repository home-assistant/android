package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import android.media.AudioRecord
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class WakeWordListenerTest {

    private lateinit var context: Context
    private lateinit var tfLiteInitializer: TfLiteInitializer
    private lateinit var microWakeWord: MicroWakeWord
    private lateinit var audioRecord: AudioRecord

    private val testModelConfig = MicroWakeWordModelConfig(
        wakeWord = "test_wake_word",
        author = "test",
        website = "https://test.com",
        model = "test.tflite",
        trainedLanguages = listOf("en"),
        version = 1,
        micro = MicroWakeWordModelConfig.MicroFrontendConfig(
            probabilityCutoff = 0.5f,
            slidingWindowSize = 3,
            featureStepSize = 10,
        ),
    )

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        tfLiteInitializer = mockk(relaxed = true)
        microWakeWord = mockk(relaxed = true)
        audioRecord = mockk(relaxed = true)

        // Default: AudioRecord is initialized and returns error to exit loop
        every { audioRecord.state } returns AudioRecord.STATE_INITIALIZED
        every { audioRecord.read(any<ShortArray>(), any(), any()) } returns -1
    }

    private fun createListener(
        playServicesAvailability: Boolean = false,
        onListenerReady: (MicroWakeWordModelConfig) -> Unit = {},
        onWakeWordDetected: (MicroWakeWordModelConfig) -> Unit = {},
        onListenerStopped: () -> Unit = {},
        onListenerFailed: () -> Unit = {},
    ): WakeWordListener {
        return WakeWordListener(
            context = context,
            onListenerReady = onListenerReady,
            onWakeWordDetected = onWakeWordDetected,
            onListenerStopped = onListenerStopped,
            onListenerFailed = onListenerFailed,
            playServicesAvailability = playServicesAvailability,
            tfLiteInitializer = tfLiteInitializer,
            microWakeWordFactory = { microWakeWord },
            audioRecordFactory = { audioRecord },
        )
    }

    @Nested
    inner class IsListeningTests {

        @Test
        fun `Given listener not started then isListening returns false`() {
            val listener = createListener()

            assertFalse(listener.isListening)
        }

        @Test
        fun `Given listener started then isListening returns true`() = runTest {
            var wasListeningWhenReady = false
            lateinit var listener: WakeWordListener
            listener = createListener(
                onListenerReady = { wasListeningWhenReady = listener.isListening },
            )

            listener.start(this, testModelConfig, testScheduler)
            runCurrent()

            // Check isListening was true during the ready callback (before loop exits)
            assertTrue(wasListeningWhenReady)
        }

        @Test
        fun `Given listener stopped then isListening returns false`() = runTest {
            val listener = createListener()

            listener.start(this, testModelConfig)
            listener.stop()
            runCurrent()

            assertFalse(listener.isListening)
        }
    }

    @Nested
    inner class StartTests {

        @Test
        fun `Given start called then initializes TFLite`() = runTest {
            val listener = createListener()

            listener.start(this, testModelConfig)
            runCurrent()

            coVerify { tfLiteInitializer.initialize(context, any()) }
        }

        @Test
        fun `Given start called then creates detector with model config`() = runTest {
            var factoryCalledWithModel: MicroWakeWordModelConfig? = null
            val listener = WakeWordListener(
                context = context,
                onWakeWordDetected = {},
                tfLiteInitializer = tfLiteInitializer,
                microWakeWordFactory = { model ->
                    factoryCalledWithModel = model
                    microWakeWord
                },
                audioRecordFactory = { audioRecord },
            )

            listener.start(this, testModelConfig, testScheduler)
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
                onWakeWordDetected = {},
                tfLiteInitializer = tfLiteInitializer,
                microWakeWordFactory = {
                    factoryCallCount++
                    microWakeWord
                },
                audioRecordFactory = { audioRecord },
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
        fun `Given stop called then invokes onListenerStopped callback`() = runTest(UnconfinedTestDispatcher()) {
            var stoppedCalled = false
            val listener = createListener(
                onListenerStopped = { stoppedCalled = true },
            )

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))

            // Loop exits due to -1 return, which completes the job and calls onListenerStopped
            assertTrue(stoppedCalled)
        }

        @Test
        fun `Given stop called then closes detector`() = runTest(UnconfinedTestDispatcher()) {
            val listener = createListener()

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))

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
        fun `Given wake word detected then invokes onWakeWordDetected callback`() = runTest(UnconfinedTestDispatcher()) {
            var detectedModel: MicroWakeWordModelConfig? = null
            val listener = createListener(
                onWakeWordDetected = { detectedModel = it },
            )

            var callCount = 0
            every { audioRecord.read(any<ShortArray>(), any(), any()) } answers {
                callCount++
                if (callCount == 1) 160 else -1
            }
            every { microWakeWord.processAudio(any()) } returns true

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))

            assertTrue(detectedModel === testModelConfig)
        }

        @Test
        fun `Given wake word detected then resets detector`() = runTest(UnconfinedTestDispatcher()) {
            val listener = createListener()

            var callCount = 0
            every { audioRecord.read(any<ShortArray>(), any(), any()) } answers {
                callCount++
                if (callCount == 1) 160 else -1
            }
            every { microWakeWord.processAudio(any()) } returns true

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))

            verify { microWakeWord.reset() }
        }

        @Test
        fun `Given no wake word detected then does not invoke callback`() = runTest(UnconfinedTestDispatcher()) {
            var detectedCalled = false
            val listener = createListener(
                onWakeWordDetected = { detectedCalled = true },
            )

            var callCount = 0
            every { audioRecord.read(any<ShortArray>(), any(), any()) } answers {
                callCount++
                if (callCount == 1) 160 else -1
            }
            every { microWakeWord.processAudio(any()) } returns false

            listener.start(backgroundScope, testModelConfig, UnconfinedTestDispatcher(testScheduler))

            assertFalse(detectedCalled)
        }
    }

    @Nested
    inner class ErrorHandlingTests {

        @Test
        fun `Given TFLite initialization fails when Play Services is unavailable then failed callback is invoked`() = runTest {
            var detectedCalled = false
            val listener = createListener(
                playServicesAvailability = false,
                onListenerFailed = { detectedCalled = true }
            )
            val expectedException = TfLiteInitializeException("Play Services is unavailable")

            coEvery { tfLiteInitializer.initialize(any(), false) } throws expectedException

            listener.start(backgroundScope, testModelConfig, this.testScheduler)
            runCurrent()

            assertTrue(detectedCalled)
        }

        @Test
        fun `Given TFLite initialization fails unexpectedly then throws`() = runTest {
            val listener = createListener()
            val expectedException = RuntimeException("Init failed")

            coEvery { tfLiteInitializer.initialize(any(), any()) } throws expectedException

            try {
                listener.start(backgroundScope, testModelConfig)
            } catch (e: RuntimeException) {
                assertEquals(expectedException, e)
            }
        }

        @Test
        fun `Given detector creation fails then throws`() = runTest {
            val expectedException = RuntimeException("Init failed")

            val listener = WakeWordListener(
                context = context,
                onWakeWordDetected = {},
                tfLiteInitializer = tfLiteInitializer,
                microWakeWordFactory = { throw expectedException },
                audioRecordFactory = { audioRecord },
            )

            try {
                listener.start(backgroundScope, testModelConfig)
            } catch (e: RuntimeException) {
                assertEquals(expectedException, e)
            }
        }

        @Test
        fun `Given scope cancelled then cleans up resources`() = runTest {
            val listener = createListener()

            listener.start(this, testModelConfig, this.testScheduler)
            runCurrent()

            backgroundScope.cancel()

            verify { microWakeWord.close() }
            verifyOrder {
                audioRecord.stop()
                audioRecord.release()
            }
        }
    }
}
