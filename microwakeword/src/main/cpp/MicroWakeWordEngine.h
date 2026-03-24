#ifndef MICRO_WAKE_WORD_ENGINE_H
#define MICRO_WAKE_WORD_ENGINE_H

#include <array>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

#include "MicroFrontendWrapper.h"
#include "tensorflow/lite/micro/micro_interpreter.h"
#include "tensorflow/lite/micro/micro_mutable_op_resolver.h"

/**
 * Combined wake word detection engine: feature extraction + TFLite Micro
 * inference + sliding window detection state.
 *
 * Closely mirrors ESPHome's streaming_model.cpp approach:
 * - Uses MicroResourceVariables for streaming state ops
 * - Reads stride from model input tensor dimensions
 * - Passes int8-quantized features directly to input tensor
 * - Reads uint8 output probability
 */
class MicroWakeWordEngine {
public:
    /**
     * @param modelData   Pointer to the TFLite flatbuffer model (copied internally, caller can free after construction)
     * @param modelSize   Size of the model data in bytes
     * @param sampleRate  Audio sample rate (typically 16000)
     * @param featureStepSizeMs  Step size for feature extraction in milliseconds
     * @param probabilityCutoff  Detection threshold (0.0-1.0)
     * @param slidingWindowSize  Number of frames to average for detection
     */
    MicroWakeWordEngine(
        const uint8_t* modelData,
        size_t modelSize,
        int sampleRate,
        int featureStepSizeMs,
        float probabilityCutoff,
        int slidingWindowSize
    );

    // Non-copyable, non-movable
    MicroWakeWordEngine(const MicroWakeWordEngine&) = delete;
    MicroWakeWordEngine& operator=(const MicroWakeWordEngine&) = delete;
    MicroWakeWordEngine(MicroWakeWordEngine&&) = delete;
    MicroWakeWordEngine& operator=(MicroWakeWordEngine&&) = delete;

    [[nodiscard]] bool isInitialized() const { return initialized_; }

    /**
     * Process audio samples and check for wake word detection.
     *
     * @param samples    16-bit PCM audio samples at the configured sample rate
     * @param numSamples Number of samples
     * @return true if wake word was detected
     */
    [[nodiscard]] bool processAudio(const int16_t* samples, size_t numSamples);

    /**
     * Reset all internal state (frontend, feature buffer, detection state).
     */
    void reset();

private:
    // Tensor arena size for the model interpreter
    static constexpr size_t TENSOR_ARENA_SIZE = 64 * 1024;

    // Variable arena size for streaming state (ESPHome uses 1024 on ESP32; we need
    // more because MicroAllocator::Create also allocates a GreedyMemoryPlanner here)
    static constexpr size_t VARIABLE_ARENA_SIZE = 4096;

    // Minimum number of inference slices before detection can trigger
    // (matches ESPHome MIN_SLICES_BEFORE_DETECTION)
    static constexpr int MIN_SLICES_BEFORE_DETECTION = 100;

    [[nodiscard]] bool loadModel();
    [[nodiscard]] bool registerOps();
    [[nodiscard]] bool processFeatureFrame(const int8_t* features);
    [[nodiscard]] bool checkDetection() const;
    void resetDetectionState();

    // Feature extraction
    MicroFrontendWrapper frontend_;

    // Input quantization parameters (read from model input tensor)
    float inputScale_ = 1.0f;
    int inputZeroPoint_ = 0;

    // TFLite Micro interpreter
    std::unique_ptr<uint8_t[]> modelCopy_;
    size_t modelSize_ = 0;
    std::array<uint8_t, TENSOR_ARENA_SIZE> tensorArena_{};
    std::array<uint8_t, VARIABLE_ARENA_SIZE> varArena_{};
    // 20 is the max number of registered ops, matching ESPHome's hardcoded value
    tflite::MicroMutableOpResolver<20> opResolver_;
    std::unique_ptr<tflite::MicroInterpreter> interpreter_;

    // Stride management (read from model input tensor dims)
    int stride_ = 1;
    int currentStrideStep_ = 0;

    // Detection state (sliding window probability averaging)
    uint8_t probabilityCutoff_;  // quantized 0-255
    int slidingWindowSize_;
    std::vector<uint8_t> recentProbabilities_;
    int lastNIndex_ = 0;
    int ignoreWindows_ = 0;

    bool initialized_ = false;
};

#endif // MICRO_WAKE_WORD_ENGINE_H
