package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.common.R as commonR

class NotificationSensorManager : NotificationListenerService(), SensorManager {
    companion object {
        private const val TAG = "NotificationManager"
        private const val SETTING_ALLOW_LIST = "notification_allow_list"
        private const val SETTING_DISABLE_ALLOW_LIST = "notification_disable_allow_list"

        private var listenerConnected = false
        val lastNotification = SensorManager.BasicSensor(
            "last_notification",
            "sensor",
            commonR.string.basic_sensor_name_last_notification,
            commonR.string.sensor_description_last_notification,
            "mdi:bell-ring",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#last-notification",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val lastRemovedNotification = SensorManager.BasicSensor(
            "last_removed_notification",
            "sensor",
            commonR.string.basic_sensor_name_last_removed_notification,
            commonR.string.sensor_description_last_removed_notification,
            "mdi:bell-ring",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#last-removed-notification",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val activeNotificationCount = SensorManager.BasicSensor(
            "active_notification_count",
            "sensor",
            commonR.string.basic_sensor_name_active_notification_count,
            commonR.string.sensor_description_active_notification_count,
            "mdi:bell-ring",
            unitOfMeasurement = "notifications",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#active-notification-count",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        private val mediaSession = SensorManager.BasicSensor(
            "media_session",
            "sensor",
            commonR.string.basic_sensor_name_media_session,
            commonR.string.sensor_description_media_session,
            "mdi:play-circle",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#media-session-sensor"
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#notification-sensors"
    }
    override fun hasSensor(context: Context): Boolean {
        val uiManager = context.getSystemService<UiModeManager>()
        return uiManager?.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION
    }
    override val name: Int
        get() = commonR.string.sensor_name_last_notification
    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(lastNotification, lastRemovedNotification, activeNotificationCount, mediaSession)
    }
    override val enabledByDefault: Boolean
        get() = false

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
    }

    override fun checkPermission(context: Context, sensorId: String): Boolean {
        return NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    override fun requestSensorUpdate(context: Context) {
        updateMediaSession(context)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        listenerConnected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        listenerConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        updateActiveNotificationCount()

        if (!isEnabled(applicationContext, lastNotification.id))
            return

        val allowPackages = getSetting(
            applicationContext,
            lastNotification,
            SETTING_ALLOW_LIST,
            SensorSettingType.LIST_APPS,
            default = ""
        ).split(", ").filter { it.isNotBlank() }

        val disableAllowListRequirement = getToggleSetting(
            applicationContext,
            lastNotification,
            SETTING_DISABLE_ALLOW_LIST,
            default = false
        )

        if (sbn.packageName == application.packageName ||
            (allowPackages.isNotEmpty() && sbn.packageName !in allowPackages) ||
            (!disableAllowListRequirement && allowPackages.isEmpty())
        ) {
            return
        }

        val attr = sbn.notification.extras.keySet()
            .map { it to sbn.notification.extras.get(it) }
            .toMap()
            .plus("package" to sbn.packageName)
            .plus("post_time" to sbn.postTime)
            .plus("is_clearable" to sbn.isClearable)
            .plus("is_ongoing" to sbn.isOngoing)

        // Attempt to use the text of the notification but fallback to package name if all else fails.
        val state = attr["android.text"] ?: attr["android.title"] ?: sbn.packageName

        onSensorUpdated(
            applicationContext,
            lastNotification,
            state.toString().take(255),
            lastNotification.statelessIcon,
            attr
        )

        // Need to send update!
        SensorWorker.start(applicationContext)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        updateActiveNotificationCount()

        if (!isEnabled(applicationContext, lastRemovedNotification.id))
            return

        val allowPackages = getSetting(
            applicationContext,
            lastRemovedNotification,
            SETTING_ALLOW_LIST,
            SensorSettingType.LIST_APPS,
            default = ""
        ).split(", ").filter { it.isNotBlank() }

        val disableAllowListRequirement = getToggleSetting(
            applicationContext,
            lastRemovedNotification,
            SETTING_DISABLE_ALLOW_LIST,
            default = false
        )

        if (sbn.packageName == application.packageName ||
            (allowPackages.isNotEmpty() && sbn.packageName !in allowPackages) ||
            (!disableAllowListRequirement && allowPackages.isEmpty())
        ) {
            return
        }

        val attr = sbn.notification.extras.keySet()
            .map { it to sbn.notification.extras.get(it) }
            .toMap()
            .plus("package" to sbn.packageName)
            .plus("post_time" to sbn.postTime)
            .plus("is_clearable" to sbn.isClearable)
            .plus("is_ongoing" to sbn.isOngoing)

        // Attempt to use the text of the notification but fallback to package name if all else fails.
        val state = attr["android.text"] ?: attr["android.title"] ?: sbn.packageName

        onSensorUpdated(
            applicationContext,
            lastRemovedNotification,
            state.toString().take(255),
            lastRemovedNotification.statelessIcon,
            attr
        )

        // Need to send update!
        SensorWorker.start(applicationContext)
    }

    private fun updateActiveNotificationCount() {
        if (!isEnabled(applicationContext, activeNotificationCount.id) || !listenerConnected)
            return

        try {
            val attr: MutableMap<String, Any?> = mutableMapOf()
            for (item in activeNotifications) {
                attr += item.notification.extras.keySet()
                    .map { it + "_" + item.packageName to item.notification.extras.get(it) }
                    .toMap()
                    .plus(item.packageName + "_" + item.id + "_post_time" to item.postTime)
                    .plus(item.packageName + "_" + item.id + "_is_ongoing" to item.isOngoing)
                    .plus(item.packageName + "_" + item.id + "_is_clearable" to item.isClearable)
            }
            onSensorUpdated(
                applicationContext,
                activeNotificationCount,
                activeNotifications.size,
                activeNotificationCount.statelessIcon,
                attr
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to update active notifications", e)
        }
    }

    private fun updateMediaSession(context: Context) {
        if (!isEnabled(context, mediaSession.id))
            return

        val mediaSessionManager = context.getSystemService<MediaSessionManager>()!!
        val mediaList = mediaSessionManager.getActiveSessions(ComponentName(context, NotificationSensorManager::class.java))
        val sessionCount = mediaList.size
        val primaryPlaybackState = if (sessionCount > 0) getPlaybackState(mediaList[0].playbackState?.state) else "Unavailable"
        val attr: MutableMap<String, Any?> = mutableMapOf()
        if (mediaList.size > 0) {
            for (item in mediaList) {
                attr += mapOf(
                    "artist_" + item.packageName to item.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    "album_" + item.packageName to item.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    "title_" + item.packageName to item.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                    "duration_" + item.packageName to item.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    "media_id_" + item.packageName to item.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                    "playback_position_" + item.packageName to item.playbackState?.position,
                    "playback_state_" + item.packageName to getPlaybackState(item.playbackState?.state)
                )
            }
        }
        attr += mapOf("total_media_session_count" to sessionCount)
        onSensorUpdated(
            context,
            mediaSession,
            primaryPlaybackState,
            mediaSession.statelessIcon,
            attr
        )
    }

    private fun getPlaybackState(state: Int?): String {
        return when (state) {
            PlaybackState.STATE_PLAYING -> "Playing"
            PlaybackState.STATE_PAUSED -> "Paused"
            PlaybackState.STATE_STOPPED -> "Stopped"
            PlaybackState.STATE_BUFFERING -> "Buffering"
            PlaybackState.STATE_CONNECTING -> "Connecting"
            PlaybackState.STATE_ERROR -> "Error"
            PlaybackState.STATE_FAST_FORWARDING -> "Fast Forwarding"
            PlaybackState.STATE_NONE -> "None"
            PlaybackState.STATE_REWINDING -> "Rewinding"
            PlaybackState.STATE_SKIPPING_TO_NEXT -> "Skip to Next"
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "Skip to Previous"
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "Skip to Queue Item"
            else -> "Unknown"
        }
    }
}
