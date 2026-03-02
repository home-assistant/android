package io.homeassistant.companion.android.assist.wakeword

import android.content.Context

/**
 * Interface for initializing TensorFlow Lite runtime.
 *
 * The full flavor uses Google Play Services TFLite which requires async initialization.
 * The minimal flavor uses bundled LiteRT which is immediately available.
 */
interface TfLiteInitializer {
    /**
     * Initialize the TFLite runtime.
     *
     * @param context Application context
     *
     * @throws Exception if any exception occurred during initialization
     */
    suspend fun initialize(context: Context)
}


/**
 * Exception thrown when TFLite initialization fails with a well known unrecoverable reason,
 * such as unavailability of Google Play Services.
 */
class TfLiteInitializeException(throwable: Throwable?, message: String?) : Exception(message, throwable)
