package io.homeassistant.companion.android.assist.wakeword

import io.homeassistant.companion.android.microfrontend.FeatureExtractor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.ByteBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.Tensor

class MicroWakeWordTest {

    private lateinit var mockInterpreter: InterpreterApi
    private lateinit var mockFeatureExtractor: FeatureExtractor
    private lateinit var mockInputTensor: Tensor
    private lateinit var mockOutputTensor: Tensor

    private val testModel = MicroWakeWordModelConfig(
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
        mockInterpreter = mockk(relaxed = true)
        mockFeatureExtractor = mockk(relaxed = true)
        mockInputTensor = mockk()
        mockOutputTensor = mockk()

        every { mockInterpreter.getInputTensor(0) } returns mockInputTensor
        every { mockInterpreter.getOutputTensor(0) } returns mockOutputTensor

        // Expected shapes
        every { mockInputTensor.shape() } returns intArrayOf(1, 3, 40)
        every { mockOutputTensor.shape() } returns intArrayOf(1, 1)

        // Quantization params (typical values: scale = 1/128)
        every { mockInputTensor.quantizationParams() } returns mockk {
            every { scale } returns 0.0078125f
            every { zeroPoint } returns 0
        }
    }

    private fun createDetector(): MicroWakeWord {
        return MicroWakeWord(
            modelConfig = testModel,
            interpreter = mockInterpreter,
            featureExtractor = mockFeatureExtractor,
        )
    }

    @Nested
    inner class InputQuantizationTests {

        @Test
        fun `Given feature value of zero then quantizes to zero`() {
            val detector = createDetector()
            val capturedInput = mutableListOf<ByteBuffer>()

            every { mockFeatureExtractor.processSamples(any()) } returns listOf(
                FloatArray(40) { 0f },
                FloatArray(40) { 0f },
                FloatArray(40) { 0f },
            )
            every { mockInterpreter.run(capture(capturedInput), any()) } answers {
                val outputBuffer = secondArg<ByteBuffer>()
                outputBuffer.rewind()
                outputBuffer.put(0.toByte())
            }

            detector.processAudio(ShortArray(0))

            val inputBuffer = capturedInput.first()
            inputBuffer.rewind()
            repeat(120) {
                assertEquals(0.toByte(), inputBuffer.get(), "Expected quantized zero")
            }
        }

        @Test
        fun `Given positive feature values then quantizes correctly`() {
            val detector = createDetector()
            val capturedInput = mutableListOf<ByteBuffer>()

            // Feature value of 0.5 with scale 0.0078125 -> quantized = 0.5 / 0.0078125 = 64
            every { mockFeatureExtractor.processSamples(any()) } returns listOf(
                FloatArray(40) { 0.5f },
                FloatArray(40) { 0.5f },
                FloatArray(40) { 0.5f },
            )
            every { mockInterpreter.run(capture(capturedInput), any()) } answers {
                val outputBuffer = secondArg<ByteBuffer>()
                outputBuffer.rewind()
                outputBuffer.put(0.toByte())
            }

            detector.processAudio(ShortArray(0))

            val inputBuffer = capturedInput.first()
            inputBuffer.rewind()
            repeat(120) {
                assertEquals(64.toByte(), inputBuffer.get(), "Expected quantized value of 64")
            }
        }

        @Test
        fun `Given large values then clamps to max 127`() {
            val detector = createDetector()
            val capturedInput = mutableListOf<ByteBuffer>()

            // Large value should clamp to 127
            every { mockFeatureExtractor.processSamples(any()) } returns listOf(
                FloatArray(40) { 10f },
                FloatArray(40) { 10f },
                FloatArray(40) { 10f },
            )
            every { mockInterpreter.run(capture(capturedInput), any()) } answers {
                val outputBuffer = secondArg<ByteBuffer>()
                outputBuffer.rewind()
                outputBuffer.put(0.toByte())
            }

            detector.processAudio(ShortArray(0))

            val inputBuffer = capturedInput.first()
            inputBuffer.rewind()
            repeat(120) {
                assertEquals(127.toByte(), inputBuffer.get(), "Expected clamped max of 127")
            }
        }

        @Test
        fun `Given negative values then clamps to min -128`() {
            val detector = createDetector()
            val capturedInput = mutableListOf<ByteBuffer>()

            every { mockFeatureExtractor.processSamples(any()) } returns listOf(
                FloatArray(40) { -10f },
                FloatArray(40) { -10f },
                FloatArray(40) { -10f },
            )
            every { mockInterpreter.run(capture(capturedInput), any()) } answers {
                val outputBuffer = secondArg<ByteBuffer>()
                outputBuffer.rewind()
                outputBuffer.put(0.toByte())
            }

            detector.processAudio(ShortArray(0))

            val inputBuffer = capturedInput.first()
            inputBuffer.rewind()
            repeat(120) {
                assertEquals((-128).toByte(), inputBuffer.get(), "Expected clamped min of -128")
            }
        }
    }

    @Nested
    inner class OutputDequantizationTests {

        @Test
        fun `Given output of 0 then returns probability 0`() {
            val detector = createDetector()

            every { mockFeatureExtractor.processSamples(any()) } returns listOf(
                FloatArray(40),
                FloatArray(40),
                FloatArray(40),
            )
            every { mockInterpreter.run(any(), any()) } answers {
                val outputBuffer = secondArg<ByteBuffer>()
                outputBuffer.rewind()
                outputBuffer.put(0.toByte())
            }

            // Process frames but probability is 0
            val detected = detector.processAudio(ShortArray(0))

            assertFalse(detected, "Zero probability should not trigger detection")
        }

        @Test
        fun `Given output of 255 then returns probability close to 1 and detects`() {
            val detector = createDetector()

            every { mockFeatureExtractor.processSamples(any()) } returns listOf(
                FloatArray(40),
                FloatArray(40),
                FloatArray(40),
            )
            every { mockInterpreter.run(any(), any()) } answers {
                val outputBuffer = secondArg<ByteBuffer>()
                outputBuffer.rewind()
                outputBuffer.put(255.toByte()) // 255 * (1/256) ≈ 0.996
            }

            // Fill sliding window (size 3) with high probabilities
            detector.processAudio(ShortArray(0))
            detector.processAudio(ShortArray(0))
            val detected = detector.processAudio(ShortArray(0))

            assertTrue(detected, "High probability should trigger detection")
        }

        @Test
        fun `Given output of 128 then probability equals 0_5 and detects at threshold`() {
            val detector = createDetector()

            every { mockFeatureExtractor.processSamples(any()) } returns listOf(
                FloatArray(40),
                FloatArray(40),
                FloatArray(40),
            )
            every { mockInterpreter.run(any(), any()) } answers {
                val outputBuffer = secondArg<ByteBuffer>()
                outputBuffer.rewind()
                outputBuffer.put(128.toByte()) // 128 * (1/256) = 0.5
            }

            // Fill sliding window
            detector.processAudio(ShortArray(0))
            detector.processAudio(ShortArray(0))
            val detected = detector.processAudio(ShortArray(0))

            assertTrue(detected, "Probability at threshold should trigger detection")
        }
    }

    @Nested
    inner class InferenceFlowTests {

        @Test
        fun `Given less than 3 frames then does not run inference`() {
            val detector = createDetector()

            every { mockFeatureExtractor.processSamples(any()) } returns listOf(FloatArray(40))

            detector.processAudio(ShortArray(0))
            detector.processAudio(ShortArray(0))

            verify(exactly = 0) { mockInterpreter.run(any(), any()) }
        }

        @Test
        fun `Given exactly 3 frames then runs inference once`() {
            val detector = createDetector()

            every { mockFeatureExtractor.processSamples(any()) } returns listOf(
                FloatArray(40),
                FloatArray(40),
                FloatArray(40),
            )
            every { mockInterpreter.run(any(), any()) } answers {
                val outputBuffer = secondArg<ByteBuffer>()
                outputBuffer.rewind()
                outputBuffer.put(0.toByte())
            }

            detector.processAudio(ShortArray(0))

            verify(exactly = 1) { mockInterpreter.run(any(), any()) }
        }

        @Test
        fun `Given 6 frames then runs inference twice`() {
            val detector = createDetector()

            every { mockFeatureExtractor.processSamples(any()) } returns listOf(
                FloatArray(40),
                FloatArray(40),
                FloatArray(40),
                FloatArray(40),
                FloatArray(40),
                FloatArray(40),
            )
            every { mockInterpreter.run(any(), any()) } answers {
                val outputBuffer = secondArg<ByteBuffer>()
                outputBuffer.rewind()
                outputBuffer.put(0.toByte())
            }

            detector.processAudio(ShortArray(0))

            verify(exactly = 2) { mockInterpreter.run(any(), any()) }
        }

        @Test
        fun `Given multiple inferences then output buffer is rewound before each call`() {
            val detector = createDetector()
            val outputBufferPositions = mutableListOf<Int>()

            every { mockFeatureExtractor.processSamples(any()) } returns listOf(
                FloatArray(40),
                FloatArray(40),
                FloatArray(40),
            )
            every { mockInterpreter.run(any(), any()) } answers {
                val outputBuffer = secondArg<ByteBuffer>()
                // Capture the buffer position when interpreter.run is called
                outputBufferPositions.add(outputBuffer.position())
                // Simulate interpreter writing output (without rewinding first)
                outputBuffer.put(128.toByte())
            }

            // Run multiple inferences
            detector.processAudio(ShortArray(0))
            detector.processAudio(ShortArray(0))
            detector.processAudio(ShortArray(0))

            // Verify buffer was at position 0 for each inference call
            assertEquals(3, outputBufferPositions.size, "Expected 3 inference calls")
            outputBufferPositions.forEachIndexed { index, position ->
                assertEquals(0, position, "Output buffer should be rewound before inference call ${index + 1}")
            }
        }
    }
}
