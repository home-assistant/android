package io.homeassistant.companion.android.notifications

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.notifications.DeviceCommandData
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.common.notifications.clearNotification
import io.homeassistant.companion.android.common.notifications.commandBeaconMonitor
import io.homeassistant.companion.android.common.notifications.commandBleTransmitter
import io.homeassistant.companion.android.common.notifications.getGroupNotificationBuilder
import io.homeassistant.companion.android.common.notifications.handleChannel
import io.homeassistant.companion.android.common.notifications.handleDeleteIntent
import io.homeassistant.companion.android.common.notifications.handleSmallIcon
import io.homeassistant.companion.android.common.notifications.handleText
import io.homeassistant.companion.android.common.util.cancelGroupIfNeeded
import io.homeassistant.companion.android.common.util.getActiveNotification
import io.homeassistant.companion.android.common.util.toJsonObject
import io.homeassistant.companion.android.common.util.tts.TextToSpeechClient
import io.homeassistant.companion.android.common.util.tts.TextToSpeechData
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.sensors.SensorReceiver
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class MessagingManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val serverManager: ServerManager,
    private val sensorDao: SensorDao,
    private val textToSpeechClient: TextToSpeechClient,
) {
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    fun handleMessage(notificationData: Map<String, String>, source: String) {
        mainScope.launch {
            val notificationDao = AppDatabase.getInstance(context).notificationDao()
            val now = System.currentTimeMillis()

            val jsonData = notificationData as Map<String, String>?
            val jsonObject = jsonData?.toJsonObject()
            val serverId = jsonData?.get(NotificationData.WEBHOOK_ID)?.let {
                serverManager.getServer(webhookId = it)?.id
            } ?: ServerManager.SERVER_ID_ACTIVE
            val notificationRow =
                NotificationItem(
                    0,
                    now,
                    notificationData[NotificationData.MESSAGE].toString(),
                    jsonObject.toString(),
                    source,
                    serverId,
                )
            notificationDao.add(notificationRow)
            if (serverManager.getServer(serverId) == null) {
                Timber.w("Received notification but no server for it, discarding")
                return@launch
            }

            val allowCommands = serverManager.integrationRepository(serverId).isTrusted()
            val message = notificationData[NotificationData.MESSAGE]
            when {
                message == NotificationData.CLEAR_NOTIFICATION && !notificationData["tag"].isNullOrBlank() -> {
                    clearNotification(context, notificationData["tag"]!!)
                }
                message == DeviceCommandData.COMMAND_BEACON_MONITOR && allowCommands -> {
                    if (!commandBeaconMonitor(context, notificationData)) {
                        sendNotification(notificationData, now)
                    }
                }
                message == DeviceCommandData.COMMAND_BLE_TRANSMITTER && allowCommands -> {
                    if (!commandBleTransmitter(context, notificationData, sensorDao)) {
                        sendNotification(notificationData)
                    }
                }
                message == TextToSpeechData.TTS -> textToSpeechClient.speakText(notificationData)
                message == TextToSpeechData.COMMAND_STOP_TTS -> textToSpeechClient.stopTTS()
                message == DeviceCommandData.COMMAND_UPDATE_SENSORS -> SensorReceiver.updateAllSensors(context)
                else -> sendNotification(notificationData, now)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendNotification(data: Map<String, String>, received: Long? = null) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)

        val tag = data["tag"].takeIf { !it.isNullOrBlank() }
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

        handleDeleteIntent(context, notificationBuilder, data, messageId, group, groupId, null)

        notificationManagerCompat.apply {
            Timber.d("Show notification with tag \"$tag\" and id \"$messageId\"")
            notify(tag, messageId, notificationBuilder.build())
            if (!group.isNullOrBlank()) {
                Timber.d("Show group notification with tag \"$group\" and id \"$groupId\"")
                notify(group, groupId, getGroupNotificationBuilder(context, channelId, group, data).build())
            } else {
                if (previousGroup.isNotBlank()) {
                    Timber.d(
                        "Remove group notification with tag \"$previousGroup\" and id \"$previousGroupId\"",
                    )
                    notificationManagerCompat.cancelGroupIfNeeded(previousGroup, previousGroupId)
                }
            }
        }
    }
}
