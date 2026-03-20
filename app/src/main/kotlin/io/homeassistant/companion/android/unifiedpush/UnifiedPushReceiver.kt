package io.homeassistant.companion.android.unifiedpush

import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.notifications.MessagingManager
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import timber.log.Timber

@AndroidEntryPoint
class UnifiedPushReceiver : MessagingReceiver() {
    companion object {
        const val SOURCE = "UnifiedPush"
    }

    @Inject
    lateinit var unifiedPushManager: UnifiedPushManager

    @Inject
    lateinit var messagingManager: MessagingManager

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        Timber.d("From: $instance")

        try {
            val jsonObject = Json.decodeFromString<JsonObject>(message.content.decodeToString())
            val data: Map<String, Any> = jsonObject.mapValues { (_, value) ->
                value.jsonPrimitive.contentOrNull ?: value.toString()
            }
            messagingManager.handleMessage(data, SOURCE)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse UnifiedPush message")
        }
    }

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        Timber.d("New Endpoint: ${endpoint.url} for $instance")
        unifiedPushManager.updateEndpoint(endpoint)
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        Timber.e("Issue registering: $reason")
        unifiedPushManager.onRegistrationFailed(reason)
    }

    override fun onUnregistered(context: Context, instance: String) {
        Timber.d("Unregistered: $instance")
        unifiedPushManager.saveDistributor(null)
    }
}
