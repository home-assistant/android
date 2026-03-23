// SPDX-License-Identifier: Apache-2.0

#include <jni.h>
#include <memory>

#include "Logging.h"
#include "MicroWakeWordEngine.h"

static constexpr char LOG_TAG[] = "MicroWakeWord_jni";

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_homeassistant_companion_android_microwakeword_MicroWakeWord_nativeCreate(
    JNIEnv* env, jclass clazz, jobject modelBuffer, jint sampleRate,
    jint featureStepSizeMs, jfloat probabilityCutoff, jint slidingWindowSize) {

    auto* modelData = static_cast<uint8_t*>(env->GetDirectBufferAddress(modelBuffer));
    if (modelData == nullptr) {
        LOGE(LOG_TAG, "Failed to get direct buffer address from model ByteBuffer");
        return 0;
    }
    jlong modelSize = env->GetDirectBufferCapacity(modelBuffer);
    if (modelSize <= 0) {
        LOGE(LOG_TAG, "Invalid model buffer capacity: %lld", static_cast<long long>(modelSize));
        return 0;
    }

    auto engine = std::make_unique<MicroWakeWordEngine>(
        modelData,
        static_cast<size_t>(modelSize),
        static_cast<int>(sampleRate),
        static_cast<int>(featureStepSizeMs),
        static_cast<float>(probabilityCutoff),
        static_cast<int>(slidingWindowSize)
    );

    if (!engine->isInitialized()) {
        return 0;
    }

    return reinterpret_cast<jlong>(engine.release());
}

JNIEXPORT jboolean JNICALL
Java_io_homeassistant_companion_android_microwakeword_MicroWakeWord_nativeProcessAudio(
    JNIEnv* env, jclass clazz, jlong handle, jshortArray samplesArray) {

    if (handle == 0) return JNI_FALSE;

    auto* engine = reinterpret_cast<MicroWakeWordEngine*>(handle);

    jsize numSamples = env->GetArrayLength(samplesArray);
    jshort* samples = env->GetShortArrayElements(samplesArray, nullptr);
    if (samples == nullptr) return JNI_FALSE;

    bool detected = engine->processAudio(samples, static_cast<size_t>(numSamples));

    // JNI_ABORT: release without copying back (we didn't modify the array)
    env->ReleaseShortArrayElements(samplesArray, samples, JNI_ABORT);

    return detected ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_homeassistant_companion_android_microwakeword_MicroWakeWord_nativeReset(
    JNIEnv* env, jclass clazz, jlong handle) {

    if (handle == 0) return;
    auto* engine = reinterpret_cast<MicroWakeWordEngine*>(handle);
    engine->reset();
}

JNIEXPORT void JNICALL
Java_io_homeassistant_companion_android_microwakeword_MicroWakeWord_nativeDestroy(
    JNIEnv* env, jclass clazz, jlong handle) {

    if (handle == 0) return;
    auto* engine = reinterpret_cast<MicroWakeWordEngine*>(handle);
    delete engine;
}

}  // extern "C"
