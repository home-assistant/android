package io.homeassistant.companion.android.notifications

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spanned
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.text.isDigitsOnly
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.toAndroidIconCompat
import com.vdurmont.emoji.EmojiParser
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.sensors.NotificationSensorManager
import io.homeassistant.companion.android.util.UrlHandler
import io.homeassistant.companion.android.util.cancel
import io.homeassistant.companion.android.util.cancelGroupIfNeeded
import io.homeassistant.companion.android.util.getActiveNotification
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import javax.inject.Inject
import kotlin.collections.HashMap
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
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
        const val CHRONOMETER = "chronometer"
        const val WHEN = "when"
        const val GROUP_PREFIX = "group_"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val ALERT_ONCE = "alert_once"
        const val INTENT_CLASS_NAME = "intent_class_name"
        const val NOTIFICATION_ICON = "notification_icon"

        // special action constants
        const val REQUEST_LOCATION_UPDATE = "request_location_update"
        const val CLEAR_NOTIFICATION = "clear_notification"
        const val REMOVE_CHANNEL = "remove_channel"
        const val TTS = "TTS"
        const val COMMAND_DND = "command_dnd"
        const val COMMAND_RINGER_MODE = "command_ringer_mode"
        const val COMMAND_BROADCAST_INTENT = "command_broadcast_intent"
        const val COMMAND_VOLUME_LEVEL = "command_volume_level"
        const val COMMAND_BLUETOOTH = "command_bluetooth"
        const val COMMAND_BLE_TRANSMITTER = "command_ble_transmitter"
        const val COMMAND_SCREEN_ON = "command_screen_on"
        const val COMMAND_MEDIA = "command_media"

        const val COMMAND_HIGH_ACCURACY_MODE = "command_high_accuracy_mode"
        const val COMMAND_ACTIVITY = "command_activity"
        const val COMMAND_WEBVIEW = "command_webview"

        // DND commands
        const val DND_PRIORITY_ONLY = "priority_only"
        const val DND_ALARMS_ONLY = "alarms_only"
        const val DND_ALL = "off"
        const val DND_NONE = "total_silence"

        // Ringer mode commands
        const val RM_NORMAL = "normal"
        const val RM_SILENT = "silent"
        const val RM_VIBRATE = "vibrate"

        // Channel streams
        const val ALARM_STREAM = "alarm_stream"
        const val ALARM_STREAM_MAX = "alarm_stream_max"
        const val MUSIC_STREAM = "music_stream"
        const val NOTIFICATION_STREAM = "notification_stream"
        const val RING_STREAM = "ring_stream"

        // Enable/Disable Commands
        const val TURN_ON = "turn_on"
        const val TURN_OFF = "turn_off"

        // Media Commands
        const val MEDIA_FAST_FORWARD = "fast_forward"
        const val MEDIA_NEXT = "next"
        const val MEDIA_PAUSE = "pause"
        const val MEDIA_PLAY = "play"
        const val MEDIA_PLAY_PAUSE = "play_pause"
        const val MEDIA_PREVIOUS = "previous"
        const val MEDIA_REWIND = "rewind"
        const val MEDIA_STOP = "stop"

        const val COMMAND_KEEP_SCREEN_ON = "keep_screen_on"

        // Command groups
        val DEVICE_COMMANDS = listOf(
            COMMAND_DND,
            COMMAND_RINGER_MODE,
            COMMAND_BROADCAST_INTENT,
            COMMAND_VOLUME_LEVEL,
            COMMAND_BLUETOOTH,
            COMMAND_BLE_TRANSMITTER,
            COMMAND_HIGH_ACCURACY_MODE,
            COMMAND_ACTIVITY,
            COMMAND_WEBVIEW,
            COMMAND_SCREEN_ON,
            COMMAND_MEDIA
        )
        val DND_COMMANDS = listOf(DND_ALARMS_ONLY, DND_ALL, DND_NONE, DND_PRIORITY_ONLY)
        val RM_COMMANDS = listOf(RM_NORMAL, RM_SILENT, RM_VIBRATE)
        val CHANNEL_VOLUME_STREAM =
            listOf(ALARM_STREAM, MUSIC_STREAM, NOTIFICATION_STREAM, RING_STREAM)
        val ENABLE_COMMANDS = listOf(TURN_OFF, TURN_ON)
        val MEDIA_COMMANDS = listOf(
            MEDIA_FAST_FORWARD, MEDIA_NEXT, MEDIA_PAUSE, MEDIA_PLAY,
            MEDIA_PLAY_PAUSE, MEDIA_PREVIOUS, MEDIA_REWIND, MEDIA_STOP
        )
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    @Inject
    lateinit var urlUseCase: UrlRepository

    @Inject
    lateinit var authenticationUseCase: AuthenticationRepository

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        remoteMessage.data.let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
            val jsonData: Map<String, String> = it
            val jsonObject = JSONObject(jsonData)
            val notificationDao = AppDatabase.getInstance(applicationContext).notificationDao()
            val now = System.currentTimeMillis()
            val notificationRow =
                NotificationItem(0, now, it[MESSAGE].toString(), jsonObject.toString())
            notificationDao.add(notificationRow)

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
                    Log.d(TAG, "Removing Notification channel ${it["channel"]}")
                    removeNotificationChannel(it["channel"]!!)
                }
                it[MESSAGE] == TTS -> {
                    Log.d(TAG, "Sending notification title to TTS")
                    speakNotification(it)
                }
                it[MESSAGE] in DEVICE_COMMANDS -> {
                    Log.d(TAG, "Processing device command")
                    when (it[MESSAGE]) {
                        COMMAND_DND -> {
                            if (it[TITLE] in DND_COMMANDS) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                    handleDeviceCommands(it)
                                else {
                                    mainScope.launch {
                                        Log.d(
                                            TAG,
                                            "Posting notification to device as it does not support DND commands"
                                        )
                                        sendNotification(it)
                                    }
                                }
                            } else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Invalid DND command received, posting notification to device"
                                    )
                                    sendNotification(it)
                                }
                            }
                        }
                        COMMAND_RINGER_MODE -> {
                            if (it[TITLE] in RM_COMMANDS) {
                                handleDeviceCommands(it)
                            } else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Invalid ringer mode command received, posting notification to device"
                                    )
                                    sendNotification(it)
                                }
                            }
                        }
                        COMMAND_BROADCAST_INTENT -> {
                            if (!it[TITLE].isNullOrEmpty() && !it["channel"].isNullOrEmpty())
                                handleDeviceCommands(it)
                            else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Invalid broadcast command received, posting notification to device"
                                    )
                                    sendNotification(it)
                                }
                            }
                        }
                        COMMAND_VOLUME_LEVEL -> {
                            if (!it["channel"].isNullOrEmpty() && it["channel"] in CHANNEL_VOLUME_STREAM &&
                                !it[TITLE].isNullOrEmpty() && it[TITLE]?.toIntOrNull() != null
                            )
                                handleDeviceCommands(it)
                            else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Invalid volume command received, posting notification to device"
                                    )
                                    sendNotification(it)
                                }
                            }
                        }
                        COMMAND_BLUETOOTH -> {
                            if (!it[TITLE].isNullOrEmpty() && it[TITLE] in ENABLE_COMMANDS)
                                handleDeviceCommands(it)
                            else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Invalid bluetooth command received, posting notification to device"
                                    )
                                    sendNotification(it)
                                }
                            }
                        }
                        COMMAND_BLE_TRANSMITTER -> {
                            if (!it[TITLE].isNullOrEmpty() && it[TITLE] in ENABLE_COMMANDS)
                                handleDeviceCommands(it)
                            else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Invalid ble transmitter command received, posting notification to device"
                                    )
                                    sendNotification(it)
                                }
                            }
                        }
                        COMMAND_HIGH_ACCURACY_MODE -> {
                            if (!it[TITLE].isNullOrEmpty() && it[TITLE] in ENABLE_COMMANDS)
                                handleDeviceCommands(it)
                            else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Invalid high accuracy mode command received, posting notification to device"
                                    )
                                }
                            }
                        }
                        COMMAND_ACTIVITY -> {
                            if (!it["tag"].isNullOrEmpty())
                                handleDeviceCommands(it)
                            else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Invalid activity command received, posting notification to device"
                                    )
                                    sendNotification(it)
                                }
                            }
                        }
                        COMMAND_WEBVIEW -> {
                            handleDeviceCommands(it)
                        }
                        COMMAND_SCREEN_ON -> {
                            handleDeviceCommands(it)
                        }
                        COMMAND_MEDIA -> {
                            if (!it[TITLE].isNullOrEmpty() && it[TITLE] in MEDIA_COMMANDS && !it["channel"].isNullOrEmpty()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                    handleDeviceCommands(it)
                                } else {
                                    mainScope.launch {
                                        Log.d(
                                            TAG,
                                            "Posting notification to device as it does not support media commands"
                                        )
                                        sendNotification(it)
                                    }
                                }
                            } else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Invalid media command received, posting notification to device"
                                    )
                                    sendNotification(it)
                                }
                            }
                        }
                        else -> Log.d(TAG, "No command received")
                    }
                }
                else -> mainScope.launch {
                    Log.d(TAG, "Creating notification with following data: $it")
                    sendNotification(it)
                }
            }
        }
    }

    private fun requestAccurateLocationUpdate() {
        val intent = Intent(this, LocationSensorManager::class.java)
        intent.action = LocationSensorManager.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE

        sendBroadcast(intent)
    }

    private fun clearNotification(tag: String) {
        val notificationManagerCompat = NotificationManagerCompat.from(this)

        val messageId = tag.hashCode()

        // Clear notification
        notificationManagerCompat.cancel(tag, messageId, true)
    }

    private fun removeNotificationChannel(channelName: String) {
        val notificationManagerCompat = NotificationManagerCompat.from(this)

        val channelID: String = createChannelID(channelName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelID != NotificationChannel.DEFAULT_CHANNEL_ID) {
            notificationManagerCompat.deleteNotificationChannel(channelID)
        }
    }

    private fun speakNotification(data: Map<String, String>) {
        var textToSpeech: TextToSpeech? = null
        var tts = data[TITLE]
        val audioManager =
            applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (tts.isNullOrEmpty())
            tts = getString(commonR.string.tts_no_title)
        textToSpeech = TextToSpeech(
            applicationContext
        ) {
            if (it == TextToSpeech.SUCCESS) {
                val listener = object : UtteranceProgressListener() {
                    override fun onStart(p0: String?) {
                        if (data["channel"] == ALARM_STREAM_MAX)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_ALARM,
                                maxAlarmVolume,
                                0
                            )
                    }

                    override fun onDone(p0: String?) {
                        textToSpeech?.stop()
                        textToSpeech?.shutdown()
                        if (data["channel"] == ALARM_STREAM_MAX)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_ALARM,
                                currentAlarmVolume,
                                0
                            )
                    }

                    override fun onError(p0: String?) {
                        textToSpeech?.stop()
                        textToSpeech?.shutdown()
                        if (data["channel"] == ALARM_STREAM_MAX)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_ALARM,
                                currentAlarmVolume,
                                0
                            )
                    }
                }
                textToSpeech?.setOnUtteranceProgressListener(listener)
                if (data["channel"] == ALARM_STREAM || data["channel"] == ALARM_STREAM_MAX) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                    textToSpeech?.setAudioAttributes(audioAttributes)
                }
                textToSpeech?.speak(tts, TextToSpeech.QUEUE_ADD, null, "")
                Log.d(TAG, "speaking notification")
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        getString(commonR.string.tts_error, tts),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun handleDeviceCommands(data: Map<String, String>) {
        val message = data[MESSAGE]
        val title = data[TITLE]
        when (message) {
            COMMAND_DND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val notificationManager =
                        applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    if (!notificationManager.isNotificationPolicyAccessGranted) {
                        notifyMissingPermission(data[MESSAGE].toString())
                    } else {
                        when (title) {
                            DND_ALARMS_ONLY -> notificationManager.setInterruptionFilter(
                                NotificationManager.INTERRUPTION_FILTER_ALARMS
                            )
                            DND_ALL -> notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                            DND_NONE -> notificationManager.setInterruptionFilter(
                                NotificationManager.INTERRUPTION_FILTER_NONE
                            )
                            DND_PRIORITY_ONLY -> notificationManager.setInterruptionFilter(
                                NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            )
                            else -> Log.d(TAG, "Skipping invalid command")
                        }
                    }
                }
            }
            COMMAND_RINGER_MODE -> {
                val audioManager =
                    applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val notificationManager =
                        applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    if (!notificationManager.isNotificationPolicyAccessGranted) {
                        notifyMissingPermission(data[MESSAGE].toString())
                    } else {
                        processRingerMode(audioManager, title)
                    }
                } else {
                    processRingerMode(audioManager, title)
                }
            }
            COMMAND_BROADCAST_INTENT -> {
                try {
                    val packageName = data["channel"]
                    val intent = Intent(title)
                    val extras = data["group"]
                    val className = data[INTENT_CLASS_NAME]
                    if (!extras.isNullOrEmpty()) {
                        addExtrasToIntent(intent, extras)
                    }
                    intent.`package` = packageName
                    if (!packageName.isNullOrEmpty() && !className.isNullOrEmpty())
                        intent.setClassName(packageName, className)
                    Log.d(TAG, "Sending broadcast intent")
                    applicationContext.sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to send broadcast intent please check command format", e)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            applicationContext,
                            commonR.string.broadcast_intent_error,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            COMMAND_VOLUME_LEVEL -> {
                val audioManager =
                    applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val notificationManager =
                        applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    if (!notificationManager.isNotificationPolicyAccessGranted) {
                        notifyMissingPermission(data[MESSAGE].toString())
                    } else {
                        processStreamVolume(
                            audioManager,
                            data["channel"].toString(),
                            title!!.toInt()
                        )
                    }
                } else {
                    processStreamVolume(
                        audioManager,
                        data["channel"].toString(),
                        title!!.toInt()
                    )
                }
            }
            COMMAND_BLUETOOTH -> {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (title == TURN_OFF)
                    bluetoothAdapter.disable()
                if (title == TURN_ON)
                    bluetoothAdapter.enable()
            }
            COMMAND_BLE_TRANSMITTER -> {
                if (title == TURN_OFF)
                    BluetoothSensorManager.enableDisableBLETransmitter(applicationContext, false)
                if (title == TURN_ON)
                    BluetoothSensorManager.enableDisableBLETransmitter(applicationContext, true)
            }
            COMMAND_HIGH_ACCURACY_MODE -> {
                if (title == TURN_OFF) {
                    LocationSensorManager.setHighAccuracyModeSetting(applicationContext, false)
                }
                if (title == TURN_ON) {
                    LocationSensorManager.setHighAccuracyModeSetting(applicationContext, true)
                }
                val intent = Intent(this, LocationSensorManager::class.java)
                intent.action = LocationSensorManager.ACTION_FORCE_HIGH_ACCURACY
                intent.putExtra("command", title)
                applicationContext.sendBroadcast(intent)
            }
            COMMAND_ACTIVITY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(applicationContext))
                        notifyMissingPermission(data[MESSAGE].toString())
                    else
                        processActivityCommand(data)
                } else
                    processActivityCommand(data)
            }
            COMMAND_WEBVIEW -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(applicationContext))
                        notifyMissingPermission(data[MESSAGE].toString())
                    else
                        openWebview(title)
                } else
                    openWebview(title)
            }
            COMMAND_SCREEN_ON -> {
                if (!title.isNullOrEmpty()) {
                    mainScope.launch {
                        integrationUseCase.setKeepScreenOnEnabled(
                            title == COMMAND_KEEP_SCREEN_ON
                        )
                    }
                }

                val powerManager =
                    applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "HomeAssistant::NotificationScreenOnWakeLock"
                )
                wakeLock.acquire(1 * 30 * 1000L /*30 seconds */)
                wakeLock.release()
            }
            COMMAND_MEDIA -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    if (!NotificationManagerCompat.getEnabledListenerPackages(applicationContext)
                        .contains(applicationContext.packageName)
                    )
                        notifyMissingPermission(data[MESSAGE].toString())
                    else {
                        processMediaCommand(data)
                    }
                }
            }
            else -> Log.d(TAG, "No command received")
        }
    }

    /**
     * Add Extra values to Intent.
     */
    private fun addExtrasToIntent(intent: Intent, extras: String) {
        val items = extras.split(',')
        for (item in items) {
            val chunks = item.split(":")
            var value = chunks[1]
            if (chunks.size > 2) {
                value = chunks.subList(1, chunks.lastIndex).joinToString(":")
                if (chunks.last() == "urlencoded")
                    value = URLDecoder.decode(value, "UTF-8")
            }
            intent.putExtra(
                chunks[0],
                if (value.isDigitsOnly())
                    value.toInt()
                else if ((value.lowercase() == "true") ||
                    (value.lowercase() == "false")
                )
                    value.toBoolean()
                else value
            )
        }
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     */
    private suspend fun sendNotification(data: Map<String, String>) {
        val notificationManagerCompat = NotificationManagerCompat.from(this)

        val tag = data["tag"]
        val messageId = tag?.hashCode() ?: System.currentTimeMillis().toInt()

        var group = data["group"]
        var groupId = 0
        var previousGroup = ""
        var previousGroupId = 0
        if (!group.isNullOrBlank()) {
            group = GROUP_PREFIX + group
            groupId = group.hashCode()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

                val notification = notificationManagerCompat.getActiveNotification(tag, messageId)
                if (notification != null && notification.isGroup) {
                    previousGroup = GROUP_PREFIX + notification.tag
                    previousGroupId = previousGroup.hashCode()
                }
            }
        }

        val channelId = handleChannel(notificationManagerCompat, data)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)

        handleSmallIcon(notificationBuilder, data)

        handleSound(notificationBuilder, data)

        handlePersistent(notificationBuilder, tag, data)

        handleLargeIcon(notificationBuilder, data)

        handleGroup(notificationBuilder, group, data[ALERT_ONCE].toBoolean())

        handleTimeout(notificationBuilder, data)

        handleColor(notificationBuilder, data)

        handleSticky(notificationBuilder, data)

        handleText(notificationBuilder, data)

        handleSubject(notificationBuilder, data)

        handleImage(notificationBuilder, data)

        handleActions(notificationBuilder, tag, messageId, data)

        handleDeleteIntent(notificationBuilder, data, messageId, group, groupId)

        handleContentIntent(notificationBuilder, messageId, group, groupId, data)

        handleChronometer(notificationBuilder, data)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            handleLegacyPriority(notificationBuilder, data)
            handleLegacyLedColor(notificationBuilder, data)
            handleLegacyVibrationPattern(notificationBuilder, data)
        }

        notificationManagerCompat.apply {
            Log.d(TAG, "Show notification with tag \"$tag\" and id \"$messageId\"")
            notify(tag, messageId, notificationBuilder.build())
            if (group != null) {
                Log.d(TAG, "Show group notification with tag \"$group\" and id \"$groupId\"")
                notify(group, groupId, getGroupNotificationBuilder(channelId, group, data).build())
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

    private fun handleChronometer(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        try { // Without this, a non-numeric when value will crash the app
            val notificationWhen = data[WHEN]?.toLongOrNull()?.times(1000) ?: 0
            val usesChronometer = data[CHRONOMETER]?.toBoolean() ?: false

            if (notificationWhen != 0L) {
                builder.setWhen(notificationWhen)
                builder.setUsesChronometer(usesChronometer)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val countdown = notificationWhen > System.currentTimeMillis()
                    builder.addExtras(Bundle()) // Without this builder.setChronometerCountDown throws a null reference exception
                    builder.setChronometerCountDown(countdown)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while handling chronometer notification", e)
        }
    }

    private fun handleSmallIcon(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        if (data[NOTIFICATION_ICON]?.startsWith("mdi") == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val iconName = data[NOTIFICATION_ICON]!!.split(":")[1]
            val iconDrawable =
                IconicsDrawable(applicationContext, "cmd-$iconName").toAndroidIconCompat()
            builder.setSmallIcon(iconDrawable)
        } else
            builder.setSmallIcon(commonR.drawable.ic_stat_ic_notification)
    }

    private fun handleContentIntent(
        builder: NotificationCompat.Builder,
        messageId: Int,
        group: String?,
        groupId: Int,
        data: Map<String, String>
    ) {
        val actionUri = if (!data["clickAction"].isNullOrEmpty()) data["clickAction"] else "/"
        val contentIntent = Intent(this, NotificationContentReceiver::class.java).apply {
            putExtra(NotificationContentReceiver.EXTRA_NOTIFICATION_GROUP, group)
            putExtra(NotificationContentReceiver.EXTRA_NOTIFICATION_GROUP_ID, groupId)
            putExtra(NotificationContentReceiver.EXTRA_NOTIFICATION_ACTION_URI, actionUri)
        }
        val contentPendingIntent = PendingIntent.getBroadcast(
            this,
            messageId,
            contentIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(contentPendingIntent)
    }

    private fun handleDeleteIntent(
        builder: NotificationCompat.Builder,
        data: Map<String, String>,
        messageId: Int,
        group: String?,
        groupId: Int

    ) {

        val deleteIntent = Intent(this, NotificationDeleteReceiver::class.java).apply {
            putExtra(NotificationDeleteReceiver.EXTRA_DATA, HashMap(data))
            putExtra(NotificationDeleteReceiver.EXTRA_NOTIFICATION_GROUP, group)
            putExtra(NotificationDeleteReceiver.EXTRA_NOTIFICATION_GROUP_ID, groupId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            this,
            messageId,
            deleteIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setDeleteIntent(deletePendingIntent)
    }

    private fun handlePersistent(
        builder: NotificationCompat.Builder,
        tag: String?,
        data: Map<String, String>
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
        group: String,
        data: Map<String, String>
    ): NotificationCompat.Builder {

        val groupNotificationBuilder = NotificationCompat.Builder(this, channelId)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setSummaryText(
                        prepareText(group.substring(GROUP_PREFIX.length))
                    )
            )
            .setGroup(group)
            .setGroupSummary(true)

        if (data[ALERT_ONCE].toBoolean())
            groupNotificationBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        handleColor(groupNotificationBuilder, data)
        handleSmallIcon(groupNotificationBuilder, data)
        return groupNotificationBuilder
    }

    private fun handleSound(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        if (data["channel"] == ALARM_STREAM) {
            builder.setCategory(Notification.CATEGORY_ALARM)
            builder.setSound(
                RingtoneManager.getActualDefaultRingtoneUri(
                    applicationContext,
                    RingtoneManager.TYPE_ALARM
                )
                    ?: RingtoneManager.getActualDefaultRingtoneUri(
                        applicationContext,
                        RingtoneManager.TYPE_RINGTONE
                    ),
                AudioManager.STREAM_ALARM
            )
        } else {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }
        if (data[ALERT_ONCE].toBoolean())
            builder.setOnlyAlertOnce(true)
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

    @RequiresApi(Build.VERSION_CODES.N)
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
        val timeout = data[TIMEOUT]?.toLongOrNull()?.times(1000) ?: -1
        if (timeout >= 0) builder.setTimeoutAfter(timeout)
    }

    private fun handleGroup(
        builder: NotificationCompat.Builder,
        group: String?,
        alertOnce: Boolean?
    ) {
        if (!group.isNullOrBlank()) {
            builder.setGroup(group)
            if (alertOnce == true)
                builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        }
    }

    private fun handleSticky(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        val sticky = data["sticky"]?.toBoolean() ?: false
        builder.setAutoCancel(!sticky)
    }

    private fun handleSubject(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        data[SUBJECT]?.let {
            builder.setContentText(prepareText(it))
        }
    }

    private fun handleText(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        data[TITLE]?.let {
            builder.setContentTitle(prepareText(it))
        }
        data[MESSAGE]?.let {
            val text = prepareText(it)
            builder.setContentText(text)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
    }

    private fun prepareText(
        text: String
    ): Spanned {
        var emojiParsedText = EmojiParser.parseToUnicode(text)
        return HtmlCompat.fromHtml(emojiParsedText, HtmlCompat.FROM_HTML_MODE_LEGACY)
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

    private suspend fun getImageBitmap(url: URL?, requiresAuth: Boolean = false): Bitmap? =
        withContext(
            Dispatchers.IO
        ) {
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
        tag: String?,
        messageId: Int,
        data: Map<String, String>
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
                if (notificationAction.key != "REPLY") {
                    val actionPendingIntent = PendingIntent.getBroadcast(
                        this,
                        (notificationAction.title.hashCode() + System.currentTimeMillis()).toInt(),
                        actionIntent,
                        PendingIntent.FLAG_IMMUTABLE
                    )

                    val icon =
                        if (notificationAction.key == "URI")
                            R.drawable.ic_globe
                        else
                            commonR.drawable.ic_stat_ic_notification
                    builder.addAction(icon, notificationAction.title, actionPendingIntent)
                } else {
                    val remoteInput: RemoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).run {
                        setLabel(getString(commonR.string.action_reply))
                        build()
                    }
                    val replyPendingIntent = PendingIntent.getBroadcast(
                        this,
                        0,
                        actionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    val action: NotificationCompat.Action = NotificationCompat.Action.Builder(
                        R.drawable.ic_baseline_reply_24,
                        notificationAction.title,
                        replyPendingIntent
                    )
                        .addRemoteInput(remoteInput)
                        .build()
                    builder.addAction(action)
                }
            }
        }
    }

    private fun handleChannel(
        notificationManagerCompat: NotificationManagerCompat,
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

            if (channelName == ALARM_STREAM)
                handleChannelSound(channel)

            setChannelLedColor(data, channel)
            setChannelVibrationPattern(data, channel)
            notificationManagerCompat.createNotificationChannel(channel)
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleChannelSound(
        channel: NotificationChannel
    ) {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .setLegacyStreamType(AudioManager.STREAM_ALARM)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()
        channel.setSound(
            RingtoneManager.getActualDefaultRingtoneUri(
                applicationContext,
                RingtoneManager.TYPE_ALARM
            )
                ?: RingtoneManager.getActualDefaultRingtoneUri(
                    applicationContext,
                    RingtoneManager.TYPE_RINGTONE
                ),
            audioAttributes
        )
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
            .toLowerCase(Locale.ROOT)
            .replace(" ", "_")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestDNDPermission() {
        val intent =
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestSystemAlertPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun requestNotificationPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun getKeyEvent(key: String): Int {
        return when (key) {
            MEDIA_FAST_FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            MEDIA_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MEDIA_PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MEDIA_PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MEDIA_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MEDIA_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MEDIA_REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
            MEDIA_STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> 0
        }
    }

    private fun processMediaCommand(data: Map<String, String>) {
        val title = data[TITLE]
        val mediaSessionManager =
            applicationContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val mediaList = mediaSessionManager.getActiveSessions(
            ComponentName(
                applicationContext,
                NotificationSensorManager::class.java
            )
        )
        var hasCorrectPackage = false
        if (mediaList.size > 0) {
            for (item in mediaList) {
                if (item.packageName == data["channel"]) {
                    hasCorrectPackage = true
                    val mediaSessionController =
                        MediaController(applicationContext, item.sessionToken)
                    val success = mediaSessionController.dispatchMediaButtonEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN,
                            getKeyEvent(title!!)
                        )
                    )
                    if (!success) {
                        mainScope.launch {
                            Log.d(
                                TAG,
                                "Posting notification as the command was not sent to the session"
                            )
                            sendNotification(data)
                        }
                    }
                }
            }
            if (!hasCorrectPackage) {
                mainScope.launch {
                    Log.d(
                        TAG,
                        "Posting notification as the package is not found in the list of media sessions"
                    )
                    sendNotification(data)
                }
            }
        } else {
            mainScope.launch {
                Log.d(TAG, "Posting notification as there are no active media sessions")
                sendNotification(data)
            }
        }
    }

    private fun processRingerMode(audioManager: AudioManager, title: String?) {
        when (title) {
            RM_NORMAL -> audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            RM_SILENT -> audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            RM_VIBRATE -> audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            else -> Log.d(TAG, "Skipping invalid command")
        }
    }

    private fun processStreamVolume(audioManager: AudioManager, stream: String, volume: Int) {
        var volumeLevel = volume
        when (stream) {
            ALARM_STREAM -> {
                if (volumeLevel > audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM))
                    volumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                else if (volumeLevel < 0)
                    volumeLevel = 0
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    volumeLevel,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            MUSIC_STREAM -> {
                if (volumeLevel > audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                    volumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                else if (volumeLevel < 0)
                    volumeLevel = 0
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    volumeLevel,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            NOTIFICATION_STREAM -> {
                if (volumeLevel > audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION))
                    volumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                else if (volumeLevel < 0)
                    volumeLevel = 0
                audioManager.setStreamVolume(
                    AudioManager.STREAM_NOTIFICATION,
                    volumeLevel,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            RING_STREAM -> {
                if (volumeLevel > audioManager.getStreamMaxVolume(AudioManager.STREAM_RING))
                    volumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                else if (volumeLevel < 0)
                    volumeLevel = 0
                audioManager.setStreamVolume(
                    AudioManager.STREAM_RING,
                    volumeLevel,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            else -> Log.d(TAG, "Skipping command due to invalid channel stream")
        }
    }

    private fun processActivityCommand(data: Map<String, String>) {
        try {
            val packageName = data["channel"]
            val action = data["tag"]
            val className = data[INTENT_CLASS_NAME]
            val intentUri = if (!data[TITLE].isNullOrEmpty()) Uri.parse(data[TITLE]) else null
            val intent = if (intentUri != null) Intent(action, intentUri) else Intent(action)
            val type = data["subject"]
            if (!type.isNullOrEmpty())
                intent.type = type
            if (!className.isNullOrEmpty() && !packageName.isNullOrEmpty())
                intent.setClassName(packageName, className)
            val extras = data["group"]
            if (!extras.isNullOrEmpty()) {
                addExtrasToIntent(intent, extras)
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (!packageName.isNullOrEmpty()) {
                intent.setPackage(packageName)
                startActivity(intent)
            } else if (intent.resolveActivity(applicationContext.packageManager) != null)
                startActivity(intent)
            else
                mainScope.launch {
                    Log.d(
                        TAG,
                        "Posting notification as we do not have enough data to start the activity"
                    )
                    sendNotification(data)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to send activity intent please check command format", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    commonR.string.activity_intent_error,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openWebview(title: String?) {
        try {
            val intent = if (title.isNullOrEmpty())
                WebViewActivity.newInstance(applicationContext)
            else
                WebViewActivity.newInstance(applicationContext, title)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open webview", e)
        }
    }

    private fun notifyMissingPermission(type: String) {
        val appManager =
            applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val currentProcess = appManager.runningAppProcesses
        if (currentProcess != null) {
            for (item in currentProcess) {
                if (applicationContext.applicationInfo.processName == item.processName) {
                    if (item.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        val data =
                            mutableMapOf(MESSAGE to getString(commonR.string.missing_command_permission))
                        runBlocking {
                            sendNotification(data)
                        }
                    } else {
                        when (type) {
                            COMMAND_WEBVIEW, COMMAND_ACTIVITY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestSystemAlertPermission()
                            }
                            COMMAND_RINGER_MODE, COMMAND_DND, COMMAND_VOLUME_LEVEL -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestDNDPermission()
                            }
                            COMMAND_MEDIA -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                requestNotificationPermission()
                            }
                        }
                    }
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
