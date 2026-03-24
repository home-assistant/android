#include "MicroWakeWordEngine.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "Logging.h"
#include "flatbuffers/flatbuffers.h"
#include "tensorflow/lite/micro/micro_interpreter.h"
#include "tensorflow/lite/micro/micro_mutable_op_resolver.h"
#include "tensorflow/lite/micro/micro_resource_variable.h"
#include "tensorflow/lite/schema/schema_generated.h"

static constexpr char LOG_TAG[] = "MicroWakeWordEngine";

MicroWakeWordEngine::MicroWakeWordEngine(
    const uint8_t* modelData,
    size_t modelSize,
    int sampleRate,
    int featureStepSizeMs,
    float probabilityCutoff,
    int slidingWindowSize
)
    : frontend_(sampleRate, static_cast<size_t>(std::max(1, featureStepSizeMs)))
    , probabilityCutoff_(static_cast<uint8_t>(std::max(0.0f, std::min(1.0f, probabilityCutoff)) * 255.0f))
    , slidingWindowSize_(std::max(1, slidingWindowSize))
    , recentProbabilities_(slidingWindowSize_, 0)
    , ignoreWindows_(-MIN_SLICES_BEFORE_DETECTION)
{
    if (slidingWindowSize <= 0) {
        LOGE(LOG_TAG, "Invalid slidingWindowSize: %d (must be > 0)", slidingWindowSize);
        return;
    }
    if (featureStepSizeMs <= 0) {
        LOGE(LOG_TAG, "Invalid featureStepSizeMs: %d (must be > 0)", featureStepSizeMs);
        return;
    }
    if (!frontend_.isInitialized()) {
        LOGE(LOG_TAG, "Frontend initialization failed");
        return;
    }

    // Copy model data so caller can free the original
    modelSize_ = modelSize;
    modelCopy_ = std::make_unique<uint8_t[]>(modelSize);
    std::memcpy(modelCopy_.get(), modelData, modelSize);

    if (!loadModel()) {
        return;
    }

    initialized_ = true;
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
        LOGE(LOG_TAG, "Failed to register TFLite operators");
        return false;
    }

    // Both MicroAllocator and MicroResourceVariables are placement-allocated into varArena_,
    // so they do not require explicit deallocation
    auto* microAllocator = tflite::MicroAllocator::Create(varArena_.data(), VARIABLE_ARENA_SIZE);
    if (microAllocator == nullptr) {
        LOGE(LOG_TAG, "Could not create MicroAllocator for variable arena");
        return false;
    }
    // 20 is the max number of resource variable slots, matching ESPHome's hardcoded value
    auto* resourceVariables = tflite::MicroResourceVariables::Create(microAllocator, 20);
    if (resourceVariables == nullptr) {
        LOGE(LOG_TAG, "Could not create MicroResourceVariables");
        return false;
    }

    // Verify the flatbuffer is a valid TFLite model before parsing
    flatbuffers::Verifier verifier(modelCopy_.get(), modelSize_);
    if (!tflite::VerifyModelBuffer(verifier)) {
        LOGE(LOG_TAG, "Invalid TFLite model flatbuffer");
        return false;
    }

    const tflite::Model* model = tflite::GetModel(modelCopy_.get());
    if (model == nullptr) {
        LOGE(LOG_TAG, "Failed to parse TFLite model");
        return false;
    }

    auto interpreter = std::make_unique<tflite::MicroInterpreter>(
        model, opResolver_, tensorArena_.data(), TENSOR_ARENA_SIZE, resourceVariables
    );

    if (interpreter->AllocateTensors() != kTfLiteOk) {
        LOGE(LOG_TAG, "Failed to allocate tensors");
        return false;
    }

    // Validate and read input tensor properties
    TfLiteTensor* input = interpreter->input(0);
    if (input == nullptr) {
        LOGE(LOG_TAG, "Model input tensor is null");
        return false;
    }
    if (input->dims->size != 3) {
        LOGE(LOG_TAG, "Model input tensor has wrong rank (expected 3, got %d)", input->dims->size);
        return false;
    }
    if (input->dims->data[0] != 1 || input->dims->data[2] != PREPROCESSOR_FEATURE_SIZE) {
        LOGE(LOG_TAG, "Model input tensor has unexpected dimensions (expected [1, stride, %zu], got [%d, %d, %d])",
             PREPROCESSOR_FEATURE_SIZE,
             input->dims->data[0], input->dims->data[1], input->dims->data[2]);
        return false;
    }
    if (input->type != kTfLiteInt8) {
        LOGE(LOG_TAG, "Model input tensor is not int8");
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
    TfLiteTensor* output = interpreter->output(0);
    if (output == nullptr) {
        LOGE(LOG_TAG, "Model output tensor is null");
        return false;
    }
    if (output->dims->size != 2) {
        LOGE(LOG_TAG, "Model output tensor has wrong rank (expected 2, got %d)", output->dims->size);
        return false;
    }
    if (output->dims->data[0] != 1 || output->dims->data[1] != 1) {
        LOGE(LOG_TAG, "Model output tensor has unexpected dimensions (expected [1, 1], got [%d, %d])",
             output->dims->data[0], output->dims->data[1]);
        return false;
    }
    if (output->type != kTfLiteUInt8) {
        LOGE(LOG_TAG, "Model output tensor is not uint8");
        return false;
    }

    interpreter_ = std::move(interpreter);

    LOGD(LOG_TAG, "Engine initialized: stride=%d, inputScale=%.6f, inputZeroPoint=%d, arena=%zu bytes",
         stride_, inputScale_, inputZeroPoint_, TENSOR_ARENA_SIZE);

    return true;
}

bool MicroWakeWordEngine::processAudio(const int16_t* samples, size_t numSamples) {
    if (!initialized_) return false;

    auto features = frontend_.processSamples(samples, numSamples);

    for (auto& frame : features) {
        // Convert float features to int8 using input quantization parameters
        int8_t quantizedFeatures[PREPROCESSOR_FEATURE_SIZE]{};
        for (size_t index = 0; index < PREPROCESSOR_FEATURE_SIZE && index < frame.size(); index++) {
            float quantized = (frame[index] / inputScale_) + static_cast<float>(inputZeroPoint_);
            int rounded = static_cast<int>(std::round(quantized));
            quantizedFeatures[index] = static_cast<int8_t>(std::max(-128, std::min(127, rounded)));
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
    std::memcpy(
        tflite::GetTensorData<int8_t>(input) + PREPROCESSOR_FEATURE_SIZE * currentStrideStep_,
        features, PREPROCESSOR_FEATURE_SIZE
    );
    ++currentStrideStep_;

    if (currentStrideStep_ < stride_) {
        return false;  // Not enough frames accumulated yet
    }

    // Run inference
    if (interpreter_->Invoke() != kTfLiteOk) {
        LOGE(LOG_TAG, "TFLite inference failed");
        return false;
    }

    // Read output probability (uint8)
    TfLiteTensor* output = interpreter_->output(0);
    if (output == nullptr) {
        LOGE(LOG_TAG, "Model output tensor is null after inference, skipping");
        return false;
    }
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
        LOGI(LOG_TAG, "Wake word detected");
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
