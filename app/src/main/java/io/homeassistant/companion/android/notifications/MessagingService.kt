package io.homeassistant.companion.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.background.LocationBroadcastReceiver
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.util.UrlHandler
import io.homeassistant.companion.android.webview.WebViewActivity
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagingService : FirebaseMessagingService() {
    companion object {
        const val TAG = "MessagingService"
        const val TITLE = "title"
        const val MESSAGE = "message"
        const val SUBJECT = "subject"
        const val IMPORTANCE = "importance"
        const val TIMEOUT = "timeout"
        const val IMAGE_URL = "image"
        const val ICON_URL = "icon_url"
        const val LED_COLOR = "ledColor"
        const val VIBRATION_PATTERN = "vibrationPattern"
        const val PERSISTENT = "persistent"
        const val GROUP_PREFIX = "group_"

        // special action constants
        const val REQUEST_LOCATION_UPDATE = "request_location_update"
        const val CLEAR_NOTIFICATION = "clear_notification"
        const val REMOVE_CHANNEL = "remove_channel"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    @Inject
    lateinit var urlUseCase: UrlUseCase

    @Inject
    lateinit var authenticationUseCase: AuthenticationUseCase

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        DaggerServiceComponent.builder()
            .appComponent((applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        remoteMessage.data.let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)

            when {
                it[MESSAGE] == REQUEST_LOCATION_UPDATE -> {
                    Log.d(TAG, "Request location update")
                    requestAccurateLocationUpdate()
                }
                it[MESSAGE] == CLEAR_NOTIFICATION && !it["tag"].isNullOrBlank() -> {
                    Log.d(TAG, "Clearing notification with tag: ${it["tag"]}")
                    clearNotification(it["tag"]!!)
                }
                it[MESSAGE] == REMOVE_CHANNEL && !it["channel"].isNullOrBlank() -> {
                    Log.d(TAG, "Removing Notification channel ${it["tag"]}")
                    removeNotificationChannel(it["channel"]!!)
                }
                else -> mainScope.launch {
                    Log.d(TAG, "Creating notification with following data: $it")
                    sendNotification(it)
                }
            }
        }
    }

    private fun requestAccurateLocationUpdate() {
        val intent = Intent(this, LocationBroadcastReceiver::class.java)
        intent.action = LocationBroadcastReceiver.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE

        sendBroadcast(intent)
    }

    private fun clearNotification(tag: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val messageId = tag.hashCode()

        // Get group key from current notification
        // to handle possible group deletion
        var groupKey: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            groupKey =
                notificationManager.activeNotifications.singleOrNull { s -> s.tag == tag && s.isGroup }?.groupKey
        }

        // Clear notification
        notificationManager.cancel(tag, messageId)

        // Check if in group is no notification left.
        // If so, also clear the group
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!groupKey.isNullOrBlank()) {
                // Get notifications of the group
                val groupNotifications =
                    notificationManager.activeNotifications.filter { s -> s.groupKey == groupKey }

                // Only one left. That means. Just the group itself is left.
                // Then clear the group
                if (groupNotifications.size == 1) {
                    val group = groupNotifications[0].notification.group
                    val groupId = group.hashCode()
                    notificationManager.cancel(group, groupId)
                }
            }
        }
    }

    private fun removeNotificationChannel(channelName: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelID: String = createChannelID(channelName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(channelID)
        }
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     */
    private suspend fun sendNotification(data: Map<String, String>) {
        var group = data["group"]
        if (!group.isNullOrBlank()) group = GROUP_PREFIX + group

        val tag = data["tag"]
        val messageId = tag?.hashCode() ?: System.currentTimeMillis().toInt()
        val groupId = group?.hashCode() ?: 0

        val pendingIntent = handleIntent(data, messageId)

        val notificationManagerCompat = NotificationManagerCompat.from(this)

        val channelId = handleChannel(notificationManagerCompat, data)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        handlePersistent(notificationBuilder, data, tag)

        handleLargeIcon(notificationBuilder, data)

        handleGroup(notificationBuilder, group)

        handleTimeout(notificationBuilder, data)

        handleColor(notificationBuilder, data)

        handleSticky(notificationBuilder, data)

        handleText(notificationBuilder, data)

        handleImage(notificationBuilder, data)

        handleActions(notificationBuilder, data, tag, messageId)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            handleLegacyPriority(notificationBuilder, data)
            handleLegacyLedColor(notificationBuilder, data)
            handleLegacyVibrationPattern(notificationBuilder, data)
        }

        notificationManagerCompat.apply {
            notify(tag, messageId, notificationBuilder.build())
            if (group != null) {
                notify(group, groupId, getGroupNotificationBuilder(channelId, group, data).build())
            }
        }
    }

    private fun handlePersistent(
        builder: NotificationCompat.Builder,
        data: Map<String, String>,
        tag: String?
    ) {
        // Only set ongoing (persistent) property if tag was supplied.
        // Without a tag the user could not clear the notification
        if (!tag.isNullOrBlank()) {
            val persistent = data[PERSISTENT]?.toBoolean() ?: false
            builder.setOngoing(persistent)
        }
    }

    private fun getGroupNotificationBuilder(
        channelId: String,
        group: String?,
        data: Map<String, String>
    ): NotificationCompat.Builder {
        var groupNotificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setStyle(
                NotificationCompat.InboxStyle().setSummaryText(group)
            )
            .setGroup(group)
            .setGroupSummary(true)

        handleColor(groupNotificationBuilder, data)
        return groupNotificationBuilder
    }

    private fun handleIntent(
        data: Map<String, String>,
        messageId: Int
    ): PendingIntent {
        val url = data["clickAction"]

        val intent = if (UrlHandler.isAbsoluteUrl(url)) {
            Intent(Intent.ACTION_VIEW).apply {
                this.data = Uri.parse(url)
            }
        } else {
            WebViewActivity.newInstance(this, url)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return PendingIntent.getActivity(
            this, messageId, intent, 0
        )
    }

    private fun handleColor(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {

        val colorString = data["color"]
        val color = parseColor(colorString, R.color.colorPrimary)
        builder.color = color
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

    private fun handleLegacyLedColor(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        val ledColor = data[LED_COLOR]
        if (!ledColor.isNullOrBlank()) {
            builder.setLights(parseColor(ledColor, R.color.colorPrimary), 3000, 3000)
        }
    }

    private fun handleLegacyVibrationPattern(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        val vibrationPattern = data[VIBRATION_PATTERN]
        if (!vibrationPattern.isNullOrBlank()) {
            val arrVibrationPattern = parseVibrationPattern(vibrationPattern)
            if (arrVibrationPattern.isNotEmpty()) {
                builder.setVibrate(arrVibrationPattern)
            }
        }
    }

    private fun handleLegacyPriority(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {

        // Use importance property for legacy priority support
        val priority = data[IMPORTANCE]

        when (priority) {
            "high" -> {
                builder.priority = NotificationCompat.PRIORITY_HIGH
            }
            "low" -> {
                builder.priority = NotificationCompat.PRIORITY_LOW
            }
            "max" -> {
                builder.priority = NotificationCompat.PRIORITY_MAX
            }
            "min" -> {
                builder.priority = NotificationCompat.PRIORITY_MIN
            }
            else -> {
                builder.priority = NotificationCompat.PRIORITY_DEFAULT
            }
        }
    }

    private fun handleImportance(
        data: Map<String, String>
    ): Int {

        val importance = data[IMPORTANCE]

        when (importance) {
            "high" -> {
                return NotificationManager.IMPORTANCE_HIGH
            }
            "low" -> {
                return NotificationManager.IMPORTANCE_LOW
            }
            "max" -> {
                return NotificationManager.IMPORTANCE_MAX
            }
            "min" -> {
                return NotificationManager.IMPORTANCE_MIN
            }
            else -> {
                return NotificationManager.IMPORTANCE_DEFAULT
            }
        }
    }

    private fun handleTimeout(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        val timeout = data[TIMEOUT]?.toLong()?.times(1000) ?: -1
        if (timeout >= 0) builder.setTimeoutAfter(timeout)
    }

    private fun handleGroup(
        builder: NotificationCompat.Builder,
        group: String?
    ) {
        if (!group.isNullOrBlank()) {
            builder.setGroup(group)
        }
    }

    private fun handleSticky(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        val sticky = data["sticky"]?.toBoolean() ?: false
        builder.setAutoCancel(!sticky)
    }

    private fun handleText(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        builder
            .setContentTitle(data[TITLE])
            .setContentText(data[SUBJECT] ?: data[MESSAGE])
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        HtmlCompat.fromHtml(
                            data[MESSAGE] ?: "Unspecified",
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    )
            )
    }

    private suspend fun handleLargeIcon(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        data[ICON_URL]?.let {
            val url = UrlHandler.handle(urlUseCase.getUrl(), it)
            val bitmap = getImageBitmap(url, !UrlHandler.isAbsoluteUrl(it))
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }
        }
    }

    private suspend fun handleImage(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        data[IMAGE_URL]?.let {
            val url = UrlHandler.handle(urlUseCase.getUrl(), it)
            val bitmap = getImageBitmap(url, !UrlHandler.isAbsoluteUrl(it))
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
    }

    private suspend fun getImageBitmap(url: URL?, requiresAuth: Boolean = false): Bitmap? = withContext(
        Dispatchers.IO) {
        if (url == null)
            return@withContext null

        var image: Bitmap? = null
        try {
            val uc = url.openConnection()
            if (requiresAuth) {
                uc.setRequestProperty("Authorization", authenticationUseCase.buildBearerToken())
            }
            image = BitmapFactory.decodeStream(uc.getInputStream())
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't download image for notification", e)
        }
        return@withContext image
    }

    private fun handleActions(
        builder: NotificationCompat.Builder,
        data: Map<String, String>,
        tag: String?,
        messageId: Int
    ) {
        for (i in 1..3) {
            if (data.containsKey("action_${i}_key")) {
                val notificationAction = NotificationAction(
                    data["action_${i}_key"].toString(),
                    data["action_${i}_title"].toString(),
                    data["action_${i}_uri"],
                    data
                )
                val actionIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                    action =
                        if (notificationAction.key == "URI")
                            NotificationActionReceiver.OPEN_URI
                        else
                            NotificationActionReceiver.FIRE_EVENT
                    if (data["sticky"]?.toBoolean() != true) {
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_TAG, tag)
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, messageId)
                    }
                    putExtra(
                        NotificationActionReceiver.EXTRA_NOTIFICATION_ACTION,
                        notificationAction
                    )
                }
                val actionPendingIntent = PendingIntent.getBroadcast(
                    this,
                    (notificationAction.title.hashCode() + System.currentTimeMillis()).toInt(),
                    actionIntent,
                    0
                )

                builder.addAction(0, notificationAction.title, actionPendingIntent)
            }
        }
    }

    private fun handleChannel(
        notificationManager: NotificationManagerCompat,
        data: Map<String, String>
    ): String {
        // Define some values for a default channel
        var channelID = "general"
        var channelName = "General"

        if (data.containsKey("channel")) {
            channelID = createChannelID(data["channel"].toString())
            channelName = data["channel"].toString().trim()
        }

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelID,
                channelName,
                handleImportance(data)
            )

            setChannelLedColor(data, channel)
            setChannelVibrationPattern(data, channel)
            notificationManager.createNotificationChannel(channel)
        }
        return channelID
    }

    private fun setChannelLedColor(
        data: Map<String, String>,
        channel: NotificationChannel
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ledColor = data[LED_COLOR]
            if (!ledColor.isNullOrBlank()) {
                channel.enableLights(true)
                channel.lightColor = parseColor(ledColor, R.color.colorPrimary)
            }
        }
    }

    private fun setChannelVibrationPattern(
        data: Map<String, String>,
        channel: NotificationChannel
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationPattern = data[VIBRATION_PATTERN]
            val arrVibrationPattern = parseVibrationPattern(vibrationPattern)
            if (arrVibrationPattern.isNotEmpty()) {
                channel.vibrationPattern = arrVibrationPattern
            }
        }
    }

    private fun parseVibrationPattern(
        vibrationPattern: String?
    ): LongArray {
        if (!vibrationPattern.isNullOrBlank()) {
            val pattern = vibrationPattern.split(",").toTypedArray()
            val list = mutableListOf<Long>()
            pattern.forEach { it ->
                val ms = it.trim().toLongOrNull()
                if (ms != null) {
                    list.add(ms)
                }
            }
            if (list.count() > 0) {
                return list.toLongArray()
            }
        }
        return LongArray(0)
    }

    private fun createChannelID(
        channelName: String
    ): String {
        return channelName
            .trim()
            .toLowerCase()
            .replace(" ", "_")
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        mainScope.launch {
            Log.d(TAG, "Refreshed token: $token")
            try {
                integrationUseCase.updateRegistration(
                    pushToken = token
                )
            } catch (e: Exception) {
                // TODO: Store for update later
                Log.e(TAG, "Issue updating token", e)
            }
        }
    }
}
