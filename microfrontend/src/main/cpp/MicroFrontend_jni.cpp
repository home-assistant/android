// SPDX-License-Identifier: Apache-2.0

#include <jni.h>
#include <android/log.h>

#include "MicroFrontendWrapper.h"

#define LOG_TAG "MicroFrontend"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Cached JNI references (initialized in JNI_OnLoad)
static jclass g_arrayListClass = nullptr;
static jmethodID g_arrayListInit = nullptr;
static jmethodID g_arrayListAdd = nullptr;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Cache ArrayList class and methods (used frequently in nativeProcessSamples)
    jclass localArrayListClass = env->FindClass("java/util/ArrayList");
    if (localArrayListClass == nullptr) {
        LOGE("Failed to find ArrayList class");
        return JNI_ERR;
    }
    g_arrayListClass = reinterpret_cast<jclass>(env->NewGlobalRef(localArrayListClass));
    env->DeleteLocalRef(localArrayListClass);

    g_arrayListInit = env->GetMethodID(g_arrayListClass, "<init>", "(I)V");
    if (g_arrayListInit == nullptr) {
        LOGE("Failed to find ArrayList constructor");
        return JNI_ERR;
    }

    g_arrayListAdd = env->GetMethodID(g_arrayListClass, "add", "(Ljava/lang/Object;)Z");
    if (g_arrayListAdd == nullptr) {
        LOGE("Failed to find ArrayList.add method");
        return JNI_ERR;
    }

    LOGD("JNI_OnLoad: cached ArrayList class and methods");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return;
    }

    if (g_arrayListClass != nullptr) {
        env->DeleteGlobalRef(g_arrayListClass);
        g_arrayListClass = nullptr;
    }
    g_arrayListInit = nullptr;
    g_arrayListAdd = nullptr;

    LOGD("JNI_OnUnload: released global references");
}

JNIEXPORT jlong JNICALL
Java_io_homeassistant_companion_android_microfrontend_MicroFrontend_nativeCreate(
    JNIEnv* env, jclass clazz, jint sampleRate, jint stepSizeMs) {
    auto* wrapper = new MicroFrontendWrapper(
        static_cast<int>(sampleRate),
        static_cast<size_t>(stepSizeMs)
    );
    if (!wrapper->isInitialized()) {
        delete wrapper;
        return 0;
    }
    return reinterpret_cast<jlong>(wrapper);
}

JNIEXPORT void JNICALL
Java_io_homeassistant_companion_android_microfrontend_MicroFrontend_nativeDestroy(
    JNIEnv* env, jclass clazz, jlong handle) {
    if (handle == 0) {
        return;
    }
    auto* wrapper = reinterpret_cast<MicroFrontendWrapper*>(handle);
    delete wrapper;
}

JNIEXPORT jobject JNICALL
Java_io_homeassistant_companion_android_microfrontend_MicroFrontend_nativeProcessSamples(
    JNIEnv* env, jclass clazz, jlong handle, jshortArray samplesArray) {
    if (handle == 0 || g_arrayListClass == nullptr) {
        return nullptr;
    }

    auto* wrapper = reinterpret_cast<MicroFrontendWrapper*>(handle);

    jsize numSamples = env->GetArrayLength(samplesArray);
    jshort* samples = env->GetShortArrayElements(samplesArray, nullptr);
    if (samples == nullptr) {
        return nullptr;
    }

    auto results = wrapper->processSamples(samples, static_cast<size_t>(numSamples));

    // JNI_ABORT: release without copying back (we didn't modify the array)
    env->ReleaseShortArrayElements(samplesArray, samples, JNI_ABORT);

    // Create ArrayList<FloatArray> using cached class and methods
    jobject resultList = env->NewObject(g_arrayListClass, g_arrayListInit, static_cast<jint>(results.size()));
    if (resultList == nullptr) {
        return nullptr;
    }

    for (const auto& frame : results) {
        jfloatArray floatArray = env->NewFloatArray(static_cast<jsize>(frame.size()));
        if (floatArray == nullptr) {
            env->DeleteLocalRef(resultList);
            return nullptr;
        }
        env->SetFloatArrayRegion(floatArray, 0, static_cast<jsize>(frame.size()), frame.data());
        env->CallBooleanMethod(resultList, g_arrayListAdd, floatArray);
        env->DeleteLocalRef(floatArray);

        // Check for exceptions after CallBooleanMethod
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(resultList);
            return nullptr;
        }
    }

    return resultList;
}

JNIEXPORT void JNICALL
Java_io_homeassistant_companion_android_microfrontend_MicroFrontend_nativeReset(
    JNIEnv* env, jclass clazz, jlong handle) {
    if (handle == 0) {
        return;
    }
    auto* wrapper = reinterpret_cast<MicroFrontendWrapper*>(handle);
    wrapper->reset();
}

}  // extern "C"
