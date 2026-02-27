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
     * @param playServicesAvailability Boolean indicating whether Google Play Services is available
     *
     * @throws Exception if any exception occurred during initialization
     */
    suspend fun initialize(context: Context, playServicesAvailability: Boolean)
}


/**
 * Exception thrown when TFLite initialization fails with a well known unrecoverable reason,
 * such as unavailability of Google Play Services.
 */
class TfLiteInitializeException(override val message: String?) : Exception()
