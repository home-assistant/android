package io.homeassistant.companion.android.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
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
    lateinit var integrationUseCase: IntegrationRepository

    @Inject
    lateinit var authenticationUseCase: AuthenticationRepository

    @Inject
    lateinit var messagingManager: MessagingManager

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from} and data: ${remoteMessage.data}")

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
            if (authenticationUseCase.getSessionState() == SessionState.ANONYMOUS) {
                Log.d(TAG, "Not trying to update registration since we aren't authenticated.")
                return@launch
            }
            try {
                integrationUseCase.updateRegistration(
                    DeviceRegistration(
                        pushToken = token,
                        pushWebsocket = false
                    )
                )
            } catch (e: Exception) {
                // TODO: Store for update later
                Log.e(TAG, "Issue updating token", e)
            }
        }
    }
}
