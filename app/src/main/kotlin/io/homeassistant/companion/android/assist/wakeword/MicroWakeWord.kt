package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.microfrontend.FeatureExtractor
import io.homeassistant.companion.android.microfrontend.MicroFrontend
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi
import timber.log.Timber

/**
 * Wake word detector using microWakeWord TFLite models.
 *
 * This implementation is inspired by the ESPHome micro_wake_word component and
 * the pymicro-wakeword Python implementation.
 *
 * The detector processes 16kHz mono audio in 10ms chunks, extracts spectrogram
 * features, and runs inference on a TFLite model to detect wake words.
 *
 * **Thread Safety:** This class is NOT thread-safe. All methods must be called from
 * a single thread, typically the audio recording thread. Concurrent calls to
 * [processAudio] or [reset] from multiple threads will result in undefined behavior.
 *
 * Use [create] to instantiate this class.
 */
class MicroWakeWord @VisibleForTesting internal constructor(
    private val modelConfig: MicroWakeWordModelConfig,
    private val interpreter: InterpreterApi,
    private val featureExtractor: FeatureExtractor,
) : Closeable {
    private val inputBuffer: ByteBuffer
    private val outputBuffer: ByteBuffer

    // Input quantization parameters from model (used to quantize float features to INT8)
    private val inputScale: Float
    private val inputZeroPoint: Int

    // Detection state for probability averaging and cooldown
    private val detectionState = WakeWordDetectionState(
        slidingWindowSize = modelConfig.micro.slidingWindowSize,
        probabilityCutoff = modelConfig.micro.probabilityCutoff,
    )

    // Buffer for accumulating feature frames before inference
    private val featureBuffer = mutableListOf<FloatArray>()

    init {
        // Get input/output tensor details
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        val inputShape = inputTensor.shape()
        val outputShape = outputTensor.shape()

        Timber.d(
            "MicroWakeWord model loaded: input=${inputShape.contentToString()}, output=${outputShape.contentToString()}",
        )

        // Store input quantization parameters for converting float features to INT8
        val inputQuantParams = inputTensor.quantizationParams()
        inputScale = inputQuantParams.scale
        inputZeroPoint = inputQuantParams.zeroPoint

        inputBuffer = createInputBuffer(inputShape)
        outputBuffer = createOutputBuffer(outputShape)
    }

    /**
     * Process audio samples and check for wake word detection.
     *
     * @param samples 16-bit PCM audio samples at 16kHz
     * @return true if wake word was detected
     */
    fun processAudio(samples: ShortArray): Boolean {
        val features = featureExtractor.processSamples(samples)

        for (frame in features) {
            if (processFeatureFrame(frame)) {
                return true
            }
        }

        return false
    }

    /**
     * Process a single feature frame and run inference when ready.
     */
    private fun processFeatureFrame(frame: FloatArray): Boolean {
        if (detectionState.isInCooldown()) {
            return false
        }

        featureBuffer.add(frame)

        if (featureBuffer.size < FEATURE_STRIDE) {
            return false
        }

        val probability = runInference()

        // Clear ALL frames after inference (non-overlapping blocks like ESPHome/Ava)
        featureBuffer.clear()

        val detected = detectionState.addProbabilityAndCheckDetection(probability)
        if (detected) {
            Timber.i(
                "Wake word '${modelConfig.wakeWord}' detected! probability was $probability",
            )
            featureExtractor.reset()
        }

        return detected
    }

    private fun runInference(): Float {
        fillInputBuffer()
        outputBuffer.rewind()

        try {
            interpreter.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            Timber.e(e, "TFLite inference failed")
            return 0f
        }

        return readOutputProbability()
    }

    private fun fillInputBuffer() {
        inputBuffer.rewind()
        for (frame in featureBuffer) {
            for (value in frame) {
                val quantized = ((value / inputScale) + inputZeroPoint)
                    .roundToInt()
                    .coerceIn(-128, 127)
                    .toByte()
                inputBuffer.put(quantized)
            }
        }
        inputBuffer.rewind()
    }

    private fun readOutputProbability(): Float {
        outputBuffer.rewind()
        val quantizedOutput = outputBuffer.get().toInt() and 0xFF
        return quantizedOutput * OUTPUT_SCALE
    }

    /**
     * Reset the detector state.
     */
    fun reset() {
        featureBuffer.clear()
        detectionState.reset()
        featureExtractor.reset()
    }

    private fun createInputBuffer(inputShape: IntArray): ByteBuffer {
        FailFast.failWhen(!inputShape.contentEquals(EXPECTED_INPUT_SHAPE)) {
            "Unexpected input shape ${inputShape.contentToString()}, expected ${EXPECTED_INPUT_SHAPE.contentToString()}"
        }
        // Allocate for all dimensions (batch * stride * features)
        val inputSize = inputShape.fold(1) { acc, dim -> acc * dim }
        return ByteBuffer.allocateDirect(inputSize).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    private fun createOutputBuffer(outputShape: IntArray): ByteBuffer {
        FailFast.failWhen(!outputShape.contentEquals(EXPECTED_OUTPUT_SHAPE)) {
            "Unexpected output shape ${outputShape.contentToString()}, expected ${EXPECTED_OUTPUT_SHAPE.contentToString()}"
        }
        // Allocate enough for all dimensions, minimum 4 bytes for TFLite padding requirements
        val outputSize = maxOf(4, outputShape.fold(1) { acc, dim -> acc * dim })
        return ByteBuffer.allocateDirect(outputSize).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    override fun close() {
        interpreter.close()
        featureExtractor.close()
    }

    companion object {
        // Model expects 3 feature frames (stride) before inference
        private const val FEATURE_STRIDE = 3

        // Output tensor quantization: scale=0.00390625 (1/256), zeroPoint=0
        private const val OUTPUT_SCALE = 0.00390625f

        // Expected tensor shapes: [batch, stride, features] and [batch, probability]
        private val EXPECTED_INPUT_SHAPE = intArrayOf(1, 3, 40)
        private val EXPECTED_OUTPUT_SHAPE = intArrayOf(1, 1)

        /**
         * Create a new MicroWakeWord instance.
         *
         * This is a suspend function to allow cancellation during model loading.
         */
        suspend fun create(context: Context, modelConfig: MicroWakeWordModelConfig): MicroWakeWord {
            val interpreter = createInterpreter(context, modelConfig)
            val featureExtractor = MicroFrontend(stepSizeMs = modelConfig.micro.featureStepSize)
            return MicroWakeWord(modelConfig, interpreter, featureExtractor)
        }

        private suspend fun createInterpreter(context: Context, modelConfig: MicroWakeWordModelConfig): InterpreterApi =
            withContext(Dispatchers.IO) {
                Timber.d("Loading wake word model: ${modelConfig.wakeWord} (${modelConfig.modelAssetPath})")
                val modelBuffer = loadModelFile(context, modelConfig.modelAssetPath)
                ensureActive()
                InterpreterApi.create(
                    modelBuffer,
                    InterpreterApi.Options()
                        .setRuntime(InterpreterApi.Options.TfLiteRuntime.PREFER_SYSTEM_OVER_APPLICATION),
                )
            }

        private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
            val assetFileDescriptor = context.assets.openFd(assetPath)
            val inputStream = assetFileDescriptor.createInputStream()
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }
}
