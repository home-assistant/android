package io.homeassistant.companion.android.frontend.addto

import android.content.Context
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import kotlin.io.encoding.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExternalEntityAddToAction(
    @SerialName("app_payload") val appPayload: String,
    val enabled: Boolean,
    val name: String,
    val details: String?,
    @SerialName("mdi_icon") val mdiIcon: String,
) {
    companion object {
        fun fromAction(context: Context, action: EntityAddToAction): ExternalEntityAddToAction {
            // Encode the app payload into Base64 to ensure that the data remains the same while going
            // to the frontend and coming back.
            return ExternalEntityAddToAction(
                appPayload = Base64.UrlSafe.encode(
                    kotlinJsonMapper.encodeToString(action)
                        .encodeToByteArray(),
                ),
                action.enabled,
                action.text(context),
                action.details(context),
                action.mdiIcon,
            )
        }

        fun appPayloadToAction(appPayload: String): EntityAddToAction {
            val actionJSON = Base64.UrlSafe.decode(appPayload).decodeToString()
            return kotlinJsonMapper.decodeFromString<EntityAddToAction>(
                actionJSON,
            )
        }
    }
}
