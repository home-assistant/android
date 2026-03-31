package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber

/**
 * Configuration for a microWakeWord model.
 *
 * This matches the JSON configuration format used by ESPHome microWakeWord models.
 * From [micro-wake-word-models](https://github.com/esphome/micro-wake-word-models/tree/main/models)
 *
 * @param wakeWord Human-readable name of the wake word (e.g., "Okay Nabu")
 * @param author Author of the model
 * @param website Author's website
 * @param model Name of the .tflite model file
 * @param trainedLanguages List of language codes the model was trained on
 * @param version Model version
 * @param micro MicroFrontend specific configuration parameters
 */
@Serializable
data class MicroWakeWordModelConfig(
    val wakeWord: String,
    val author: String,
    val website: String,
    val model: String,
    val trainedLanguages: List<String>,
    val version: Int,
    val micro: MicroFrontendConfig,
) {
    /**
     * Full asset path to the model file.
     */
    val modelAssetPath: String = "$WAKEWORD_ASSET_DIR/$model"

    /**
     * MicroFrontend specific configuration parameters.
     *
     * @param probabilityCutoff Detection threshold (0.0-1.0)
     * @param featureStepSize Step size in milliseconds for feature extraction
     * @param slidingWindowSize Number of frames to average for detection
     */
    @Serializable
    data class MicroFrontendConfig(val probabilityCutoff: Float, val featureStepSize: Int, val slidingWindowSize: Int)

    companion object {
        private const val WAKEWORD_ASSET_DIR = "wakeword"

        /**
         * Load all available wake word models from assets.
         *
         * Scans the wakeword/ assets directory for .json configuration files
         * and parses them into [MicroWakeWordModelConfig] instances.
         *
         * @param context Android context for accessing assets
         * @return List of available wake word models
         */
        suspend fun loadAvailableModels(context: Context): List<MicroWakeWordModelConfig> =
            withContext(Dispatchers.IO) {
                val models = mutableListOf<MicroWakeWordModelConfig>()

                val assetFiles = context.assets.list(WAKEWORD_ASSET_DIR) ?: emptyArray()

                for (fileName in assetFiles) {
                    if (fileName.endsWith(".json")) {
                        FailFast.failOnCatch {
                            val jsonContent = context.assets
                                .open("$WAKEWORD_ASSET_DIR/$fileName")
                                .bufferedReader()
                                .use { it.readText() }

                            val model = kotlinJsonMapper.decodeFromString<MicroWakeWordModelConfig>(jsonContent)
                            models.add(model)
                            Timber.d("Loaded wake word model: ${model.wakeWord}")
                        }
                    }
                }
                models.sortedBy { it.wakeWord }
            }
    }
}
