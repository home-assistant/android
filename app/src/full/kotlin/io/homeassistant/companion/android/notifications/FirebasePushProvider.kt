package io.homeassistant.companion.android.notifications

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import io.homeassistant.companion.android.common.notifications.PushProvider
import io.homeassistant.companion.android.database.settings.PushProviderSetting
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class FirebasePushProvider @Inject constructor(
    private val messagingManager: MessagingManager
) : PushProvider {

    companion object {
        const val SOURCE = "FCM"
    }

    override val setting = PushProviderSetting.FCM

    override fun isAvailable(context: Context): Boolean = true

    private var token: String? = null
    private val tokenMutex = Mutex()

    suspend fun setToken(token: String) = tokenMutex.withLock {
        this.token = token
    }

    override suspend fun getToken(): String {
        return tokenMutex.withLock { token } ?: try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Timber.e(e, "Issue getting token")
            ""
        }
    }

    override fun onMessage(context: Context, notificationData: Map<String, String>) {
        messagingManager.handleMessage(notificationData, SOURCE)
    }
}
