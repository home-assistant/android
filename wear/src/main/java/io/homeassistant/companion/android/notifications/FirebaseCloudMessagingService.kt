package io.homeassistant.companion.android.notifications

import android.annotation.SuppressLint
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.common.notifications.getGroupNotificationBuilder
import io.homeassistant.companion.android.common.notifications.handleChannel
import io.homeassistant.companion.android.common.notifications.handleSmallIcon
import io.homeassistant.companion.android.common.notifications.handleText
import io.homeassistant.companion.android.common.util.cancelGroupIfNeeded
import io.homeassistant.companion.android.common.util.getActiveNotification
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.notification.NotificationItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
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

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from} and data: ${remoteMessage.data}")

        val notificationDao = AppDatabase.getInstance(applicationContext).notificationDao()
        var now = System.currentTimeMillis()
        var jsonData = remoteMessage.data
        val notificationId: Long

        val jsonObject = (jsonData as Map<*, *>?)?.let { JSONObject(it) }
        val notificationRow =
            NotificationItem(0, now, jsonData[NotificationData.MESSAGE].toString(), jsonObject.toString(), SOURCE)
        notificationId = notificationDao.add(notificationRow)

        sendNotification(jsonData, notificationId, now)
    }

    @SuppressLint("MissingPermission")
    private fun sendNotification(data: Map<String, String>, id: Long? = null, received: Long? = null) {
        val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)

        val tag = data["tag"]
        val messageId = tag?.hashCode() ?: received?.toInt() ?: System.currentTimeMillis().toInt()

        var group = data["group"]
        var groupId = 0
        var previousGroup = ""
        var previousGroupId = 0
        if (!group.isNullOrBlank()) {
            group = NotificationData.GROUP_PREFIX + group
            groupId = group.hashCode()
        } else {
            val notification = notificationManagerCompat.getActiveNotification(tag, messageId)
            if (notification != null && notification.isGroup) {
                previousGroup = NotificationData.GROUP_PREFIX + notification.tag
                previousGroupId = previousGroup.hashCode()
            }
        }

        val channelId = handleChannel(applicationContext, notificationManagerCompat, data)

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)

        handleSmallIcon(applicationContext, notificationBuilder, data)

        handleText(notificationBuilder, data)

        notificationManagerCompat.apply {
            Log.d(TAG, "Show notification with tag \"$tag\" and id \"$messageId\"")
            notify(tag, messageId, notificationBuilder.build())
            if (!group.isNullOrBlank()) {
                Log.d(TAG, "Show group notification with tag \"$group\" and id \"$groupId\"")
                notify(group, groupId, getGroupNotificationBuilder(applicationContext, channelId, group, data).build())
            } else {
                if (!previousGroup.isBlank()) {
                    Log.d(
                        TAG,
                        "Remove group notification with tag \"$previousGroup\" and id \"$previousGroupId\""
                    )
                    notificationManagerCompat.cancelGroupIfNeeded(previousGroup, previousGroupId)
                }
            }
        }
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
                        pushToken = token
                    )
                )
            } catch (e: Exception) {
                // TODO: Store for update later
                Log.e(TAG, "Issue updating token", e)
            }
        }
    }
}
