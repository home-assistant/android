package io.homeassistant.companion.android.webview.externalbus

import android.content.Context
import android.webkit.ValueCallback
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.webview.addto.EntityAddToAction
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * [External bus documentation](https://developers.home-assistant.io/docs/frontend/external-bus)
 *
 * [External bus frontend implementation](https://github.com/home-assistant/frontend/blob/dev/src/external_app/external_messaging.ts)
 */
open class ExternalBusMessage(
    val id: Any?,
    val type: String,
    val command: String? = null,
    val success: Boolean? = null,
    val result: Any? = null,
    val error: Any? = null,
    val payload: Any? = null,
    val callback: ValueCallback<String>? = null,
)

class NavigateTo(path: String, replace: Boolean = false) :
    ExternalBusMessage(
        id = -1,
        type = "command",
        command = "navigate",
        payload = mapOf(
            "path" to path,
            "options" to mapOf(
                "replace" to replace,
            ),
        ),
    )

object ShowSidebar : ExternalBusMessage(
    id = -1,
    type = "command",
    command = "sidebar/show",
)

class ExternalConfigResponse(
    id: Any,
    hasNfc: Boolean,
    canCommissionMatter: Boolean,
    canExportThread: Boolean,
    hasBarCodeScanner: Int,
    appVersion: AppVersion,
) : ExternalBusMessage(
    id = id,
    type = "result",
    success = true,
    result = JSONObject(
        mapOf(
            "hasSettingsScreen" to true,
            "canWriteTag" to hasNfc,
            "hasExoPlayer" to true,
            "canCommissionMatter" to canCommissionMatter,
            "canImportThreadCredentials" to canExportThread,
            "hasAssist" to true,
            "hasBarCodeScanner" to hasBarCodeScanner,
            "canSetupImprov" to true,
            "downloadFileSupported" to true,
            "appVersion" to appVersion.value,
            "hasEntityAddTo" to true,
        ),
    ),
    callback = {
        Timber.d("Callback from external config (id=$id): $it")
    },
)

@Serializable
data class ExternalEntityAddToAction(
    val appPayload: String,
    val enabled: Boolean,
    val name: String,
    val details: String?,
    val mdiIcon: String,
) {
    companion object {
        /**
         * We encode the app payload into Base64 so that we are sure that the data remains the same while going
         * to the frontend and coming back.
         */
        fun fromAction(context: Context, action: EntityAddToAction): ExternalEntityAddToAction {
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

class EntityAddToActionsResponse(id: Any, actions: List<ExternalEntityAddToAction>) :
    ExternalBusMessage(
        id = id,
        type = "result",
        success = true,
        result = JSONObject(
            mapOf(
                "actions" to JSONArray(kotlinJsonMapper.encodeToString(actions)),
            ),
        ),
        callback = {
            Timber.d("Callback from AddToActions")
        },
    )
