package io.homeassistant.companion.android.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class FirebaseCloudMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushProvider: FirebaseCloudMessagingProvider

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("From: ${remoteMessage.from}")

        pushProvider.onMessage(this, remoteMessage.data)
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        mainScope.launch {
            pushProvider.setToken(token)
            pushProvider.updateRegistration(this@FirebaseCloudMessagingService, mainScope)
        }
    }
}
