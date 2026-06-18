#include <jni.h>
#include <memory>

#include "Logging.h"
#include "MicroWakeWordEngine.h"

static constexpr char LOG_TAG[] = "MicroWakeWord_jni";

static jlong nativeCreate(
    JNIEnv* env, jclass /*clazz*/, jobject modelBuffer, jint sampleRate,
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

static jboolean nativeProcessAudio(
    JNIEnv* env, jclass /*clazz*/, jlong handle, jshortArray samplesArray) {

    if (handle == 0) return JNI_FALSE;

    auto* engine = reinterpret_cast<MicroWakeWordEngine*>(handle);

    jsize numSamples = env->GetArrayLength(samplesArray);
    // GetShortArrayElements may return a direct pointer (zero-copy) or a pinned copy.
    // Either way it avoids a per-call heap allocation on this hot path (called every ~10ms).
    jshort* samples = env->GetShortArrayElements(samplesArray, nullptr);
    if (samples == nullptr) return JNI_FALSE;

    bool detected = engine->processAudio(samples, static_cast<size_t>(numSamples));

    // JNI_ABORT: release without copying back (read-only access)
    env->ReleaseShortArrayElements(samplesArray, samples, JNI_ABORT);

    return detected ? JNI_TRUE : JNI_FALSE;
}

static void nativeReset(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    if (handle == 0) return;
    auto* engine = reinterpret_cast<MicroWakeWordEngine*>(handle);
    engine->reset();
}

static void nativeDestroy(JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {
    if (handle == 0) return;
    auto* engine = reinterpret_cast<MicroWakeWordEngine*>(handle);
    delete engine;
}

static const JNINativeMethod methods[] = {
    {"nativeCreate", "(Ljava/nio/ByteBuffer;IIFI)J", reinterpret_cast<void*>(nativeCreate)},
    {"nativeProcessAudio", "(J[S)Z", reinterpret_cast<void*>(nativeProcessAudio)},
    {"nativeReset", "(J)V", reinterpret_cast<void*>(nativeReset)},
    {"nativeDestroy", "(J)V", reinterpret_cast<void*>(nativeDestroy)},
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("io/homeassistant/companion/android/microwakeword/MicroWakeWord");
    if (clazz == nullptr) {
        LOGE(LOG_TAG, "Failed to find MicroWakeWord class for JNI registration");
        return JNI_ERR;
    }

    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) != JNI_OK) {
        LOGE(LOG_TAG, "Failed to register native methods");
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
