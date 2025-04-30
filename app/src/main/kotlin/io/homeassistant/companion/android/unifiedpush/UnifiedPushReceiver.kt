package io.homeassistant.companion.android.unifiedpush

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.notifications.MessagingManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

        val data: Map<String, Any> = jacksonObjectMapper().readValue(message.content)
        messagingManager.handleMessage(data, SOURCE)
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
        unifiedPushManager.saveDistributor(UnifiedPushManager.DISTRIBUTOR_DISABLED)
        unifiedPushManager.updateEndpoint(null)
    }
}