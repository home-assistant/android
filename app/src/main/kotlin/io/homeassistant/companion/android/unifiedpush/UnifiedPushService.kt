package io.homeassistant.companion.android.unifiedpush

import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.notifications.MessagingManager
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import timber.log.Timber

/**
 * UnifiedPush service implementation handling push notification delivery.
 *
 * Extends [PushService] (the recommended base class as of connector 3.0.x) rather than
 * the deprecated `MessagingReceiver`. The library embeds its own receiver and forwards
 * events to this service via the `org.unifiedpush.android.connector.PUSH_EVENT` action.
 */
@AndroidEntryPoint
class UnifiedPushService : PushService() {

    @Inject
    lateinit var unifiedPushManager: UnifiedPushManager

    @Inject
    lateinit var messagingManager: MessagingManager

    override fun onMessage(message: PushMessage, instance: String) {
        Timber.d("From: $instance")

        try {
            val jsonObject = Json.decodeFromString<JsonObject>(message.content.decodeToString())
            val data: Map<String, Any> = jsonObject.mapValues { (_, value) ->
                if (value is JsonPrimitive) {
                    value.contentOrNull ?: value.toString()
                } else {
                    value.toString()
                }
            }
            messagingManager.handleMessage(data, SOURCE)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse UnifiedPush message")
        }
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Timber.d("New Endpoint: ${endpoint.url} for $instance")
        unifiedPushManager.updateEndpoint(endpoint)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Timber.e("Issue registering: $reason")
        unifiedPushManager.onRegistrationFailed(reason)
    }

    override fun onUnregistered(instance: String) {
        Timber.d("Unregistered: $instance")
        unifiedPushManager.saveDistributor(null)
    }

    companion object {
        const val SOURCE = "UnifiedPush"
    }
}
