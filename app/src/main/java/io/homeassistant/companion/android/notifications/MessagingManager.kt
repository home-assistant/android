package io.homeassistant.companion.android.notifications

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
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
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.text.HtmlCompat
import androidx.core.text.isDigitsOnly
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.toAndroidIconCompat
import com.vdurmont.emoji.EmojiParser
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.util.cancel
import io.homeassistant.companion.android.common.util.cancelGroupIfNeeded
import io.homeassistant.companion.android.common.util.generalChannel
import io.homeassistant.companion.android.common.util.getActiveNotification
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.sensors.NotificationSensorManager
import io.homeassistant.companion.android.sensors.SensorWorker
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.util.UrlHandler
import io.homeassistant.companion.android.websocket.WebsocketManager
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

class MessagingManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val okHttpClient: OkHttpClient,
    private val integrationUseCase: IntegrationRepository,
    private val urlUseCase: UrlRepository,
    private val authenticationUseCase: AuthenticationRepository,
    private val notificationDao: NotificationDao,
    private val sensorDao: SensorDao,
    private val settingsDao: SettingsDao
) {
    companion object {
        const val TAG = "MessagingService"

        const val APP_PREFIX = "app://"
        const val MARKET_PREFIX = "https://play.google.com/store/apps/details?id="
        const val SETTINGS_PREFIX = "settings://"
        const val NOTIFICATION_HISTORY = "notification_history"

        const val TITLE = "title"
        const val MESSAGE = "message"
        const val SUBJECT = "subject"
        const val IMPORTANCE = "importance"
        const val TIMEOUT = "timeout"
        const val IMAGE_URL = "image"
        const val ICON_URL = "icon_url"
        const val VIDEO_URL = "video"
        const val VISIBILITY = "visibility"
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
        const val URI = "URI"
        const val REPLY = "REPLY"
        const val BLE_ADVERTISE = "ble_advertise"
        const val BLE_TRANSMIT = "ble_transmit"
        const val HIGH_ACCURACY_UPDATE_INTERVAL = "high_accuracy_update_interval"
        const val PACKAGE_NAME = "package_name"
        const val COMMAND = "command"
        const val TTS_TEXT = "tts_text"
        const val CHANNEL = "channel"

        // special intent constants
        const val INTENT_PACKAGE_NAME = "intent_package_name"
        const val INTENT_ACTION = "intent_action"
        const val INTENT_EXTRAS = "intent_extras"
        const val INTENT_URI = "intent_uri"
        const val INTENT_TYPE = "intent_type"

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
        const val COMMAND_UPDATE_SENSORS = "command_update_sensors"
        const val COMMAND_HIGH_ACCURACY_MODE = "command_high_accuracy_mode"
        const val COMMAND_ACTIVITY = "command_activity"
        const val COMMAND_WEBVIEW = "command_webview"
        const val COMMAND_KEEP_SCREEN_ON = "keep_screen_on"
        const val COMMAND_LAUNCH_APP = "command_launch_app"
        const val COMMAND_PERSISTENT_CONNECTION = "command_persistent_connection"
        const val COMMAND_STOP_TTS = "command_stop_tts"

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
        const val SYSTEM_STREAM = "system_stream"
        const val CALL_STREAM = "call_stream"
        const val DTMF_STREAM = "dtmf_stream"

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
        const val MEDIA_PACKAGE_NAME = "media_package_name"
        const val MEDIA_COMMAND = "media_command"
        const val MEDIA_STREAM = "media_stream"

        // Ble Transmitter Commands
        const val BLE_SET_TRANSMIT_POWER = "ble_set_transmit_power"
        const val BLE_SET_ADVERTISE_MODE = "ble_set_advertise_mode"
        const val BLE_ADVERTISE_LOW_LATENCY = "ble_advertise_low_latency"
        const val BLE_ADVERTISE_BALANCED = "ble_advertise_balanced"
        const val BLE_ADVERTISE_LOW_POWER = "ble_advertise_low_power"
        const val BLE_TRANSMIT_ULTRA_LOW = "ble_transmit_ultra_low"
        const val BLE_TRANSMIT_LOW = "ble_transmit_low"
        const val BLE_TRANSMIT_MEDIUM = "ble_transmit_medium"
        const val BLE_TRANSMIT_HIGH = "ble_transmit_high"
        const val BLE_SET_UUID = "ble_set_uuid"
        const val BLE_SET_MAJOR = "ble_set_major"
        const val BLE_SET_MINOR = "ble_set_minor"
        const val BLE_UUID = "ble_uuid"
        const val BLE_MAJOR = "ble_major"
        const val BLE_MINOR = "ble_minor"

        // High accuracy commands
        const val HIGH_ACCURACY_SET_UPDATE_INTERVAL = "high_accuracy_set_update_interval"

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
            COMMAND_MEDIA,
            COMMAND_UPDATE_SENSORS,
            COMMAND_LAUNCH_APP,
            COMMAND_PERSISTENT_CONNECTION,
            COMMAND_STOP_TTS
        )
        val DND_COMMANDS = listOf(DND_ALARMS_ONLY, DND_ALL, DND_NONE, DND_PRIORITY_ONLY)
        val RM_COMMANDS = listOf(RM_NORMAL, RM_SILENT, RM_VIBRATE)
        val CHANNEL_VOLUME_STREAM = listOf(
            ALARM_STREAM, MUSIC_STREAM, NOTIFICATION_STREAM, RING_STREAM, CALL_STREAM,
            SYSTEM_STREAM, DTMF_STREAM
        )
        val ENABLE_COMMANDS = listOf(TURN_OFF, TURN_ON)
        val MEDIA_COMMANDS = listOf(
            MEDIA_FAST_FORWARD, MEDIA_NEXT, MEDIA_PAUSE, MEDIA_PLAY,
            MEDIA_PLAY_PAUSE, MEDIA_PREVIOUS, MEDIA_REWIND, MEDIA_STOP
        )
        val BLE_COMMANDS = listOf(
            BLE_SET_ADVERTISE_MODE, BLE_SET_TRANSMIT_POWER, BLE_SET_UUID, BLE_SET_MAJOR,
            BLE_SET_MINOR
        )
        val BLE_TRANSMIT_COMMANDS =
            listOf(BLE_TRANSMIT_HIGH, BLE_TRANSMIT_LOW, BLE_TRANSMIT_MEDIUM, BLE_TRANSMIT_ULTRA_LOW)
        val BLE_ADVERTISE_COMMANDS =
            listOf(BLE_ADVERTISE_BALANCED, BLE_ADVERTISE_LOW_LATENCY, BLE_ADVERTISE_LOW_POWER)

        // Video Values
        const val VIDEO_START_MICROSECONDS = 100000L
        const val VIDEO_INCREMENT_MICROSECONDS = 2000000L
        const val VIDEO_GUESS_MILLISECONDS = 7000L
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var textToSpeech: TextToSpeech? = null

    fun handleMessage(jsonData: Map<String, String>, source: String) {

        val jsonObject = JSONObject(jsonData)
        val now = System.currentTimeMillis()
        val notificationRow =
            NotificationItem(0, now, jsonData[MESSAGE].toString(), jsonObject.toString(), source)
        notificationDao.add(notificationRow)

        mainScope.launch {
            try {
                integrationUseCase.fireEvent("mobile_app_notification_received", jsonData)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to send notification received event", e)
            }
        }
        when {
            jsonData[MESSAGE] == REQUEST_LOCATION_UPDATE -> {
                Log.d(TAG, "Request location update")
                requestAccurateLocationUpdate()
            }
            jsonData[MESSAGE] == CLEAR_NOTIFICATION && !jsonData["tag"].isNullOrBlank() -> {
                Log.d(TAG, "Clearing notification with tag: ${jsonData["tag"]}")
                clearNotification(jsonData["tag"]!!)
            }
            jsonData[MESSAGE] == REMOVE_CHANNEL && !jsonData[CHANNEL].isNullOrBlank() -> {
                Log.d(TAG, "Removing Notification channel ${jsonData[CHANNEL]}")
                removeNotificationChannel(jsonData[CHANNEL]!!)
            }
            jsonData[MESSAGE] == TTS -> {
                Log.d(TAG, "Sending notification title to TTS")
                speakNotification(jsonData)
            }
            jsonData[MESSAGE] in DEVICE_COMMANDS -> {
                Log.d(TAG, "Processing device command")
                when (jsonData[MESSAGE]) {
                    COMMAND_DND -> {
                        if (jsonData[COMMAND] in DND_COMMANDS) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                handleDeviceCommands(jsonData)
                            else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Posting notification to device as it does not support DND commands"
                                    )
                                    sendNotification(jsonData)
                                }
                            }
                        } else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid DND command received, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_RINGER_MODE -> {
                        if (jsonData[COMMAND] in RM_COMMANDS) {
                            handleDeviceCommands(jsonData)
                        } else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid ringer mode command received, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_BROADCAST_INTENT -> {
                        if (!jsonData[INTENT_ACTION].isNullOrEmpty() && !jsonData[INTENT_PACKAGE_NAME].isNullOrEmpty())
                            handleDeviceCommands(jsonData)
                        else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid broadcast command received, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_VOLUME_LEVEL -> {
                        if (!jsonData[MEDIA_STREAM].isNullOrEmpty() && jsonData[MEDIA_STREAM] in CHANNEL_VOLUME_STREAM &&
                            !jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND]?.toIntOrNull() != null
                        )
                            handleDeviceCommands(jsonData)
                        else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid volume command received, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_BLUETOOTH -> {
                        if (!jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND] in ENABLE_COMMANDS)
                            handleDeviceCommands(jsonData)
                        else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid bluetooth command received, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_BLE_TRANSMITTER -> {
                        if (
                            (!jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND] in ENABLE_COMMANDS) ||
                            (
                                (!jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND] in BLE_COMMANDS) &&
                                    (
                                        (!jsonData[BLE_ADVERTISE].isNullOrEmpty() && jsonData[BLE_ADVERTISE] in BLE_ADVERTISE_COMMANDS) ||
                                            (!jsonData[BLE_TRANSMIT].isNullOrEmpty() && jsonData[BLE_TRANSMIT] in BLE_TRANSMIT_COMMANDS) ||
                                            (jsonData[COMMAND] == BLE_SET_UUID && !jsonData[BLE_UUID].isNullOrEmpty()) ||
                                            (jsonData[COMMAND] == BLE_SET_MAJOR && !jsonData[BLE_MAJOR].isNullOrEmpty()) ||
                                            (jsonData[COMMAND] == BLE_SET_MINOR && !jsonData[BLE_MINOR].isNullOrEmpty())
                                        )
                                )
                        )
                            handleDeviceCommands(jsonData)
                        else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid ble transmitter command received, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_HIGH_ACCURACY_MODE -> {
                        if ((!jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND] in ENABLE_COMMANDS) ||
                            (
                                !jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND] == HIGH_ACCURACY_SET_UPDATE_INTERVAL &&
                                    jsonData[HIGH_ACCURACY_UPDATE_INTERVAL]?.toIntOrNull() != null && jsonData[HIGH_ACCURACY_UPDATE_INTERVAL]?.toInt()!! >= 5
                                )
                        )
                            handleDeviceCommands(jsonData)
                        else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid high accuracy mode command received, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_ACTIVITY -> {
                        if (!jsonData[INTENT_ACTION].isNullOrEmpty())
                            handleDeviceCommands(jsonData)
                        else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid activity command received, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_WEBVIEW -> {
                        handleDeviceCommands(jsonData)
                    }
                    COMMAND_SCREEN_ON -> {
                        handleDeviceCommands(jsonData)
                    }
                    COMMAND_MEDIA -> {
                        if (!jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND] in MEDIA_COMMANDS && !jsonData[MEDIA_PACKAGE_NAME].isNullOrEmpty()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                handleDeviceCommands(jsonData)
                            } else {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Posting notification to device as it does not support media commands"
                                    )
                                    sendNotification(jsonData)
                                }
                            }
                        } else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid media command received, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_UPDATE_SENSORS -> SensorWorker.start(context)
                    COMMAND_LAUNCH_APP -> {
                        if (!jsonData[PACKAGE_NAME].isNullOrEmpty()) {
                            handleDeviceCommands(jsonData)
                        } else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Missing package name for app to launch, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_PERSISTENT_CONNECTION -> {
                        val validPersistentTypes = WebsocketSetting.values().map { setting -> setting.name }

                        when {
                            jsonData[PERSISTENT].isNullOrEmpty() -> {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Missing persistent modifier, posting notification to device"
                                    )
                                    sendNotification(jsonData)
                                }
                            }
                            jsonData[PERSISTENT]!!.uppercase() !in validPersistentTypes -> {
                                mainScope.launch {
                                    Log.d(
                                        TAG,
                                        "Persistent modifier is not one of $validPersistentTypes"
                                    )
                                    sendNotification(jsonData)
                                }
                            }
                            else -> handleDeviceCommands(jsonData)
                        }
                    }
                    COMMAND_STOP_TTS -> handleDeviceCommands(jsonData)
                    else -> Log.d(TAG, "No command received")
                }
            }
            else -> mainScope.launch {
                Log.d(TAG, "Creating notification with following data: $jsonData")
                sendNotification(jsonData)
            }
        }
    }

    private fun requestAccurateLocationUpdate() {
        val intent = Intent(context, LocationSensorManager::class.java)
        intent.action = LocationSensorManager.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE

        context.sendBroadcast(intent)
    }

    private fun clearNotification(tag: String) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)

        val messageId = tag.hashCode()

        // Clear notification
        notificationManagerCompat.cancel(tag, messageId, true)
    }

    private fun stopTTS() {
        Log.d(TAG, "Stopping TTS")
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    private fun removeNotificationChannel(channelName: String) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)

        val channelID: String = createChannelID(channelName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelID != NotificationChannel.DEFAULT_CHANNEL_ID) {
            notificationManagerCompat.deleteNotificationChannel(channelID)
        }
    }

    private fun speakNotification(data: Map<String, String>) {
        var tts = data[TTS_TEXT]
        val audioManager = context.getSystemService<AudioManager>()
        val currentAlarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxAlarmVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (tts.isNullOrEmpty())
            tts = context.getString(commonR.string.tts_no_title)
        textToSpeech = TextToSpeech(
            context
        ) {
            if (it == TextToSpeech.SUCCESS) {
                val listener = object : UtteranceProgressListener() {
                    override fun onStart(p0: String?) {
                        if (data[MEDIA_STREAM] == ALARM_STREAM_MAX)
                            audioManager?.setStreamVolume(
                                AudioManager.STREAM_ALARM,
                                maxAlarmVolume!!,
                                0
                            )
                    }

                    override fun onDone(p0: String?) {
                        textToSpeech?.stop()
                        textToSpeech?.shutdown()
                        if (data[MEDIA_STREAM] == ALARM_STREAM_MAX)
                            audioManager?.setStreamVolume(
                                AudioManager.STREAM_ALARM,
                                currentAlarmVolume!!,
                                0
                            )
                    }

                    override fun onError(p0: String?) {
                        textToSpeech?.stop()
                        textToSpeech?.shutdown()
                        if (data[MEDIA_STREAM] == ALARM_STREAM_MAX)
                            audioManager?.setStreamVolume(
                                AudioManager.STREAM_ALARM,
                                currentAlarmVolume!!,
                                0
                            )
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (data[MEDIA_STREAM] == ALARM_STREAM_MAX)
                            audioManager?.setStreamVolume(
                                AudioManager.STREAM_ALARM,
                                currentAlarmVolume!!,
                                0
                            )
                    }
                }
                textToSpeech?.setOnUtteranceProgressListener(listener)
                if (data[MEDIA_STREAM] == ALARM_STREAM || data[MEDIA_STREAM] == ALARM_STREAM_MAX) {
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
                        context,
                        context.getString(commonR.string.tts_error, tts),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun handleDeviceCommands(data: Map<String, String>) {
        val message = data[MESSAGE]
        val command = data[COMMAND]
        when (message) {
            COMMAND_DND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val notificationManager =
                        context.getSystemService<NotificationManager>()
                    if (notificationManager?.isNotificationPolicyAccessGranted == false) {
                        notifyMissingPermission(data[MESSAGE].toString())
                    } else {
                        when (command) {
                            DND_ALARMS_ONLY -> notificationManager?.setInterruptionFilter(
                                NotificationManager.INTERRUPTION_FILTER_ALARMS
                            )
                            DND_ALL -> notificationManager?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                            DND_NONE -> notificationManager?.setInterruptionFilter(
                                NotificationManager.INTERRUPTION_FILTER_NONE
                            )
                            DND_PRIORITY_ONLY -> notificationManager?.setInterruptionFilter(
                                NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            )
                            else -> Log.d(TAG, "Skipping invalid command")
                        }
                    }
                }
            }
            COMMAND_RINGER_MODE -> {
                val audioManager = context.getSystemService<AudioManager>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val notificationManager =
                        context.getSystemService<NotificationManager>()
                    if (notificationManager?.isNotificationPolicyAccessGranted == false) {
                        notifyMissingPermission(data[MESSAGE].toString())
                    } else {
                        processRingerMode(audioManager!!, command)
                    }
                } else {
                    processRingerMode(audioManager!!, command)
                }
            }
            COMMAND_BROADCAST_INTENT -> {
                try {
                    val packageName = data[INTENT_PACKAGE_NAME]
                    val intent = Intent(data[INTENT_ACTION])
                    val extras = data[INTENT_EXTRAS]
                    val className = data[INTENT_CLASS_NAME]
                    if (!extras.isNullOrEmpty()) {
                        addExtrasToIntent(intent, extras)
                    }
                    intent.`package` = packageName
                    if (!packageName.isNullOrEmpty() && !className.isNullOrEmpty())
                        intent.setClassName(packageName, className)
                    Log.d(TAG, "Sending broadcast intent")
                    context.sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to send broadcast intent please check command format", e)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            commonR.string.broadcast_intent_error,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            COMMAND_VOLUME_LEVEL -> {
                val audioManager =
                    context.getSystemService<AudioManager>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val notificationManager = context.getSystemService<NotificationManager>()
                    if (notificationManager?.isNotificationPolicyAccessGranted == false) {
                        notifyMissingPermission(data[MESSAGE].toString())
                    } else {
                        processStreamVolume(
                            audioManager!!,
                            data[MEDIA_STREAM].toString(),
                            command!!.toInt()
                        )
                    }
                } else {
                    processStreamVolume(
                        audioManager!!,
                        data[MEDIA_STREAM].toString(),
                        command!!.toInt()
                    )
                }
            }
            COMMAND_BLUETOOTH -> {
                val bluetoothAdapter = context.getSystemService<BluetoothManager>()?.adapter
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    when (PackageManager.PERMISSION_GRANTED) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) -> {
                            Log.d(TAG, "We have proper bluetooth permissions proceeding with command")
                        }
                        else -> {
                            Log.e(TAG, "Missing Bluetooth permissions, notifying user to grant permissions")
                            notifyMissingPermission(data[MESSAGE].toString())
                        }
                    }
                }
                if (command == TURN_OFF)
                    bluetoothAdapter?.disable()
                if (command == TURN_ON)
                    bluetoothAdapter?.enable()
            }
            COMMAND_BLE_TRANSMITTER -> {
                if (command == TURN_OFF)
                    BluetoothSensorManager.enableDisableBLETransmitter(context, false)
                if (command == TURN_ON)
                    BluetoothSensorManager.enableDisableBLETransmitter(context, true)
                if (command in BLE_COMMANDS) {
                    sensorDao.updateSettingValue(
                        BluetoothSensorManager.bleTransmitter.id,
                        when (command) {
                            BLE_SET_ADVERTISE_MODE -> BluetoothSensorManager.SETTING_BLE_ADVERTISE_MODE
                            BLE_SET_TRANSMIT_POWER -> BluetoothSensorManager.SETTING_BLE_TRANSMIT_POWER
                            BLE_SET_UUID -> BluetoothSensorManager.SETTING_BLE_ID1
                            BLE_SET_MAJOR -> BluetoothSensorManager.SETTING_BLE_ID2
                            BLE_SET_MINOR -> BluetoothSensorManager.SETTING_BLE_ID3
                            else -> BluetoothSensorManager.SETTING_BLE_TRANSMIT_POWER
                        },
                        when (command) {
                            BLE_SET_ADVERTISE_MODE -> {
                                when (data[BLE_ADVERTISE]) {
                                    BLE_ADVERTISE_BALANCED -> BluetoothSensorManager.BLE_ADVERTISE_BALANCED
                                    BLE_ADVERTISE_LOW_LATENCY -> BluetoothSensorManager.BLE_ADVERTISE_LOW_LATENCY
                                    BLE_ADVERTISE_LOW_POWER -> BluetoothSensorManager.BLE_ADVERTISE_LOW_POWER
                                    else -> BluetoothSensorManager.BLE_ADVERTISE_LOW_POWER
                                }
                            }
                            BLE_SET_UUID -> data[BLE_UUID] ?: UUID.randomUUID().toString()
                            BLE_SET_MAJOR -> data[BLE_MAJOR]
                                ?: BluetoothSensorManager.DEFAULT_BLE_MAJOR
                            BLE_SET_MINOR -> data[BLE_MINOR]
                                ?: BluetoothSensorManager.DEFAULT_BLE_MINOR
                            else -> {
                                when (data[BLE_TRANSMIT]) {
                                    BLE_TRANSMIT_HIGH -> BluetoothSensorManager.BLE_TRANSMIT_HIGH
                                    BLE_TRANSMIT_LOW -> BluetoothSensorManager.BLE_TRANSMIT_LOW
                                    BLE_TRANSMIT_MEDIUM -> BluetoothSensorManager.BLE_TRANSMIT_MEDIUM
                                    BLE_TRANSMIT_ULTRA_LOW -> BluetoothSensorManager.BLE_TRANSMIT_ULTRA_LOW
                                    else -> BluetoothSensorManager.BLE_TRANSMIT_ULTRA_LOW
                                }
                            }
                        }
                    )

                    // Force the transmitter to restart and send updated attributes
                    mainScope.launch {
                        sensorDao.updateLastSentStateAndIcon(
                            BluetoothSensorManager.bleTransmitter.id,
                            null,
                            null
                        )
                    }
                    BluetoothSensorManager().requestSensorUpdate(context)
                    SensorWorker.start(context)
                }
            }
            COMMAND_HIGH_ACCURACY_MODE -> {
                when (command) {
                    TURN_OFF -> LocationSensorManager.setHighAccuracyModeSetting(context, false)
                    TURN_ON -> LocationSensorManager.setHighAccuracyModeSetting(context, true)
                    HIGH_ACCURACY_SET_UPDATE_INTERVAL -> LocationSensorManager.setHighAccuracyModeIntervalSetting(context, data[HIGH_ACCURACY_UPDATE_INTERVAL]!!.toInt())
                }
                val intent = Intent(context, LocationSensorManager::class.java)
                intent.action = LocationSensorManager.ACTION_FORCE_HIGH_ACCURACY
                intent.putExtra("command", command)
                context.sendBroadcast(intent)
            }
            COMMAND_ACTIVITY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(context))
                        notifyMissingPermission(data[MESSAGE].toString())
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED && data["tag"] == Intent.ACTION_CALL) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                context.getString(commonR.string.missing_phone_permission),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        navigateAppDetails()
                    } else
                        processActivityCommand(data)
                } else
                    processActivityCommand(data)
            }
            COMMAND_WEBVIEW -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(context))
                        notifyMissingPermission(data[MESSAGE].toString())
                    else
                        openWebview(command)
                } else
                    openWebview(command)
            }
            COMMAND_SCREEN_ON -> {
                if (!command.isNullOrEmpty()) {
                    mainScope.launch {
                        integrationUseCase.setKeepScreenOnEnabled(
                            command == COMMAND_KEEP_SCREEN_ON
                        )
                    }
                }

                val powerManager = context.getSystemService<PowerManager>()
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "HomeAssistant::NotificationScreenOnWakeLock"
                )
                wakeLock?.acquire(1 * 30 * 1000L /*30 seconds */)
                wakeLock?.release()
            }
            COMMAND_MEDIA -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    if (!NotificationManagerCompat.getEnabledListenerPackages(context)
                        .contains(context.packageName)
                    )
                        notifyMissingPermission(data[MESSAGE].toString())
                    else {
                        processMediaCommand(data)
                    }
                }
            }
            COMMAND_LAUNCH_APP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(context))
                        notifyMissingPermission(data[MESSAGE].toString())
                    else
                        launchApp(data)
                } else
                    launchApp(data)
            }
            COMMAND_PERSISTENT_CONNECTION -> {
                togglePersistentConnection(data[PERSISTENT].toString())
            }
            COMMAND_STOP_TTS -> {
                stopTTS()
            }
            else -> Log.d(TAG, "No command received")
        }
    }

    /**
     * Add Extra values to Intent.
     * Extra value might include type information based on type
     * given by the user. If a known type is given, the initial string value
     * will be converted to this type.
     */
    private fun addExtrasToIntent(intent: Intent, extras: String) {
        val items = extras.split(',')
        for (item in items) {
            val chunks = item.split(":")
            var value = chunks[1]
            val hasTypeInfo = chunks.size > 2

            // Intent item has included type info, convert to this type
            if (hasTypeInfo) {
                value = chunks.subList(1, chunks.lastIndex).joinToString(":")

                when (chunks.last()) {
                    "urlencoded" -> intent.putExtra(chunks[0], URLDecoder.decode(value, "UTF-8"))
                    "int" -> intent.putExtra(chunks[0], value.toInt())
                    "double" -> intent.putExtra(chunks[0], value.toDouble())
                    "float" -> intent.putExtra(chunks[0], value.toFloat())
                    "long" -> intent.putExtra(chunks[0], value.toLong())
                    "short" -> intent.putExtra(chunks[0], value.toShort())
                    "boolean" -> intent.putExtra(chunks[0], value.toBoolean())
                    "char" -> intent.putExtra(chunks[0], value[0].toChar())
                    "ArrayList<Integer>" -> intent.putIntegerArrayListExtra(
                        chunks[0],
                        value.split(";").map { it.toInt() }.toCollection(ArrayList())
                    )
                    "ArrayList<String>" -> intent.putStringArrayListExtra(
                        chunks[0],
                        value.split(";").toCollection(ArrayList())
                    )
                    else -> {
                        intent.putExtra(chunks[0], value)
                    }
                }
            } else {
                // Try to guess the correct type
                if (value.isDigitsOnly())
                    intent.putExtra(chunks[0], value.toInt())
                else if ((value.lowercase() == "true") || (value.lowercase() == "false"))
                    intent.putExtra(chunks[0], value.toBoolean())
                else
                    intent.putExtra(chunks[0], value)
            }
        }
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     */
    private suspend fun sendNotification(data: Map<String, String>) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)

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

        val notificationBuilder = NotificationCompat.Builder(context, channelId)

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

        handleVideo(notificationBuilder, data)

        handleVisibility(notificationBuilder, data)

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
            if (!group.isNullOrBlank()) {
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
        if (data[NOTIFICATION_ICON]?.startsWith("mdi:") == true && !data[NOTIFICATION_ICON]?.substringAfter("mdi:").isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val iconName = data[NOTIFICATION_ICON]!!.split(":")[1]
            val iconDrawable =
                IconicsDrawable(context, "cmd-$iconName").toAndroidIconCompat()
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
        val actionUri = data["clickAction"] ?: "/"
        builder.setContentIntent(createOpenUriPendingIntent(actionUri))
    }

    private fun handleDeleteIntent(
        builder: NotificationCompat.Builder,
        data: Map<String, String>,
        messageId: Int,
        group: String?,
        groupId: Int

    ) {

        val deleteIntent = Intent(context, NotificationDeleteReceiver::class.java).apply {
            putExtra(NotificationDeleteReceiver.EXTRA_DATA, HashMap(data))
            putExtra(NotificationDeleteReceiver.EXTRA_NOTIFICATION_GROUP, group)
            putExtra(NotificationDeleteReceiver.EXTRA_NOTIFICATION_GROUP_ID, groupId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
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

        val groupNotificationBuilder = NotificationCompat.Builder(context, channelId)
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
        if (data[CHANNEL] == ALARM_STREAM) {
            builder.setCategory(Notification.CATEGORY_ALARM)
            builder.setSound(
                RingtoneManager.getActualDefaultRingtoneUri(
                    context,
                    RingtoneManager.TYPE_ALARM
                )
                    ?: RingtoneManager.getActualDefaultRingtoneUri(
                        context,
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
        val color = parseColor(colorString, commonR.color.colorPrimary)
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
        return ContextCompat.getColor(context, default)
    }

    private fun handleLegacyLedColor(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        val ledColor = data[LED_COLOR]
        if (!ledColor.isNullOrBlank()) {
            builder.setLights(parseColor(ledColor, commonR.color.colorPrimary), 3000, 3000)
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
        // Replace control char \r\n, \r, \n and also \r\n, \r, \n as text literals in strings to <br>
        var brText = text.replace("(\r\n|\r|\n)|(\\\\r\\\\n|\\\\r|\\\\n)".toRegex(), "<br>")
        var emojiParsedText = EmojiParser.parseToUnicode(brText)
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
                val request = Request.Builder().apply {
                    url(url)
                    if (requiresAuth) {
                        addHeader("Authorization", authenticationUseCase.buildBearerToken())
                    }
                }.build()

                val response = okHttpClient.newCall(request).execute()
                image = BitmapFactory.decodeStream(response.body?.byteStream())
                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't download image for notification", e)
            }
            return@withContext image
        }

    private suspend fun handleVideo(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        data[VIDEO_URL]?.let {
            val url = UrlHandler.handle(urlUseCase.getUrl(), it)
            getVideoFrames(url, !UrlHandler.isAbsoluteUrl(it))?.let { frames ->
                Log.d(TAG, "Found ${frames.size} frames for video notification")
                RemoteViews(context.packageName, R.layout.view_image_flipper).let { remoteViewFlipper ->
                    if (frames.isNotEmpty()) {
                        frames.forEach { frame ->
                            remoteViewFlipper.addView(
                                R.id.frame_flipper,
                                RemoteViews(context.packageName, R.layout.view_single_frame).apply {
                                    setImageViewBitmap(
                                        R.id.frame,
                                        frame
                                    )
                                }
                            )
                        }

                        data[TITLE]?.let { rawTitle ->
                            remoteViewFlipper.setTextViewText(R.id.title, rawTitle)
                        }

                        data[MESSAGE]?.let { rawMessage ->
                            remoteViewFlipper.setTextViewText(R.id.info, rawMessage)
                        }

                        builder.setCustomBigContentView(remoteViewFlipper)
                        builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    }
                }
            }
        }
    }

    private suspend fun getVideoFrames(url: URL?, requiresAuth: Boolean = false): List<Bitmap>? =
        withContext(
            Dispatchers.IO
        ) {
            url ?: return@withContext null
            val processingFrames = mutableListOf<Deferred<Bitmap?>>()

            try {
                MediaMetadataRetriever().let { mediaRetriever ->

                    if (requiresAuth) {
                        mediaRetriever.setDataSource(url.toString(), mapOf("Authorization" to authenticationUseCase.buildBearerToken()))
                    } else {
                        mediaRetriever.setDataSource(url.toString(), hashMapOf())
                    }

                    val durationInMicroSeconds = ((mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: VIDEO_GUESS_MILLISECONDS)) * 1000

                    // Start at 100 milliseconds and get frames every 2 seconds until reaching the end
                    run frameLoop@{
                        for (timeInMicroSeconds in VIDEO_START_MICROSECONDS until durationInMicroSeconds step VIDEO_INCREMENT_MICROSECONDS) {
                            if (processingFrames.size >= 5) {
                                return@frameLoop
                            }

                            mediaRetriever.getFrameAtTime(timeInMicroSeconds, MediaMetadataRetriever.OPTION_CLOSEST)
                                ?.let { smallFrame -> processingFrames.add(async { smallFrame.getCompressedFrame() }) }
                        }
                    }

                    mediaRetriever.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Couldn't download video for notification", e)
            }

            return@withContext processingFrames.awaitAll().filterNotNull()
        }

    private fun Bitmap.getCompressedFrame(): Bitmap? {
        val newHeight = height / 4
        val newWidth = width / 4
        return Bitmap.createScaledBitmap(this, newWidth, newHeight, false)
    }

    private fun handleVisibility(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        data[VISIBILITY]?.let {
            builder.setVisibility(
                when (it) {
                    "public" -> NotificationCompat.VISIBILITY_PUBLIC
                    "secret" -> NotificationCompat.VISIBILITY_SECRET
                    else -> NotificationCompat.VISIBILITY_PRIVATE
                }
            )
        }
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
                val eventIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.FIRE_EVENT
                    if (data["sticky"]?.toBoolean() != true) {
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_TAG, tag)
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, messageId)
                    }
                    putExtra(
                        NotificationActionReceiver.EXTRA_NOTIFICATION_ACTION,
                        notificationAction
                    )
                }

                when (notificationAction.key) {
                    URI -> {
                        if (!notificationAction.uri.isNullOrBlank()) {
                            builder.addAction(
                                commonR.drawable.ic_globe,
                                notificationAction.title,
                                createOpenUriPendingIntent(notificationAction.uri)
                            )
                        }
                    }
                    REPLY -> {
                        val remoteInput: RemoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).run {
                            setLabel(context.getString(commonR.string.action_reply))
                            build()
                        }
                        val replyPendingIntent = PendingIntent.getBroadcast(
                            context,
                            0,
                            eventIntent,
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
                    else -> {
                        val actionPendingIntent = PendingIntent.getBroadcast(
                            context,
                            (notificationAction.title.hashCode() + System.currentTimeMillis()).toInt(),
                            eventIntent,
                            PendingIntent.FLAG_IMMUTABLE
                        )
                        builder.addAction(
                            commonR.drawable.ic_stat_ic_notification,
                            notificationAction.title,
                            actionPendingIntent
                        )
                    }
                }
            }
        }
    }

    private fun createOpenUriPendingIntent(
        uri: String
    ): PendingIntent {
        val intent = when {
            uri.isBlank() -> {
                WebViewActivity.newInstance(context)
            }
            uri.startsWith(APP_PREFIX) -> {
                context.packageManager.getLaunchIntentForPackage(uri.substringAfter(APP_PREFIX))
            }
            uri.startsWith(SETTINGS_PREFIX) -> {
                if (uri.substringAfter(SETTINGS_PREFIX) == NOTIFICATION_HISTORY)
                    SettingsActivity.newInstance(context)
                else
                    WebViewActivity.newInstance(context)
            }
            UrlHandler.isAbsoluteUrl(uri) -> {
                Intent(Intent.ACTION_VIEW).apply {
                    this.data = Uri.parse(uri)
                }
            }
            else -> {
                WebViewActivity.newInstance(context, uri)
            }
        } ?: WebViewActivity.newInstance(context)

        if (uri.startsWith(SETTINGS_PREFIX) && uri.substringAfter(SETTINGS_PREFIX) == NOTIFICATION_HISTORY)
            intent.putExtra("fragment", NOTIFICATION_HISTORY)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        return PendingIntent.getActivity(
            context,
            (uri.hashCode() + System.currentTimeMillis()).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun handleChannel(
        notificationManagerCompat: NotificationManagerCompat,
        data: Map<String, String>
    ): String {
        // Define some values for a default channel
        var channelID = generalChannel
        var channelName = "General"

        if (!data[CHANNEL].isNullOrEmpty()) {
            channelID = createChannelID(data[CHANNEL].toString())
            channelName = data[CHANNEL].toString().trim()
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
                channel.lightColor = parseColor(ledColor, commonR.color.colorPrimary)
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
                context,
                RingtoneManager.TYPE_ALARM
            )
                ?: RingtoneManager.getActualDefaultRingtoneUri(
                    context,
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
        context.startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestSystemAlertPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun requestNotificationPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    private fun navigateAppDetails() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
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
        val title = data[MEDIA_COMMAND]
        val mediaSessionManager = context.getSystemService<MediaSessionManager>()!!
        val mediaList = mediaSessionManager.getActiveSessions(
            ComponentName(
                context,
                NotificationSensorManager::class.java
            )
        )
        var hasCorrectPackage = false
        if (mediaList.size > 0) {
            for (item in mediaList) {
                if (item.packageName == data[MEDIA_PACKAGE_NAME]) {
                    hasCorrectPackage = true
                    val mediaSessionController =
                        MediaController(context, item.sessionToken)
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
        when (stream) {
            ALARM_STREAM -> adjustVolumeStream(AudioManager.STREAM_ALARM, volume, audioManager)
            MUSIC_STREAM -> adjustVolumeStream(AudioManager.STREAM_MUSIC, volume, audioManager)
            NOTIFICATION_STREAM -> adjustVolumeStream(AudioManager.STREAM_NOTIFICATION, volume, audioManager)
            RING_STREAM -> adjustVolumeStream(AudioManager.STREAM_RING, volume, audioManager)
            CALL_STREAM -> adjustVolumeStream(AudioManager.STREAM_VOICE_CALL, volume, audioManager)
            SYSTEM_STREAM -> adjustVolumeStream(AudioManager.STREAM_SYSTEM, volume, audioManager)
            DTMF_STREAM -> adjustVolumeStream(AudioManager.STREAM_DTMF, volume, audioManager)
            else -> Log.d(TAG, "Skipping command due to invalid channel stream")
        }
    }

    private fun adjustVolumeStream(stream: Int, volume: Int, audioManager: AudioManager) {
        var volumeLevel = volume
        if (volumeLevel > audioManager.getStreamMaxVolume(stream))
            volumeLevel = audioManager.getStreamMaxVolume(stream)
        else if (volumeLevel < 0)
            volumeLevel = 0
        audioManager.setStreamVolume(
            stream,
            volumeLevel,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun processActivityCommand(data: Map<String, String>) {
        try {
            val packageName = data[INTENT_PACKAGE_NAME]
            val action = data[INTENT_ACTION]
            val className = data[INTENT_CLASS_NAME]
            val intentUri = if (!data[INTENT_URI].isNullOrEmpty()) Uri.parse(data[INTENT_URI]) else null
            val intent = if (intentUri != null) Intent(action, intentUri) else Intent(action)
            val type = data[INTENT_TYPE]
            if (!type.isNullOrEmpty())
                intent.type = type
            if (!className.isNullOrEmpty() && !packageName.isNullOrEmpty())
                intent.setClassName(packageName, className)
            val extras = data[INTENT_EXTRAS]
            if (!extras.isNullOrEmpty()) {
                addExtrasToIntent(intent, extras)
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (!packageName.isNullOrEmpty()) {
                intent.setPackage(packageName)
                context.startActivity(intent)
            } else if (intent.resolveActivity(context.packageManager) != null)
                context.startActivity(intent)
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
                    context,
                    commonR.string.activity_intent_error,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openWebview(title: String?) {
        try {
            val intent = if (title.isNullOrEmpty())
                WebViewActivity.newInstance(context)
            else
                WebViewActivity.newInstance(context, title)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open webview", e)
        }
    }

    private fun launchApp(data: Map<String, String>) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(data[PACKAGE_NAME]!!)
            if (launchIntent != null)
                context.startActivity(launchIntent)
            else {
                Log.w(TAG, "No intent to launch app found, opening app store")
                val marketIntent = Intent(Intent.ACTION_VIEW)
                marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                marketIntent.data = Uri.parse(
                    MARKET_PREFIX + data[PACKAGE_NAME]
                )
                context.startActivity(marketIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch app", e)
            mainScope.launch { sendNotification(data) }
        }
    }

    private fun togglePersistentConnection(mode: String) {
        when (mode.uppercase()) {
            WebsocketSetting.NEVER.name -> {
                settingsDao.get(0)?.let {
                    it.websocketSetting = WebsocketSetting.NEVER
                    settingsDao.update(it)
                }
            }
            WebsocketSetting.ALWAYS.name -> {
                settingsDao.get(0)?.let {
                    it.websocketSetting = WebsocketSetting.ALWAYS
                    settingsDao.update(it)
                }
            }
            WebsocketSetting.HOME_WIFI.name -> {
                settingsDao.get(0)?.let {
                    it.websocketSetting = WebsocketSetting.HOME_WIFI
                    settingsDao.update(it)
                }
            }
            WebsocketSetting.SCREEN_ON.name -> {
                settingsDao.get(0)?.let {
                    it.websocketSetting = WebsocketSetting.SCREEN_ON
                    settingsDao.update(it)
                }
            }
        }

        WebsocketManager.start(context)
    }

    private fun notifyMissingPermission(type: String) {
        val appManager =
            context.getSystemService<ActivityManager>()
        val currentProcess = appManager?.runningAppProcesses
        if (currentProcess != null) {
            for (item in currentProcess) {
                if (context.applicationInfo.processName == item.processName) {
                    if (item.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        val data =
                            mutableMapOf(MESSAGE to context.getString(commonR.string.missing_command_permission))
                        runBlocking {
                            sendNotification(data)
                        }
                    } else {
                        when (type) {
                            COMMAND_WEBVIEW, COMMAND_ACTIVITY, COMMAND_LAUNCH_APP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestSystemAlertPermission()
                            }
                            COMMAND_RINGER_MODE, COMMAND_DND, COMMAND_VOLUME_LEVEL -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestDNDPermission()
                            }
                            COMMAND_MEDIA -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                requestNotificationPermission()
                            }
                            COMMAND_BLUETOOTH -> {
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        context,
                                        context.getString(commonR.string.missing_bluetooth_permission),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                navigateAppDetails()
                            }
                        }
                    }
                }
            }
        }
    }
}
