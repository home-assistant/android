package io.homeassistant.companion.android.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.homeassistant.companion.android.background.LocationBroadcastReceiver
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.notification.AbstractNotificationActionReceiver.Companion.EXTRA_NOTIFICATION_ACTION
import io.homeassistant.companion.android.notification.AbstractNotificationActionReceiver.Companion.EXTRA_NOTIFICATION_ID
import io.homeassistant.companion.android.notification.AbstractNotificationActionReceiver.Companion.EXTRA_NOTIFICATION_TAG
import io.homeassistant.companion.android.notification.AbstractNotificationActionReceiver.Companion.FIRE_EVENT
import io.homeassistant.companion.android.notification.AbstractNotificationActionReceiver.Companion.OPEN_URI
import io.homeassistant.companion.android.resources.R
import io.homeassistant.companion.android.util.extensions.handle
import io.homeassistant.companion.android.util.extensions.isAbsoluteUrl
import io.homeassistant.companion.android.util.extensions.notificationManager
import io.homeassistant.companion.android.util.extensions.saveChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashMap

abstract class AbstractMessagingService : FirebaseMessagingService() {

    companion object {
        const val TAG = "MessagingService"
        const val TITLE = "title"
        const val MESSAGE = "message"
        const val IMAGE_URL = "image"

        // special action constants
        const val REQUEST_LOCATION_UPDATE = "request_location_update"
        const val CLEAR_NOTIFICATION = "clear_notification"
        const val REMOVE_CHANNEL = "remove_channel"
    }

    @Inject lateinit var integrationUseCase: IntegrationUseCase
    @Inject lateinit var urlUseCase: UrlUseCase
    @Inject lateinit var authenticationUseCase: AuthenticationUseCase

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        val graphAccessor = applicationContext as GraphComponentAccessor

        DaggerNotificationComponent.factory()
            .create(graphAccessor.appComponent, graphAccessor.domainComponent)
            .inject(this)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage): Unit = runBlocking {
        Log.d(TAG, "From: ${remoteMessage.from}")

        val messageData = remoteMessage.data
        Log.d(TAG, "Message data payload: $messageData")

        val messageKey = messageData[MESSAGE]
        val notificationTag = messageData["tag"]
        val channel = messageData["channel"]
        when {
            messageKey == REQUEST_LOCATION_UPDATE -> requestAccurateLocationUpdate()
            messageKey == CLEAR_NOTIFICATION && !notificationTag.isNullOrBlank() -> clearNotification(notificationTag)
            messageKey == REMOVE_CHANNEL && !channel.isNullOrBlank() -> removeNotificationChannel(channel)
            else -> sendNotification(notificationTag, messageData)
        }
    }

    private fun requestAccurateLocationUpdate() {
        Log.d(TAG, "Request location update")
        sendBroadcast(
            Intent(this, LocationBroadcastReceiver::class.java)
                .setAction(LocationBroadcastReceiver.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE)
        )
    }

    private fun clearNotification(tag: String) {
        Log.d(TAG, "Clearing notification with tag: $tag")
        val messageId = tag.hashCode()
        notificationManager.cancel(tag, messageId)
    }

    private fun removeNotificationChannel(channelName: String) {
        val channelID: String = createChannelID(channelName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(channelID)
        }
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     */
    private suspend fun sendNotification(notificationTag: String?, data: Map<String, String>) {
        Log.d(TAG, "Creating notification with following data: $data")

        val messageId = notificationTag?.hashCode() ?: System.currentTimeMillis().toInt()

        val pendingIntent = handleIntent(notificationTag, messageId, data["clickAction"])

        val channelId = handleChannel(data["channel"])

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
            .setDefaults(Notification.DEFAULT_ALL)

        val stickyNotification = data["sticky"]?.toBoolean() ?: false

        handleColor(notificationBuilder, data["color"])
        handleSticky(notificationBuilder, stickyNotification)
        handleText(notificationBuilder, data[TITLE], data[MESSAGE])
        handleLocalOnly(notificationBuilder, data["localOnly"]?.toBoolean() ?: false)

        val imageUrl = data[IMAGE_URL]
        if (imageUrl != null) {
            handleImage(notificationBuilder, imageUrl)
        }

        val actions: List<NotificationAction> = (1..3).mapNotNull { actionKey ->
            val key = data["action_${actionKey}_key"] ?: return@mapNotNull null
            val title = data["action_${actionKey}_title"].toString()
            return@mapNotNull NotificationAction(key, title, data["action_${actionKey}_uri"], HashMap(data))
        }

        handleActions(notificationBuilder, notificationTag, messageId, stickyNotification, actions)

        if (notificationTag != null) {
            notificationManager.notify(notificationTag, messageId, notificationBuilder.build())
        } else {
            notificationManager.notify(messageId, notificationBuilder.build())
        }
    }

    protected abstract fun handleIntent(notificationTag: String?, messageId: Int, actionUrl: String?): PendingIntent

    private fun handleColor(builder: NotificationCompat.Builder, colorString: String?) {
        var color = ContextCompat.getColor(this, R.color.colorPrimary)

        if (!colorString.isNullOrBlank()) {
            try {
                color = Color.parseColor(colorString)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to parse color", e)
            }
        }

        builder.color = color
    }

    private fun handleSticky(builder: NotificationCompat.Builder, sticky: Boolean) {
        builder.setAutoCancel(!sticky)
    }

    private fun handleLocalOnly(builder: NotificationCompat.Builder, localOnly: Boolean) {
        builder.setLocalOnly(localOnly)
    }

    private fun handleText(builder: NotificationCompat.Builder, title: String?, message: String?) {
        builder
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
    }

    private suspend fun handleImage(builder: NotificationCompat.Builder, imageUrl: String) {
        val url = urlUseCase.getUrl().handle(imageUrl) ?: return
        val bitmap = getImageBitmap(url, !imageUrl.isAbsoluteUrl())
        if (bitmap != null) {
            builder
                .setLargeIcon(bitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null)
                )
        }
    }

    private suspend fun getImageBitmap(url: URL, requiresAuth: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            val uc = url.openConnection()
            if (requiresAuth) {
                uc.setRequestProperty("Authorization", authenticationUseCase.buildBearerToken())
            }
            BitmapFactory.decodeStream(uc.getInputStream())
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't download image for notification", e)
            null
        }
    }

    protected abstract fun actionHandler(): Class<*>

    private fun handleActions(
        builder: NotificationCompat.Builder,
        tag: String?,
        messageId: Int,
        sticky: Boolean,
        actions: List<NotificationAction>
    ) {
        if (actions.isEmpty()) {
            return
        }

        val isWearable = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        val extender = NotificationCompat.WearableExtender()
        if (isWearable) {
            builder.extend(extender.setDismissalId(tag).setContentAction(0))
        }

        actions.asSequence()
            .forEach { action ->
                val intent = Intent(this, actionHandler())
                    .setAction(if (action.key == "URI") OPEN_URI else FIRE_EVENT)
                    .putExtra(EXTRA_NOTIFICATION_ACTION, action)

                if (sticky) {
                    intent.putExtra(EXTRA_NOTIFICATION_TAG, tag).putExtra(EXTRA_NOTIFICATION_ID, messageId)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    (action.title.hashCode() + System.currentTimeMillis()).toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(0, action.title, pendingIntent)
            }
    }

    private fun handleChannel(channel: String?): String {
        // Define some values for a default channel
        val channelID = createChannelID(channel ?: "default")
        val channelName = channel?.trim() ?: "Default Channel"
        saveChannel(channelID, channelName)
        return channelID
    }

    private fun createChannelID(channelName: String): String {
        return channelName
            .trim()
            .toLowerCase(Locale.getDefault())
            .replace(" ", "_")
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        mainScope.launch {
            try {
                integrationUseCase.updateRegistration(pushToken = token)
            } catch (e: Exception) {
                // TODO: Store for update later
                Log.e(TAG, "Issue updating token", e)
            }
        }
    }
}
