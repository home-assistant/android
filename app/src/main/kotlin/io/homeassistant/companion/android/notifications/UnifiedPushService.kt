package io.homeassistant.companion.android.notifications

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import timber.log.Timber

@AndroidEntryPoint
class UnifiedPushService : PushService() {
    companion object {
        private const val SOURCE = "UnifiedPush"
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var messagingManager: MessagingManager

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onMessage(message: PushMessage, instance: String) {
        Timber.d("From: $instance")

        val data: Map<String, Any> = jacksonObjectMapper().readValue(message.content)
        messagingManager.handleMessage(data, SOURCE)
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        mainScope.launch {
            Timber.d("New Endpoint: ${endpoint.url} for $instance")
            if (!serverManager.isRegistered()) {
                Timber.d("Not trying to update registration since we aren't authenticated.")
                return@launch
            }
            serverManager.defaultServers.forEach {
                launch {
                    try {
                        serverManager.integrationRepository(it.id).updateRegistration(
                            deviceRegistration = DeviceRegistration(pushUrl = endpoint.url),
                            allowReregistration = false
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Issue updating push url")
                    }
                }
            }
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Timber.e("Issue registering: $reason")
    }

    override fun onUnregistered(instance: String) {
        mainScope.launch {
            Timber.d("Unregistered: $instance")
            if (!serverManager.isRegistered()) {
                Timber.d("Not trying to update registration since we aren't authenticated.")
                return@launch
            }
            serverManager.defaultServers.forEach {
                launch {
                    try {
                        serverManager.integrationRepository(it.id).updateRegistration(
                            deviceRegistration = DeviceRegistration(pushUrl = null),
                            allowReregistration = false
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Issue updating push url")
                    }
                }
            }
        }
    }
}