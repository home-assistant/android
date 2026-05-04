#ifndef MICRO_FRONTEND_WRAPPER_H
#define MICRO_FRONTEND_WRAPPER_H

#include <cstddef>
#include <cstdint>
#include <vector>

extern "C" {
#include "tensorflow/lite/experimental/microfrontend/lib/frontend.h"
}

// Number of mel filterbank features per frame (matches ESPHome PREPROCESSOR_FEATURE_SIZE)
constexpr size_t PREPROCESSOR_FEATURE_SIZE = 40;

/**
 * C++ wrapper for TFLite Micro Frontend audio feature extraction.
 *
 * Configuration matches ESPHome microWakeWord component:
 * https://github.com/esphome/esphome/blob/dev/esphome/components/micro_wake_word/preprocessor_settings.h
 */
class MicroFrontendWrapper {
public:
    MicroFrontendWrapper(int sampleRate, size_t stepSizeMs);
    ~MicroFrontendWrapper();

    // Non-copyable, non-movable
    MicroFrontendWrapper(const MicroFrontendWrapper&) = delete;
    MicroFrontendWrapper& operator=(const MicroFrontendWrapper&) = delete;
    MicroFrontendWrapper(MicroFrontendWrapper&&) = delete;
    MicroFrontendWrapper& operator=(MicroFrontendWrapper&&) = delete;

    [[nodiscard]] bool isInitialized() const { return initialized_; }

    /**
     * Process audio samples and extract spectrogram features.
     *
     * @param samples 16-bit PCM audio samples
     * @param numSamples Number of samples
     * @return Vector of feature frames (each frame is a vector of floats)
     */
    std::vector<std::vector<float>> processSamples(const int16_t* samples, size_t numSamples);

    /**
     * Reset internal state (noise estimates, PCAN state, sample buffer).
     */
    void reset();

private:
    struct FrontendState state_{};
    int sampleRate_;
    size_t stepSizeMs_;
    bool initialized_ = false;
};

#endif // MICRO_FRONTEND_WRAPPER_H
