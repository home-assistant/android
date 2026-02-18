package io.homeassistant.companion.android.frontend.externalbus.outgoing

import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ResultMessage.Companion.config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Base sealed interface for messages sent to the Home Assistant frontend via the external bus.
 *
 * These messages are serialized to JSON and evaluated in the WebView to communicate
 * with the Home Assistant frontend.
 *
 * @see <a href="https://developers.home-assistant.io/docs/frontend/external-bus">External bus documentation</a>
 */
@Serializable
sealed interface OutgoingExternalBusMessage {
    val id: Int?
}

/**
 * Result message for responding to frontend requests.
 *
 * This is the response format for all result-type messages from the app to the frontend.
 * Use the companion factory functions to create specific result types with proper payloads.
 */
@Serializable
@SerialName("result")
data class ResultMessage(
    override val id: Int?,
    val success: Boolean = true,
    val result: JsonElement = JsonNull,
    val error: JsonElement = JsonNull,
) : OutgoingExternalBusMessage {

    companion object {

        /**
         * Creates a config response with app capabilities.
         *
         * @param id The message ID from the config/get request
         * @param config The app capabilities configuration
         */
        fun config(id: Int?, config: ConfigResult): ResultMessage {
            return ResultMessage(
                id = id,
                result = frontendExternalBusJson.encodeToJsonElement(config),
            )
        }
    }
}

/**
 * Configuration result payload for config/get requests.
 *
 * Contains the app's capabilities that the frontend needs to know about.
 */
@Serializable
data class ConfigResult(
    val hasSettingsScreen: Boolean = true,
    val canWriteTag: Boolean,
    val hasExoPlayer: Boolean = true,
    val canCommissionMatter: Boolean,
    val canImportThreadCredentials: Boolean,
    val hasAssist: Boolean = true,
    val hasBarCodeScanner: Int,
    val canSetupImprov: Boolean = true,
    val downloadFileSupported: Boolean = true,
    val appVersion: String,
    val hasEntityAddTo: Boolean = true,
) {
    companion object {
        fun create(
            hasNfc: Boolean,
            canCommissionMatter: Boolean,
            canExportThread: Boolean,
            hasBarCodeScanner: Int,
            appVersion: AppVersion,
        ) = ConfigResult(
            canWriteTag = hasNfc,
            canCommissionMatter = canCommissionMatter,
            canImportThreadCredentials = canExportThread,
            hasBarCodeScanner = hasBarCodeScanner,
            appVersion = appVersion.value,
        )
    }
}
