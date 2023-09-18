package io.homeassistant.companion.android.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FirebaseCloudMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "FCMService"
        private const val SOURCE = "FCM"
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var messagingManager: MessagingManager

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        messagingManager.handleMessage(remoteMessage.data, SOURCE)
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        mainScope.launch {
            Log.d(TAG, "Refreshed token: $token")
            if (!serverManager.isRegistered()) {
                Log.d(TAG, "Not trying to update registration since we aren't authenticated.")
                return@launch
            }
            serverManager.defaultServers.forEach {
                launch {
                    try {
                        serverManager.integrationRepository(it.id).updateRegistration(
                            deviceRegistration = DeviceRegistration(pushToken = token),
                            allowReregistration = false
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue updating token", e)
                    }
                }
            }
        }
    }
}
