package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import io.homeassistant.companion.android.microfrontend.MicroFrontend
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt
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
 * @param context Android context for loading assets
 * @param model Wake word model configuration containing model path and detection parameters
 */
class MicroWakeWord(context: Context, private val model: MicroWakeWordModel) : Closeable {

    private val featureExtractor = MicroFrontend(stepSizeMs = model.micro.featureStepSize)
    private val interpreter: InterpreterApi
    private val inputBuffer: ByteBuffer
    private val outputBuffer: ByteBuffer

    // Input quantization parameters from model (used to quantize float features to INT8)
    private val inputScale: Float
    private val inputZeroPoint: Int

    // Detection parameters from model config
    private val probabilityCutoff = model.micro.probabilityCutoff
    private val slidingWindowSize = model.micro.slidingWindowSize

    // Sliding window of probabilities for averaging
    private val probabilities = FloatArray(slidingWindowSize)
    private var probabilityIndex = 0
    private var probabilityCount = 0

    // Buffer for accumulating feature frames before inference
    private val featureBuffer = mutableListOf<FloatArray>()

    // Cooldown to prevent multiple detections
    private var cooldownFrames = 0
    private val cooldownDuration = slidingWindowSize * 2

    init {
        Timber.d("Loading wake word model: ${model.wakeWord} (${model.modelAssetPath})")

        val modelBuffer = loadModelFile(context, model.modelAssetPath)
        interpreter = InterpreterApi.create(
            modelBuffer,
            InterpreterApi.Options()
                .setRuntime(InterpreterApi.Options.TfLiteRuntime.PREFER_SYSTEM_OVER_APPLICATION),
        )

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

        // Input: [1, stride, features] = [1, 3, 40] - INT8
        val inputSize = inputShape[1] * inputShape[2]
        inputBuffer = ByteBuffer.allocateDirect(inputSize).apply {
            order(ByteOrder.nativeOrder())
        }

        // Output: [1, 1] = single probability value (uint8)
        outputBuffer = ByteBuffer.allocateDirect(1).apply {
            order(ByteOrder.nativeOrder())
        }
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
        if (cooldownFrames > 0) {
            cooldownFrames--
            return false
        }

        featureBuffer.add(frame)

        if (featureBuffer.size < FEATURE_STRIDE) {
            return false
        }

        val probability = runInference()

        // Clear ALL frames after inference (non-overlapping blocks like ESPHome/Ava)
        featureBuffer.clear()

        // Add probability to sliding window
        probabilities[probabilityIndex] = probability
        probabilityIndex = (probabilityIndex + 1) % slidingWindowSize
        if (probabilityCount < slidingWindowSize) {
            probabilityCount++
        }

        // Check for detection using average probability
        if (probabilityCount >= slidingWindowSize) {
            val avgProbability = probabilities.sum() / slidingWindowSize

            if (avgProbability >= probabilityCutoff) {
                Timber.i("Wake word '${model.wakeWord}' detected! avgProbability=$avgProbability")
                reset()
                cooldownFrames = cooldownDuration
                return true
            }
        }

        return false
    }

    private fun runInference(): Float {
        // Prepare input buffer with FEATURE_STRIDE frames, quantizing floats to INT8
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

        outputBuffer.rewind()
        try {
            interpreter.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            Timber.e(e, "TFLite inference failed")
            return 0f
        }

        // Get output probability (uint8 quantized, scale=1/256, zeroPoint=0)
        outputBuffer.rewind()
        val quantizedOutput = outputBuffer.get().toInt() and 0xFF
        return quantizedOutput * OUTPUT_SCALE
    }

    /**
     * Reset the detector state.
     */
    fun reset() {
        featureBuffer.clear()
        probabilities.fill(0f)
        probabilityIndex = 0
        probabilityCount = 0
        featureExtractor.reset()
    }

    /**
     * Load TFLite model from assets.
     */
    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(assetPath)
        val inputStream = assetFileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
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
    }
}
