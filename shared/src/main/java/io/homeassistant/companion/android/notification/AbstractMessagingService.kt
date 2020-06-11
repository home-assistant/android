package io.homeassistant.companion.android.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
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
import io.homeassistant.companion.android.util.extensions.parseVibrationPattern
import io.homeassistant.companion.android.util.extensions.saveChannel
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import kotlin.collections.HashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

abstract class AbstractMessagingService : FirebaseMessagingService() {

    companion object {
        const val TAG = "MessagingService"
        const val TITLE = "title"
        const val MESSAGE = "message"
        const val SUBJECT = "subject"
        const val IMPORTANCE = "importance"
        const val TIMEOUT = "timeout"
        const val IMAGE_URL = "image"
        const val ICON_URL = "icon_url"
        const val COLOR = "color"
        const val LED_COLOR = "ledColor"
        const val VIBRATION_PATTERN = "vibrationPattern"
        const val PERSISTENT = "persistent"
        const val LOCAL_ONLY = "localOnly"

        // special action constants
        const val REQUEST_LOCATION_UPDATE = "request_location_update"
        const val CLEAR_NOTIFICATION = "clear_notification"
        const val REMOVE_CHANNEL = "remove_channel"
    }

    @Inject lateinit var integrationUseCase: IntegrationUseCase
    @Inject lateinit var urlUseCase: UrlUseCase
    @Inject lateinit var authenticationUseCase: AuthenticationUseCase

    override fun onCreate() {
        super.onCreate()
        val graphAccessor = applicationContext as GraphComponentAccessor
        DaggerNotificationComponent.factory().create(graphAccessor.appComponent).inject(this)
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

        val group = data["group"]
        val messageId = notificationTag?.hashCode() ?: System.currentTimeMillis().toInt()
        val groupId = group?.hashCode() ?: 0

        val pendingIntent = handleIntent(notificationTag, messageId, data["clickAction"])

        val channelId = handleChannel(
            data["channel"], data[IMPORTANCE], data[LED_COLOR], data[VIBRATION_PATTERN]
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
            .setDefaults(Notification.DEFAULT_ALL)

        val stickyNotification = data["sticky"]?.toBoolean() ?: false

        handleColor(notificationBuilder, data[COLOR])
        handleSticky(notificationBuilder, stickyNotification)
        handleText(notificationBuilder, data[TITLE], data[SUBJECT] ?: data[MESSAGE], data[MESSAGE])
        handleLocalOnly(notificationBuilder, data[LOCAL_ONLY]?.toBoolean() ?: false)
        handlePersistent(notificationBuilder, notificationTag, data[PERSISTENT]?.toBoolean() ?: false)
        handleLargeIcon(notificationBuilder, data[ICON_URL] ?: "")
        handleGroup(notificationBuilder, group)
        handleTimeout(notificationBuilder, data[TIMEOUT]?.toLong()?.times(1000))

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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            handleLegacyPriority(notificationBuilder, data[IMPORTANCE])
            handleLegacyLedColor(notificationBuilder, data[LED_COLOR])
            handleLegacyVibrationPattern(notificationBuilder, data[VIBRATION_PATTERN])
        }

        notificationManager.apply {
            notify(notificationTag, messageId, notificationBuilder.build())
            if (group != null) {
                notify(group, groupId, getGroupNotificationBuilder(channelId, group, data[COLOR])
                    .build())
            }
        }
    }

    private fun handlePersistent(
        builder: NotificationCompat.Builder,
        tag: String?,
        persistent: Boolean
    ) {
        // Only set ongoing (persistent) property if tag was supplied.
        // Without a tag the user could not clear the notification
        if (!tag.isNullOrBlank()) {
            builder.setOngoing(persistent)
        }
    }

    private fun getGroupNotificationBuilder(
        channelId: String,
        group: String?,
        colorString: String?
    ): NotificationCompat.Builder {
        val groupNotificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(group))
            .setGroup(group)
            .setGroupSummary(true)

        handleColor(groupNotificationBuilder, colorString)
        return groupNotificationBuilder
    }

    protected abstract fun handleIntent(
        notificationTag: String?,
        messageId: Int,
        actionUrl: String?
    ): PendingIntent

    private fun handleColor(builder: NotificationCompat.Builder, colorString: String?) {
        builder.color = parseColor(colorString, R.color.colorPrimary)
    }

    private fun parseColor(colorString: String?, default: Int): Int {
        if (!colorString.isNullOrBlank()) {
            try {
                return Color.parseColor(colorString)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to parse color", e)
            }
        }
        return ContextCompat.getColor(this, default)
    }

    private fun handleLegacyLedColor(builder: NotificationCompat.Builder, ledColor: String?) {
        if (!ledColor.isNullOrBlank()) {
            builder.setLights(parseColor(ledColor, R.color.colorPrimary), 3000, 3000)
        }
    }

    private fun handleLegacyVibrationPattern(
        builder: NotificationCompat.Builder,
        vibrationPattern: String?
    ) {
        val arrVibrationPattern = vibrationPattern.parseVibrationPattern()
        if (arrVibrationPattern.isNotEmpty()) {
            builder.setVibrate(arrVibrationPattern)
        }
    }

    private fun handleLegacyPriority(builder: NotificationCompat.Builder, importance: String?) {
        // Use importance property for legacy priority support
        builder.priority = when (importance) {
            "high" -> NotificationCompat.PRIORITY_HIGH
            "low" -> NotificationCompat.PRIORITY_LOW
            "max" -> NotificationCompat.PRIORITY_MAX
            "min" -> NotificationCompat.PRIORITY_MIN
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
    }

    private fun handleSticky(builder: NotificationCompat.Builder, sticky: Boolean) {
        builder.setAutoCancel(!sticky)
    }

    private fun handleLocalOnly(builder: NotificationCompat.Builder, localOnly: Boolean) {
        builder.setLocalOnly(localOnly)
    }

    private fun handleText(
        builder: NotificationCompat.Builder,
        title: String?,
        subject: String?,
        message: String?
    ) {
        builder
            .setContentTitle(title)
            .setContentText(subject ?: message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                HtmlCompat.fromHtml(message ?: "Unspecified",
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )))
    }

    private fun handleTimeout(builder: NotificationCompat.Builder, timeout: Long?) {
        if (timeout != null && timeout >= 0) {
            builder.setTimeoutAfter(timeout)
        }
    }

    private fun handleGroup(builder: NotificationCompat.Builder, group: String?) {
        if (!group.isNullOrBlank()) {
            builder.setGroup(group)
        }
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

    private suspend fun handleLargeIcon(builder: NotificationCompat.Builder, iconUrl: String) {
        val url = urlUseCase.getUrl().handle(iconUrl) ?: return
        val bitmap = getImageBitmap(url, !iconUrl.isAbsoluteUrl()) ?: return
        builder.setLargeIcon(bitmap)
    }

    protected abstract fun actionHandler(): Class<*>

    private fun handleActions(
        builder: NotificationCompat.Builder,
        tag: String?,
        messageId: Int,
        sticky: Boolean,
        actions: List<NotificationAction>
    ) {
        actions.forEach { action ->
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

    private fun handleChannel(
        channel: String?,
        importance: String?,
        ledColor: String?,
        vibrationPattern: String?
    ): String {
        // Define some values for a default channel
        val channelID = createChannelID(channel ?: "general")
        val channelName = channel?.trim() ?: "General"
        saveChannel(
            channelID,
            channelName,
            parseColor(ledColor, R.color.colorPrimary),
            importance,
            vibrationPattern = vibrationPattern
        )
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
     *
     * This function is called from a worker thread so we can stay on the same thread to update
     * the registration with the (new) received token.
     */
    override fun onNewToken(token: String) {
        runBlocking {
            Log.d(TAG, "Refreshed token: $token")
            try {
                integrationUseCase.updateRegistration(pushToken = token)
            } catch (e: Exception) {
                // TODO: Store for update later
                Log.e(TAG, "Issue updating token", e)
            }
        }
    }
}
