package io.homeassistant.companion.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.background.LocationBroadcastReceiver
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.webview.WebViewActivity
import java.lang.Exception
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
        const val IMAGE_URL = "image"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

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

        val actions = ArrayList<NotificationAction>()
        // Check if message contains a data payload.
        remoteMessage.data.let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
            for (i in 1..3) {
                if (it.containsKey("action_${i}_key")) {
                    actions.add(
                        NotificationAction(
                            it["action_${i}_key"]!!,
                            it["action_${i}_title"].toString(),
                            it
                        )
                    )
                }
            }

            if (!it.containsKey(MESSAGE)) {
                Log.e(TAG, "Message missing from notification.")
                return
            }

            val title = it[TITLE]
            val message = it[MESSAGE]!!
            val imageUrl = it[IMAGE_URL]

            if (message == "request_location_update") {
                Log.d(TAG, "Request location update")
                if (actions.size != 0) {
                    Log.w(TAG, "Ignoring received actions since location update was requested")
                }
                requestAccurateLocationUpdate()
            } else {
                mainScope.launch {
                    Log.d(TAG, "Message Notification: $title -> $message")
                    sendNotification(title, message, imageUrl, actions)
                }
            }
        }
    }

    private fun requestAccurateLocationUpdate() {
        val intent = Intent(this, LocationBroadcastReceiver::class.java)
        intent.action = LocationBroadcastReceiver.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE

        sendBroadcast(intent)
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     * @param messageBody FCM message body received.
     */
    private suspend fun sendNotification(messageTitle: String?, messageBody: String, imageUrl: String?, actions: List<NotificationAction>) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT)

        // TODO: implement channels
        val channelId = "default"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
            .setContentTitle(messageTitle)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        if (imageUrl != null) {
            val bitmap = getImageBitmap(imageUrl)
            notificationBuilder
                .setLargeIcon(bitmap)
                .setStyle(NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null))
        }

        // TODO: This message id probably isn't the best
        val messageId = (messageBody + messageTitle + System.currentTimeMillis()).hashCode()

        actions.forEach {
            val actionIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.FIRE_EVENT
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, messageId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ACTION, it)
            }
            val actionPendingIntent = PendingIntent.getBroadcast(
                this,
                it.key.hashCode(),
                actionIntent,
                PendingIntent.FLAG_CANCEL_CURRENT
            )

            notificationBuilder.addAction(0, it.title, actionPendingIntent)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(messageId, notificationBuilder.build())
    }

    private suspend fun getImageBitmap(url: String): Bitmap = withContext(Dispatchers.IO) {
        return@withContext BitmapFactory.decodeStream(URL(url).openStream())
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
