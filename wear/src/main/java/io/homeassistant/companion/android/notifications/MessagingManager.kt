package io.homeassistant.companion.android.notifications

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.notifications.DeviceCommandData
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.common.notifications.commandBeaconMonitor
import io.homeassistant.companion.android.common.notifications.commandBleTransmitter
import io.homeassistant.companion.android.common.notifications.createAction
import io.homeassistant.companion.android.common.notifications.createActionEventIntent
import io.homeassistant.companion.android.common.notifications.createNotificationActionItems
import io.homeassistant.companion.android.common.notifications.createPendingIntent
import io.homeassistant.companion.android.common.notifications.getGroupNotificationBuilder
import io.homeassistant.companion.android.common.notifications.handleChannel
import io.homeassistant.companion.android.common.notifications.handleReplyHistory
import io.homeassistant.companion.android.common.notifications.handleSmallIcon
import io.homeassistant.companion.android.common.notifications.handleText
import io.homeassistant.companion.android.common.util.TextToSpeechData
import io.homeassistant.companion.android.common.util.cancelGroupIfNeeded
import io.homeassistant.companion.android.common.util.getActiveNotification
import io.homeassistant.companion.android.common.util.speakText
import io.homeassistant.companion.android.common.util.stopTTS
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.database.sensor.SensorDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.json.JSONObject
import javax.inject.Inject

class MessagingManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val serverManager: ServerManager,
    private val sensorDao: SensorDao,
    private val notificationDao: NotificationDao,
) {

    companion object {
        const val TAG = "MessagingManager"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    fun handleMessage(notificationData: Map<String, String>, source: String) {

        var now = System.currentTimeMillis()
        var jsonData = notificationData
        val notificationId: Long

        if (source.startsWith(NotificationData.SOURCE_REPLY)) {
            notificationId = source.substringAfter(NotificationData.SOURCE_REPLY).toLong()
            notificationDao.get(notificationId.toInt())?.let {
                val dbData: Map<String, String> = jacksonObjectMapper().readValue(it.data)

                now = it.received // Allow for updating the existing notification without a tag
                jsonData = jsonData + dbData // Add the notificationData, this contains the reply text
            } ?: return
        } else {
            val jsonObject = (notificationData as Map<*, *>?)?.let { JSONObject(it) }
            val receivedServer = jsonData[NotificationData.WEBHOOK_ID]?.let {
                serverManager.getServer(webhookId = it)?.id
            }
            val notificationRow =
                NotificationItem(
                    0,
                    now,
                    notificationData[NotificationData.MESSAGE].toString(),
                    jsonObject.toString(),
                    source,
                    receivedServer
                )
            notificationId = notificationDao.add(notificationRow)
        }

        when (notificationData[NotificationData.MESSAGE]) {
            DeviceCommandData.COMMAND_BEACON_MONITOR -> {
                if (!commandBeaconMonitor(context, notificationData)) {
                    sendNotification(notificationData, now)
                }
            }
            DeviceCommandData.COMMAND_BLE_TRANSMITTER -> {
                if (!commandBleTransmitter(context, notificationData, sensorDao, mainScope)) {
                    sendNotification(notificationData)
                }
            }
            TextToSpeechData.TTS -> speakText(context, notificationData)
            TextToSpeechData.COMMAND_STOP_TTS -> stopTTS()
            else -> sendNotification(notificationData, notificationId, now)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendNotification(data: Map<String, String>, id: Long? = null, received: Long? = null) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)

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

        val channelId = handleChannel(context, notificationManagerCompat, data)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)

        handleSmallIcon(context, notificationBuilder, data)

        handleText(notificationBuilder, data)

        handleActions(notificationBuilder, messageId, id, data)

        handleReplyHistory(notificationBuilder, data)

        notificationManagerCompat.apply {
            Log.d(TAG, "Show notification with tag \"$tag\" and id \"$messageId\"")
            notify(tag, messageId, notificationBuilder.build())
            if (!group.isNullOrBlank()) {
                Log.d(TAG, "Show group notification with tag \"$group\" and id \"$groupId\"")
                notify(group, groupId, getGroupNotificationBuilder(context, channelId, group, data).build())
            } else {
                if (previousGroup.isNotBlank()) {
                    Log.d(
                        TAG,
                        "Remove group notification with tag \"$previousGroup\" and id \"$previousGroupId\""
                    )
                    notificationManagerCompat.cancelGroupIfNeeded(previousGroup, previousGroupId)
                }
            }
        }
    }

    private fun handleActions(
        builder: NotificationCompat.Builder,
        messageId: Int,
        databaseId: Long?,
        data: Map<String, String>
    ) {
        for (i in 1..3) {
            if (data.containsKey("action_${i}_key")) {
                val notificationAction = createNotificationActionItems(data, i)
                val eventIntent = createActionEventIntent(
                    context,
                    data,
                    messageId,
                    notificationAction,
                    databaseId,
                    NotificationActionReceiver::class.java
                )

                when (notificationAction.key) {
                    NotificationData.REPLY -> {
                        val replyPendingIntent = createPendingIntent(
                            context,
                            messageId,
                            eventIntent,
                            "reply"
                        )
                        createAction(context, notificationAction, builder, replyPendingIntent, "reply")
                    }
                    else -> {
                        val actionPendingIntent = createPendingIntent(
                            context,
                            (notificationAction.title.hashCode() + System.currentTimeMillis()).toInt(),
                            eventIntent,
                            "action"
                        )
                        createAction(context, notificationAction, builder, actionPendingIntent, "action")
                    }
                }
            }
        }
    }
}
