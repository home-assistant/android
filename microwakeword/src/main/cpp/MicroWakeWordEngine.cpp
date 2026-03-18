// SPDX-License-Identifier: Apache-2.0

#include "MicroWakeWordEngine.h"

#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>

#include "tensorflow/lite/micro/micro_interpreter.h"
#include "tensorflow/lite/micro/micro_mutable_op_resolver.h"
#include "tensorflow/lite/micro/micro_resource_variable.h"
#include "tensorflow/lite/schema/schema_generated.h"

#define LOG_TAG "MicroWakeWord"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

MicroWakeWordEngine::MicroWakeWordEngine(
    const uint8_t* modelData,
    size_t modelSize,
    int sampleRate,
    int featureStepSizeMs,
    float probabilityCutoff,
    int slidingWindowSize
)
    : frontend_(sampleRate, static_cast<size_t>(featureStepSizeMs))
    , probabilityCutoff_(static_cast<uint8_t>(probabilityCutoff * 255.0f))
    , slidingWindowSize_(slidingWindowSize)
    , recentProbabilities_(slidingWindowSize, 0)
    , ignoreWindows_(-MIN_SLICES_BEFORE_DETECTION)
{
    if (!frontend_.isInitialized()) {
        LOGE("Frontend initialization failed");
        return;
    }

    // Copy model data so caller can free the original
    modelCopy_ = std::make_unique<uint8_t[]>(modelSize);
    std::memcpy(modelCopy_.get(), modelData, modelSize);

    if (!loadModel()) {
        return;
    }

    initialized_ = true;
}

MicroWakeWordEngine::~MicroWakeWordEngine() {
    interpreter_.reset();
    // Free arenas (allocated with new[])
    delete[] tensorArena_;
    delete[] varArena_;
}

bool MicroWakeWordEngine::registerOps() {
    // Operators matching ESPHome's streaming_model register_streaming_ops_
    if (opResolver_.AddCallOnce() != kTfLiteOk) return false;
    if (opResolver_.AddVarHandle() != kTfLiteOk) return false;
    if (opResolver_.AddReshape() != kTfLiteOk) return false;
    if (opResolver_.AddReadVariable() != kTfLiteOk) return false;
    if (opResolver_.AddStridedSlice() != kTfLiteOk) return false;
    if (opResolver_.AddConcatenation() != kTfLiteOk) return false;
    if (opResolver_.AddAssignVariable() != kTfLiteOk) return false;
    if (opResolver_.AddConv2D() != kTfLiteOk) return false;
    if (opResolver_.AddMul() != kTfLiteOk) return false;
    if (opResolver_.AddAdd() != kTfLiteOk) return false;
    if (opResolver_.AddMean() != kTfLiteOk) return false;
    if (opResolver_.AddFullyConnected() != kTfLiteOk) return false;
    if (opResolver_.AddLogistic() != kTfLiteOk) return false;
    if (opResolver_.AddQuantize() != kTfLiteOk) return false;
    if (opResolver_.AddDepthwiseConv2D() != kTfLiteOk) return false;
    if (opResolver_.AddAveragePool2D() != kTfLiteOk) return false;
    if (opResolver_.AddMaxPool2D() != kTfLiteOk) return false;
    if (opResolver_.AddPad() != kTfLiteOk) return false;
    if (opResolver_.AddPack() != kTfLiteOk) return false;
    if (opResolver_.AddSplitV() != kTfLiteOk) return false;
    return true;
}

bool MicroWakeWordEngine::loadModel() {
    if (!registerOps()) {
        LOGE("Failed to register TFLite operators");
        return false;
    }

    // Dynamically allocate tensor arena (aligned)
    tensorArena_ = new (std::nothrow) uint8_t[TENSOR_ARENA_SIZE];
    if (tensorArena_ == nullptr) {
        LOGE("Could not allocate tensor arena (%zu bytes)", TENSOR_ARENA_SIZE);
        return false;
    }

    // Allocate variable arena for streaming state ops
    varArena_ = new (std::nothrow) uint8_t[VARIABLE_ARENA_SIZE];
    if (varArena_ == nullptr) {
        LOGE("Could not allocate variable arena (%zu bytes)", VARIABLE_ARENA_SIZE);
        return false;
    }
    microAllocator_ = tflite::MicroAllocator::Create(varArena_, VARIABLE_ARENA_SIZE);
    if (microAllocator_ == nullptr) {
        LOGE("Could not create MicroAllocator for variable arena");
        return false;
    }
    resourceVariables_ = tflite::MicroResourceVariables::Create(microAllocator_, 20);
    if (resourceVariables_ == nullptr) {
        LOGE("Could not create MicroResourceVariables");
        return false;
    }

    const tflite::Model* model = tflite::GetModel(modelCopy_.get());
    if (model == nullptr) {
        LOGE("Failed to parse TFLite model");
        return false;
    }

    interpreter_ = std::make_unique<tflite::MicroInterpreter>(
        model, opResolver_, tensorArena_, TENSOR_ARENA_SIZE, resourceVariables_
    );

    if (interpreter_->AllocateTensors() != kTfLiteOk) {
        LOGE("Failed to allocate tensors");
        interpreter_.reset();
        return false;
    }

    // Validate and read input tensor properties
    TfLiteTensor* input = interpreter_->input(0);
    if (input == nullptr || input->dims->size != 3 || input->dims->data[0] != 1 ||
        input->dims->data[2] != PREPROCESSOR_FEATURE_SIZE) {
        LOGE("Model input tensor has unexpected dimensions (expected [1, stride, %d])", PREPROCESSOR_FEATURE_SIZE);
        interpreter_.reset();
        return false;
    }
    if (input->type != kTfLiteInt8) {
        LOGE("Model input tensor is not int8");
        interpreter_.reset();
        return false;
    }

    // Read stride from model (dimension 1 of input tensor)
    stride_ = input->dims->data[1];

    // Read input quantization parameters
    if (input->quantization.type == kTfLiteAffineQuantization) {
        auto* params = static_cast<TfLiteAffineQuantization*>(input->quantization.params);
        if (params != nullptr && params->scale != nullptr && params->zero_point != nullptr) {
            inputScale_ = params->scale->data[0];
            inputZeroPoint_ = params->zero_point->data[0];
        }
    }

    // Validate output tensor
    TfLiteTensor* output = interpreter_->output(0);
    if (output == nullptr || output->dims->size != 2 || output->dims->data[0] != 1 ||
        output->dims->data[1] != 1) {
        LOGE("Model output tensor has unexpected dimensions (expected [1, 1])");
        interpreter_.reset();
        return false;
    }
    if (output->type != kTfLiteUInt8) {
        LOGE("Model output tensor is not uint8");
        interpreter_.reset();
        return false;
    }

    LOGD("Engine initialized: stride=%d, inputScale=%.6f, inputZeroPoint=%d, arena=%zu bytes",
         stride_, inputScale_, inputZeroPoint_, TENSOR_ARENA_SIZE);

    return true;
}

bool MicroWakeWordEngine::processAudio(const int16_t* samples, size_t numSamples) {
    if (!initialized_) return false;

    auto features = frontend_.processSamples(samples, numSamples);

    for (auto& frame : features) {
        // Convert float features to int8 using input quantization parameters
        int8_t quantizedFeatures[PREPROCESSOR_FEATURE_SIZE];
        for (int i = 0; i < PREPROCESSOR_FEATURE_SIZE && i < static_cast<int>(frame.size()); i++) {
            float quantized = (frame[i] / inputScale_) + static_cast<float>(inputZeroPoint_);
            int rounded = static_cast<int>(std::round(quantized));
            quantizedFeatures[i] = static_cast<int8_t>(std::max(-128, std::min(127, rounded)));
        }

        if (processFeatureFrame(quantizedFeatures)) {
            return true;
        }
    }

    return false;
}

bool MicroWakeWordEngine::processFeatureFrame(const int8_t* features) {
    TfLiteTensor* input = interpreter_->input(0);
    if (input == nullptr) return false;

    // Place features at the current stride position in the input tensor
    // (matches ESPHome's stride-based accumulation)
    currentStrideStep_ = currentStrideStep_ % stride_;
    std::memmove(
        tflite::GetTensorData<int8_t>(input) + PREPROCESSOR_FEATURE_SIZE * currentStrideStep_,
        features, PREPROCESSOR_FEATURE_SIZE
    );
    ++currentStrideStep_;

    if (currentStrideStep_ < stride_) {
        return false;  // Not enough frames accumulated yet
    }

    // Run inference
    if (interpreter_->Invoke() != kTfLiteOk) {
        LOGE("TFLite inference failed");
        return false;
    }

    // Read output probability (uint8)
    TfLiteTensor* output = interpreter_->output(0);
    uint8_t probability = output->data.uint8[0];

    // Update sliding window
    ++lastNIndex_;
    if (lastNIndex_ == slidingWindowSize_) {
        lastNIndex_ = 0;
    }
    recentProbabilities_[lastNIndex_] = probability;

    // Only increment ignore windows when below cutoff (cool-off from previous detection)
    if (probability < probabilityCutoff_) {
        ignoreWindows_ = std::min(ignoreWindows_ + 1, 0);
    }

    // Check for detection
    if (checkDetection()) {
        LOGI("Wake word detected");
        resetDetectionState();
        return true;
    }

    return false;
}

bool MicroWakeWordEngine::checkDetection() const {
    // Don't detect during ignore window (cool-off period)
    if (ignoreWindows_ < 0) return false;

    // Compute sum of recent probabilities (matching ESPHome's determine_detected)
    uint32_t sum = 0;
    for (auto prob : recentProbabilities_) {
        sum += prob;
    }

    // Detection threshold: sum > cutoff * window_size
    return sum > static_cast<uint32_t>(probabilityCutoff_) * static_cast<uint32_t>(slidingWindowSize_);
}

void MicroWakeWordEngine::resetDetectionState() {
    std::fill(recentProbabilities_.begin(), recentProbabilities_.end(), static_cast<uint8_t>(0));
    ignoreWindows_ = -MIN_SLICES_BEFORE_DETECTION;
    lastNIndex_ = 0;
}

void MicroWakeWordEngine::reset() {
    resetDetectionState();
    currentStrideStep_ = 0;
    frontend_.reset();
}
