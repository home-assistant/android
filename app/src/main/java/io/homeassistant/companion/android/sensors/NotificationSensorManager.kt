package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.sensors.SensorManager

class NotificationSensorManager : NotificationListenerService(), SensorManager {
    companion object {
        private const val TAG = "NotificationManager"
        private const val SETTING_ALLOW_LIST = "notification_allow_list"

        private var listenerConnected = false
        val lastNotification = SensorManager.BasicSensor(
            "last_notification",
            "sensor",
            R.string.basic_sensor_name_last_notification,
            R.string.sensor_description_last_notification,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#last-notification"
        )
        val lastRemovedNotification = SensorManager.BasicSensor(
            "last_removed_notification",
            "sensor",
            R.string.basic_sensor_name_last_removed_notification,
            R.string.sensor_description_last_removed_notification,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#last-removed-notification"
        )
        val activeNotificationCount = SensorManager.BasicSensor(
            "active_notification_count",
            "sensor",
            R.string.basic_sensor_name_active_notification_count,
            R.string.sensor_description_active_notification_count,
            unitOfMeasurement = "notifications",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#active-notification-count",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT
        )
        private val mediaSession = SensorManager.BasicSensor(
            "media_session",
            "sensor",
            R.string.basic_sensor_name_media_session,
            R.string.sensor_description_media_session,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#media-session-sensor"
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#notification-sensors"
    }
    override val name: Int
        get() = R.string.sensor_name_last_notification
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
            "list-apps",
            ""
        ).split(", ").filter { it.isNotBlank() }

        if (sbn.packageName == application.packageName ||
            (allowPackages.isNotEmpty() && sbn.packageName !in allowPackages)
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
            "mdi:bell-ring",
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
            "list-apps",
            ""
        ).split(", ").filter { it.isNotBlank() }

        if (sbn.packageName == application.packageName ||
            (allowPackages.isNotEmpty() && sbn.packageName !in allowPackages)
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
            "mdi:bell-ring",
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
                "mdi:bell-ring",
                attr
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to update active notifications", e)
        }
    }

    private fun updateMediaSession(context: Context) {
        if (!isEnabled(context, mediaSession.id))
            return

        val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val mediaList = mediaSessionManager.getActiveSessions(ComponentName(context, NotificationSensorManager::class.java))
        val sessionCount = mediaList.size
        val primaryPlaybackState = if (sessionCount > 0) getPlaybackState(mediaList[0].playbackState?.state) else "Unavailable"
        val attr: MutableMap<String, Any?> = mutableMapOf()
        val icon = "mdi:play-circle"
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
            icon,
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
