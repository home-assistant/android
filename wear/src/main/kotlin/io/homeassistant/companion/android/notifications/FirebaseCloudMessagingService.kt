package io.homeassistant.companion.android.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.MessagingToken
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class FirebaseCloudMessagingService : FirebaseMessagingService() {
    companion object {
        private const val SOURCE = "FCM"
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var messagingManager: MessagingManager

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("From: ${remoteMessage.from} and data: ${remoteMessage.data}")

        messagingManager.handleMessage(remoteMessage.data, SOURCE)
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        mainScope.launch {
            Timber.d("Refreshed token: $token")
            if (!serverManager.isRegistered()) {
                Timber.d("Not trying to update registration since we aren't authenticated.")
                return@launch
            }
            serverManager.defaultServers.forEach {
                launch {
                    try {
                        serverManager.integrationRepository(it.id).updateRegistration(
                            deviceRegistration = DeviceRegistration(
                                pushToken = MessagingToken(token),
                                pushWebsocket = false,
                            ),
                            allowReregistration = false,
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Issue updating token")
                    }
                }
            }
        }
    }
}
