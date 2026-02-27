package io.homeassistant.companion.android.assist.wakeword

import android.content.Context

/**
 * TFLite initializer for the minimal flavor using bundled LiteRT.
 *
 * LiteRT is bundled directly in the APK and doesn't require initialization.
 */
class TfLiteInitializerImpl : TfLiteInitializer {

    override suspend fun initialize(context: Context, playServicesAvailable: Boolean) {
        // LiteRT is bundled and doesn't require initialization
    }
}
