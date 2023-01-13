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
import android.util.Log
import android.view.KeyEvent
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.text.isDigitsOnly
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.notifications.ALARM_STREAM
import io.homeassistant.companion.android.common.notifications.ALARM_STREAM_MAX
import io.homeassistant.companion.android.common.notifications.ALERT_ONCE
import io.homeassistant.companion.android.common.notifications.CALL_STREAM
import io.homeassistant.companion.android.common.notifications.CHANNEL
import io.homeassistant.companion.android.common.notifications.DTMF_STREAM
import io.homeassistant.companion.android.common.notifications.GROUP_PREFIX
import io.homeassistant.companion.android.common.notifications.IMPORTANCE
import io.homeassistant.companion.android.common.notifications.LED_COLOR
import io.homeassistant.companion.android.common.notifications.MESSAGE
import io.homeassistant.companion.android.common.notifications.MUSIC_STREAM
import io.homeassistant.companion.android.common.notifications.NOTIFICATION_STREAM
import io.homeassistant.companion.android.common.notifications.RING_STREAM
import io.homeassistant.companion.android.common.notifications.SYSTEM_STREAM
import io.homeassistant.companion.android.common.notifications.TITLE
import io.homeassistant.companion.android.common.notifications.VIBRATION_PATTERN
import io.homeassistant.companion.android.common.notifications.createChannelID
import io.homeassistant.companion.android.common.notifications.getGroupNotificationBuilder
import io.homeassistant.companion.android.common.notifications.handleChannel
import io.homeassistant.companion.android.common.notifications.handleColor
import io.homeassistant.companion.android.common.notifications.handleSmallIcon
import io.homeassistant.companion.android.common.notifications.handleText
import io.homeassistant.companion.android.common.notifications.parseColor
import io.homeassistant.companion.android.common.notifications.parseVibrationPattern
import io.homeassistant.companion.android.common.notifications.prepareText
import io.homeassistant.companion.android.common.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.common.util.cancel
import io.homeassistant.companion.android.common.util.cancelGroupIfNeeded
import io.homeassistant.companion.android.common.util.getActiveNotification
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.sensors.NotificationSensorManager
import io.homeassistant.companion.android.sensors.SensorReceiver
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
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLDecoder
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
        const val DEEP_LINK_PREFIX = "deep-link://"
        const val INTENT_PREFIX = "intent:"
        const val MARKET_PREFIX = "https://play.google.com/store/apps/details?id="
        const val SETTINGS_PREFIX = "settings://"
        const val NOTIFICATION_HISTORY = "notification_history"

        const val SUBJECT = "subject"
        const val TIMEOUT = "timeout"
        const val IMAGE_URL = "image"
        const val ICON_URL = "icon_url"
        const val VIDEO_URL = "video"
        const val VISIBILITY = "visibility"
        const val PERSISTENT = "persistent"
        const val CHRONOMETER = "chronometer"
        const val WHEN = "when"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val INTENT_CLASS_NAME = "intent_class_name"
        const val URI = "URI"
        const val REPLY = "REPLY"
        const val BLE_ADVERTISE = "ble_advertise"
        const val BLE_TRANSMIT = "ble_transmit"
        const val HIGH_ACCURACY_UPDATE_INTERVAL = "high_accuracy_update_interval"
        const val PACKAGE_NAME = "package_name"
        const val COMMAND = "command"
        const val TTS_TEXT = "tts_text"
        const val CONFIRMATION = "confirmation"

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
        const val COMMAND_BEACON_MONITOR = "command_beacon_monitor"
        const val COMMAND_SCREEN_ON = "command_screen_on"
        const val COMMAND_MEDIA = "command_media"
        const val COMMAND_UPDATE_SENSORS = "command_update_sensors"
        const val COMMAND_HIGH_ACCURACY_MODE = "command_high_accuracy_mode"
        const val COMMAND_ACTIVITY = "command_activity"
        const val COMMAND_WEBVIEW = "command_webview"
        const val COMMAND_KEEP_SCREEN_ON = "keep_screen_on"
        const val COMMAND_LAUNCH_APP = "command_launch_app"
        const val COMMAND_APP_LOCK = "command_app_lock"
        const val COMMAND_PERSISTENT_CONNECTION = "command_persistent_connection"
        const val COMMAND_STOP_TTS = "command_stop_tts"
        const val COMMAND_AUTO_SCREEN_BRIGHTNESS = "command_auto_screen_brightness"
        const val COMMAND_SCREEN_BRIGHTNESS_LEVEL = "command_screen_brightness_level"
        const val COMMAND_SCREEN_OFF_TIMEOUT = "command_screen_off_timeout"

        // DND commands
        const val DND_PRIORITY_ONLY = "priority_only"
        const val DND_ALARMS_ONLY = "alarms_only"
        const val DND_ALL = "off"
        const val DND_NONE = "total_silence"

        // Ringer mode commands
        const val RM_NORMAL = "normal"
        const val RM_SILENT = "silent"
        const val RM_VIBRATE = "vibrate"

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

        // App-lock command parameters:
        const val APP_LOCK_ENABLED = "app_lock_enabled"
        const val APP_LOCK_TIMEOUT = "app_lock_timeout"
        const val HOME_BYPASS_ENABLED = "home_bypass_enabled"

        // High accuracy commands
        const val HIGH_ACCURACY_SET_UPDATE_INTERVAL = "high_accuracy_set_update_interval"
        const val FORCE_ON = "force_on"
        const val FORCE_OFF = "force_off"

        // Command groups
        val DEVICE_COMMANDS = listOf(
            COMMAND_DND,
            COMMAND_RINGER_MODE,
            COMMAND_BROADCAST_INTENT,
            COMMAND_VOLUME_LEVEL,
            COMMAND_BLUETOOTH,
            COMMAND_BLE_TRANSMITTER,
            COMMAND_BEACON_MONITOR,
            COMMAND_HIGH_ACCURACY_MODE,
            COMMAND_ACTIVITY,
            COMMAND_WEBVIEW,
            COMMAND_SCREEN_ON,
            COMMAND_MEDIA,
            COMMAND_UPDATE_SENSORS,
            COMMAND_LAUNCH_APP,
            COMMAND_APP_LOCK,
            COMMAND_PERSISTENT_CONNECTION,
            COMMAND_STOP_TTS,
            COMMAND_AUTO_SCREEN_BRIGHTNESS,
            COMMAND_SCREEN_BRIGHTNESS_LEVEL,
            COMMAND_SCREEN_OFF_TIMEOUT
        )
        val DND_COMMANDS = listOf(DND_ALARMS_ONLY, DND_ALL, DND_NONE, DND_PRIORITY_ONLY)
        val RM_COMMANDS = listOf(RM_NORMAL, RM_SILENT, RM_VIBRATE)
        val CHANNEL_VOLUME_STREAM = listOf(
            ALARM_STREAM, MUSIC_STREAM, NOTIFICATION_STREAM, RING_STREAM, CALL_STREAM,
            SYSTEM_STREAM, DTMF_STREAM
        )
        val ENABLE_COMMANDS = listOf(TURN_OFF, TURN_ON)
        val FORCE_COMMANDS = listOf(FORCE_OFF, FORCE_ON)
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
        const val VIDEO_INCREMENT_MICROSECONDS = 750000L
        const val VIDEO_GUESS_MILLISECONDS = 7000L

        // Values for a notification that has been replied to
        const val SOURCE_REPLY = "REPLY_"
        const val SOURCE_REPLY_HISTORY = "reply_history_"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var textToSpeech: TextToSpeech? = null

    fun handleMessage(notificationData: Map<String, String>, source: String) {

        var now = System.currentTimeMillis()
        var jsonData = notificationData
        val notificationId: Long

        if (source.startsWith(SOURCE_REPLY)) {
            notificationId = source.substringAfter(SOURCE_REPLY).toLong()
            notificationDao.get(notificationId.toInt())?.let {
                val dbData: Map<String, String> = jacksonObjectMapper().readValue(it.data)

                now = it.received // Allow for updating the existing notification without a tag
                jsonData = jsonData + dbData // Add the notificationData, this contains the reply text
            } ?: return
        } else {
            val jsonObject = JSONObject(jsonData)
            val notificationRow =
                NotificationItem(0, now, jsonData[MESSAGE].toString(), jsonObject.toString(), source)
            notificationId = notificationDao.add(notificationRow)

            val confirmation = jsonData[CONFIRMATION]?.toBoolean() ?: false
            if (confirmation) {
                mainScope.launch {
                    try {
                        integrationUseCase.fireEvent("mobile_app_notification_received", jsonData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to send notification received event", e)
                    }
                }
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
                        if (
                            !jsonData[COMMAND].isNullOrEmpty() &&
                            jsonData[COMMAND] in ENABLE_COMMANDS &&
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        )
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
                    COMMAND_BEACON_MONITOR -> {
                        if (!jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND] in ENABLE_COMMANDS)
                            handleDeviceCommands(jsonData)
                        else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid beacon monitor command received, posting notification to device"
                                )
                                sendNotification(jsonData)
                            }
                        }
                    }
                    COMMAND_HIGH_ACCURACY_MODE -> {
                        if ((!jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND] in ENABLE_COMMANDS) ||
                            (!jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND] in FORCE_COMMANDS) ||
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
                    COMMAND_APP_LOCK -> {
                        val appLockEnablePresent = jsonData[APP_LOCK_ENABLED] != null
                        val appLockTimeoutPresent = jsonData[APP_LOCK_TIMEOUT] != null
                        val homeBypassEnablePresent = jsonData[HOME_BYPASS_ENABLED] != null

                        val appLockEnableValue = jsonData[APP_LOCK_ENABLED]?.lowercase()?.toBooleanStrictOrNull()
                        val appLockTimeoutValue = jsonData[APP_LOCK_TIMEOUT]?.toIntOrNull()
                        val homeBypassEnableValue = jsonData[HOME_BYPASS_ENABLED]?.lowercase()?.toBooleanStrictOrNull()

                        val invalid = (!appLockEnablePresent && !appLockTimeoutPresent && !homeBypassEnablePresent) ||
                            (appLockEnablePresent && appLockEnableValue == null) ||
                            (appLockTimeoutPresent && (appLockTimeoutValue == null || appLockTimeoutValue < 0)) ||
                            (homeBypassEnablePresent && homeBypassEnableValue == null)

                        if (!invalid)
                            handleDeviceCommands(jsonData)
                        else {
                            mainScope.launch {
                                Log.d(
                                    TAG,
                                    "Invalid app lock command received, posting notification to device"
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
                        if (!jsonData[MEDIA_COMMAND].isNullOrEmpty() && jsonData[MEDIA_COMMAND] in MEDIA_COMMANDS && !jsonData[MEDIA_PACKAGE_NAME].isNullOrEmpty()) {
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
                    COMMAND_UPDATE_SENSORS -> SensorReceiver.updateAllSensors(context)
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
                    COMMAND_AUTO_SCREEN_BRIGHTNESS -> {
                        if (!jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND] in ENABLE_COMMANDS)
                            handleDeviceCommands(jsonData)
                        else
                            mainScope.launch {
                                sendNotification(jsonData)
                            }
                    }
                    COMMAND_SCREEN_BRIGHTNESS_LEVEL, COMMAND_SCREEN_OFF_TIMEOUT -> {
                        if (!jsonData[COMMAND].isNullOrEmpty() && jsonData[COMMAND]?.toIntOrNull() != null)
                            handleDeviceCommands(jsonData)
                        else
                            mainScope.launch {
                                sendNotification(jsonData)
                            }
                    }
                    else -> Log.d(TAG, "No command received")
                }
            }
            else -> mainScope.launch {
                Log.d(TAG, "Creating notification with following data: $jsonData")
                sendNotification(jsonData, notificationId, now)
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
            tts = context.getString(commonR.string.tts_no_text)
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
                @Suppress("DEPRECATION")
                if (command == TURN_OFF)
                    bluetoothAdapter?.disable()
                else if (command == TURN_ON)
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
                    SensorReceiver.updateAllSensors(context)
                }
            }
            COMMAND_BEACON_MONITOR -> {
                if (command == TURN_OFF)
                    BluetoothSensorManager.enableDisableBeaconMonitor(context, false)
                if (command == TURN_ON)
                    BluetoothSensorManager.enableDisableBeaconMonitor(context, true)
            }
            COMMAND_HIGH_ACCURACY_MODE -> {
                when (command) {
                    TURN_OFF -> LocationSensorManager.setHighAccuracyModeSetting(context, false)
                    TURN_ON -> LocationSensorManager.setHighAccuracyModeSetting(context, true)
                    FORCE_ON -> LocationSensorManager.setHighAccuracyModeSetting(context, true)
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
            COMMAND_APP_LOCK -> {
                mainScope.launch {
                    setAppLock(data)
                }
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
            COMMAND_AUTO_SCREEN_BRIGHTNESS, COMMAND_SCREEN_BRIGHTNESS_LEVEL, COMMAND_SCREEN_OFF_TIMEOUT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.System.canWrite(context)) {
                        if (!processScreenCommands(data))
                            mainScope.launch { sendNotification(data) }
                    } else
                        notifyMissingPermission(data[MESSAGE].toString())
                } else if (!processScreenCommands(data))
                    mainScope.launch { sendNotification(data) }
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
    private suspend fun sendNotification(data: Map<String, String>, id: Long? = null, received: Long? = null) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)

        val tag = data["tag"]
        val messageId = tag?.hashCode() ?: received?.toInt() ?: System.currentTimeMillis().toInt()

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

        val channelId = handleChannel(context, notificationManagerCompat, data)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)

        handleSmallIcon(context, notificationBuilder, data)

        handleSound(notificationBuilder, data)

        handlePersistent(notificationBuilder, tag, data)

        handleLargeIcon(notificationBuilder, data)

        handleGroup(notificationBuilder, group, data[ALERT_ONCE].toBoolean())

        handleTimeout(notificationBuilder, data)

        handleColor(context, notificationBuilder, data)

        handleSticky(notificationBuilder, data)

        handleText(notificationBuilder, data)

        handleSubject(notificationBuilder, data)

        handleImage(notificationBuilder, data)

        handleVideo(notificationBuilder, data)

        handleVisibility(notificationBuilder, data)

        handleActions(notificationBuilder, tag, messageId, id, data)

        handleReplyHistory(notificationBuilder, data)

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
                notify(group, groupId, getGroupNotificationBuilder(context, channelId, group, data).build())
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

    private fun handleLegacyLedColor(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        val ledColor = data[LED_COLOR]
        if (!ledColor.isNullOrBlank()) {
            builder.setLights(parseColor(context, ledColor, commonR.color.colorPrimary), 3000, 3000)
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
            val videoFile = File(context.applicationContext.cacheDir.absolutePath + "/notifications/video-${System.currentTimeMillis()}")
            val processingFrames = mutableListOf<Deferred<Bitmap?>>()
            var processingFramesSize = 0
            var singleFrame = 0

            try {
                MediaMetadataRetriever().let { mediaRetriever ->
                    val request = Request.Builder().apply {
                        url(url)
                        if (requiresAuth) {
                            addHeader("Authorization", authenticationUseCase.buildBearerToken())
                        }
                    }.build()
                    val response = okHttpClient.newCall(request).execute()

                    if (!videoFile.exists()) {
                        videoFile.parentFile?.mkdirs()
                        videoFile.createNewFile()
                    }
                    FileOutputStream(videoFile).use { output ->
                        response.body?.byteStream()?.copyTo(output)
                    }
                    response.close()

                    mediaRetriever.setDataSource(videoFile.absolutePath)
                    val durationInMicroSeconds = ((mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: VIDEO_GUESS_MILLISECONDS)) * 1000

                    // Start at 100 milliseconds and get frames every 0.75 seconds until reaching the end
                    run frameLoop@{
                        for (timeInMicroSeconds in VIDEO_START_MICROSECONDS until durationInMicroSeconds step VIDEO_INCREMENT_MICROSECONDS) {
                            // Max size in bytes for notification GIF
                            val maxSize = (5000000 - singleFrame)
                            if (processingFramesSize >= maxSize) {
                                return@frameLoop
                            }

                            mediaRetriever.getFrameAtTime(timeInMicroSeconds, MediaMetadataRetriever.OPTION_CLOSEST)
                                ?.let { smallFrame ->
                                    processingFrames.add(async { smallFrame.getCompressedFrame() })
                                    processingFramesSize += (smallFrame.getCompressedFrame())!!.allocationByteCount
                                    singleFrame = (smallFrame.getCompressedFrame())!!.allocationByteCount
                                }
                        }
                    }

                    mediaRetriever.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Couldn't download video for notification", e)
            }

            val frames = processingFrames.awaitAll().filterNotNull()
            videoFile.delete()
            return@withContext frames
        }

    private fun Bitmap.getCompressedFrame(): Bitmap? {
        var newWidth = 480
        var newHeight = 0
        // If already smaller than 480p do not scale else scale
        if (width < newWidth) {
            newWidth = width
            newHeight = height
        } else {
            val ratio: Float = (width.toFloat() / height.toFloat())
            newHeight = (newWidth / ratio).toInt()
        }
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
        databaseId: Long?,
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
                    putExtra(
                        NotificationActionReceiver.EXTRA_NOTIFICATION_DB,
                        databaseId
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
                            messageId,
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
        val needsPackage = uri.startsWith(APP_PREFIX) || uri.startsWith(INTENT_PREFIX)
        val otherApp = needsPackage || UrlHandler.isAbsoluteUrl(uri) || uri.startsWith(DEEP_LINK_PREFIX)
        val intent = when {
            uri.isBlank() -> {
                WebViewActivity.newInstance(context)
            }
            uri.startsWith(APP_PREFIX) -> {
                context.packageManager.getLaunchIntentForPackage(uri.substringAfter(APP_PREFIX))
            }
            uri.startsWith(INTENT_PREFIX) -> {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            }
            uri.startsWith(SETTINGS_PREFIX) -> {
                if (uri.substringAfter(SETTINGS_PREFIX) == NOTIFICATION_HISTORY)
                    SettingsActivity.newInstance(context)
                else
                    WebViewActivity.newInstance(context)
            }
            UrlHandler.isAbsoluteUrl(uri) || uri.startsWith(DEEP_LINK_PREFIX) -> {
                Intent(Intent.ACTION_VIEW).apply {
                    this.data = Uri.parse(
                        if (uri.startsWith(DEEP_LINK_PREFIX))
                            uri.removePrefix(DEEP_LINK_PREFIX)
                        else
                            uri
                    )
                }
            }
            else -> {
                WebViewActivity.newInstance(context, uri)
            }
        } ?: WebViewActivity.newInstance(context)

        if (uri.startsWith(SETTINGS_PREFIX) && uri.substringAfter(SETTINGS_PREFIX) == NOTIFICATION_HISTORY)
            intent.putExtra("fragment", NOTIFICATION_HISTORY)

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!otherApp) {
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }

        return PendingIntent.getActivity(
            context,
            (uri.hashCode() + System.currentTimeMillis()).toInt(),
            if (needsPackage) {
                val intentPackage = intent.`package`?.let {
                    context.packageManager.getLaunchIntentForPackage(
                        it
                    )
                }
                if (intentPackage == null && (!intent.`package`.isNullOrEmpty() || uri.startsWith(APP_PREFIX))) {
                    val marketIntent = Intent(Intent.ACTION_VIEW)
                    marketIntent.data = Uri.parse(MARKET_PREFIX + if (uri.startsWith(INTENT_PREFIX)) intent.`package`.toString() else uri.removePrefix(APP_PREFIX))
                    marketIntent
                } else
                    intent
            } else
                intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun handleReplyHistory(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val replies = data.entries
                .filter { it.key.startsWith(SOURCE_REPLY_HISTORY) }
                .sortedBy { it.key.substringAfter(SOURCE_REPLY_HISTORY).toInt() }
            if (replies.any()) {
                val history = replies.map { it.value }.reversed().toTypedArray() // Reverse to have latest replies first
                builder.setRemoteInputHistory(history)
                builder.setOnlyAlertOnce(true) // Overwrites user settings to match system defaults
            }
        }
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

    @RequiresApi(Build.VERSION_CODES.M)
    private fun requestWriteSystemPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = Uri.parse("package:" + context.packageName)
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
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
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

    private suspend fun setAppLock(data: Map<String, String>) {
        val appLockEnableValue = data[APP_LOCK_ENABLED]?.lowercase()?.toBooleanStrictOrNull()
        val appLockTimeoutValue = data[APP_LOCK_TIMEOUT]?.toIntOrNull()
        val homeBypassEnableValue = data[HOME_BYPASS_ENABLED]?.lowercase()?.toBooleanStrictOrNull()

        val canAuth = (BiometricManager.from(context).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS)
        if (canAuth) {
            if (appLockEnableValue != null) {
                authenticationUseCase.setLockEnabled(appLockEnableValue)
            }
            if (appLockTimeoutValue != null) {
                integrationUseCase.sessionTimeOut(appLockTimeoutValue)
            }
            if (homeBypassEnableValue != null) {
                authenticationUseCase.setLockHomeBypassEnabled(homeBypassEnableValue)
            }
        } else {
            Log.w(TAG, "Not changing App-Lock settings. BiometricManager cannot Authenticate!")
            sendNotification(data)
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

    private fun processScreenCommands(data: Map<String, String>): Boolean {
        val command = data[COMMAND]
        val contentResolver = context.contentResolver
        val success = Settings.System.putInt(
            contentResolver,
            when (data[MESSAGE].toString()) {
                COMMAND_SCREEN_BRIGHTNESS_LEVEL -> Settings.System.SCREEN_BRIGHTNESS
                COMMAND_AUTO_SCREEN_BRIGHTNESS -> Settings.System.SCREEN_BRIGHTNESS_MODE
                else -> Settings.System.SCREEN_OFF_TIMEOUT
            },
            when (data[MESSAGE].toString()) {
                COMMAND_SCREEN_BRIGHTNESS_LEVEL -> command!!.toInt().coerceIn(0, 255)
                COMMAND_AUTO_SCREEN_BRIGHTNESS -> {
                    if (command == TURN_ON)
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    else
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                }
                else -> command!!.toInt()
            }
        )
        return success
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
                            COMMAND_SCREEN_BRIGHTNESS_LEVEL, COMMAND_AUTO_SCREEN_BRIGHTNESS -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    requestWriteSystemPermission()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
