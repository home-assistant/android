package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import com.google.android.gms.tflite.java.TfLite
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * TFLite initializer for the full flavor using Google Play Services.
 *
 * Play Services TFLite downloads the runtime on demand, which requires
 * async initialization before the interpreter can be used.
 */
class TfLiteInitializerImpl : TfLiteInitializer {

    override suspend fun initialize(context: Context) {
        Timber.d("Initializing TFLite via Play Services")
        try {
            TfLite.initialize(context).await()
            Timber.d("TFLite Play Services initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TFLite Play Services")
            throw e
        }
    }
}
