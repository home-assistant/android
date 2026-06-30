package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Sensor manager for the notification sensors. Owns the basic sensors, availability, permission and
 * reporting logic.
 */
@Singleton
class NotificationSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        private const val SETTING_ALLOW_LIST = "notification_allow_list"
        private const val SETTING_DISABLE_ALLOW_LIST = "notification_disable_allow_list"
        private const val SETTING_INCLUDE_CONTENTS_AS_ATTRS = "active_notification_count_content_attrs"

        @ProvidesSensor
        val lastNotification = SensorManager.BasicSensor(
            "last_notification",
            "sensor",
            commonR.string.basic_sensor_name_last_notification,
            commonR.string.sensor_description_last_notification,
            "mdi:bell-ring",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#last-notification",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT_ONLY,
        )

        @ProvidesSensor
        val lastRemovedNotification = SensorManager.BasicSensor(
            "last_removed_notification",
            "sensor",
            commonR.string.basic_sensor_name_last_removed_notification,
            commonR.string.sensor_description_last_removed_notification,
            "mdi:bell-ring",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#last-removed-notification",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT_ONLY,
        )

        @ProvidesSensor
        val activeNotificationCount = SensorManager.BasicSensor(
            "active_notification_count",
            "sensor",
            commonR.string.basic_sensor_name_active_notification_count,
            commonR.string.sensor_description_active_notification_count,
            "mdi:bell-ring",
            unitOfMeasurement = "notifications",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#active-notification-count",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        internal val mediaSession = SensorManager.BasicSensor(
            "media_session",
            "sensor",
            commonR.string.basic_sensor_name_media_session,
            commonR.string.sensor_description_media_session,
            "mdi:play-circle",
            deviceClass = "enum",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#media-session-sensor",
        )
    }

    /** Whether the notification listener service is currently connected, set by the service. */
    private var listenerConnected = AtomicBoolean(false)

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#notification-sensors"
    }

    override fun hasSensor(): Boolean {
        return if (!applicationContext.isAutomotive()) {
            val uiManager = applicationContext.getSystemService<UiModeManager>()
            uiManager?.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION
        } else {
            false
        }
    }

    override val name: Int
        get() = commonR.string.sensor_name_last_notification

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(lastNotification, lastRemovedNotification, activeNotificationCount, mediaSession)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
    }

    override suspend fun checkPermission(sensorId: String): Boolean {
        return NotificationManagerCompat
            .getEnabledListenerPackages(applicationContext)
            .contains(applicationContext.packageName)
    }

    override suspend fun requestSensorUpdate() {
        updateMediaSession()
    }

    /** Called by [NotificationSensorListenerService] when the listener connects or disconnects. */
    fun onListenerConnectionChanged(connected: Boolean) {
        listenerConnected.set(connected)
    }

    /** Called by [NotificationSensorListenerService.onNotificationPosted]. */
    fun onNotificationPosted(sbn: StatusBarNotification, activeNotifications: Array<StatusBarNotification>) {
        updateActiveNotificationCount(activeNotifications)

        sensorWorkerScope.launch {
            if (!isEnabled(lastNotification)) {
                return@launch
            }

            val allowPackages = getSetting(
                lastNotification,
                SETTING_ALLOW_LIST,
                SensorSettingType.LIST_APPS,
                default = "",
            ).split(", ").filter { it.isNotBlank() }

            val disableAllowListRequirement = getToggleSetting(
                lastNotification,
                SETTING_DISABLE_ALLOW_LIST,
                default = false,
            )

            if (sbn.packageName == applicationContext.packageName ||
                (allowPackages.isNotEmpty() && sbn.packageName !in allowPackages) ||
                (!disableAllowListRequirement && allowPackages.isEmpty())
            ) {
                return@launch
            }

            if (sbn.notification.group == "ranker_group") {
                return@launch
            }

            val attrs = buildMap {
                putAll(mappedBundle(sbn.notification.extras).orEmpty())
                put("package", sbn.packageName)
                put("post_time", sbn.postTime)
                put("is_clearable", sbn.isClearable)
                put("is_ongoing", sbn.isOngoing)
                put("group_id", sbn.notification.group)
                put("category", sbn.notification.category)

                if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
                    put("channel_id", sbn.notification.channelId)
                }
            }

            // Attempt to use the text of the notification but fallback to package name if all else fails.
            val state = attrs["android.text"] ?: attrs["android.title"] ?: sbn.packageName

            onSensorUpdated(
                lastNotification,
                state.toString().take(255),
                lastNotification.statelessIcon,
                attrs,
                forceUpdate = true,
            )

            // Need to send update!
            SensorReceiver.updateAllSensors(applicationContext)
        }
    }

    /** Called by [NotificationSensorListenerService.onNotificationRemoved]. */
    fun onNotificationRemoved(sbn: StatusBarNotification, activeNotifications: Array<StatusBarNotification>) {
        updateActiveNotificationCount(activeNotifications)

        sensorWorkerScope.launch {
            if (!isEnabled(lastRemovedNotification)) {
                return@launch
            }

            val allowPackages = getSetting(
                lastRemovedNotification,
                SETTING_ALLOW_LIST,
                SensorSettingType.LIST_APPS,
                default = "",
            ).split(", ").filter { it.isNotBlank() }

            val disableAllowListRequirement = getToggleSetting(
                lastRemovedNotification,
                SETTING_DISABLE_ALLOW_LIST,
                default = false,
            )

            if (sbn.packageName == applicationContext.packageName ||
                (allowPackages.isNotEmpty() && sbn.packageName !in allowPackages) ||
                (!disableAllowListRequirement && allowPackages.isEmpty())
            ) {
                return@launch
            }

            val attrs = buildMap {
                putAll(mappedBundle(sbn.notification.extras).orEmpty())
                put("package", sbn.packageName)
                put("post_time", sbn.postTime)
                put("is_clearable", sbn.isClearable)
                put("is_ongoing", sbn.isOngoing)
                put("group_id", sbn.notification.group)
                put("category", sbn.notification.category)

                if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
                    put("channel_id", sbn.notification.channelId)
                }
            }

            // Attempt to use the text of the notification but fallback to package name if all else fails.
            val state = attrs["android.text"] ?: attrs["android.title"] ?: sbn.packageName

            onSensorUpdated(
                lastRemovedNotification,
                state.toString().take(255),
                lastRemovedNotification.statelessIcon,
                attrs,
                forceUpdate = true,
            )

            // Need to send update!
            SensorReceiver.updateAllSensors(applicationContext)
        }
    }

    private fun updateActiveNotificationCount(activeNotifications: Array<StatusBarNotification>) {
        sensorWorkerScope.launch {
            if (!isEnabled(activeNotificationCount) || !listenerConnected.get()) {
                return@launch
            }

            try {
                val includeContentsAsAttrsSetting =
                    getToggleSetting(
                        activeNotificationCount,
                        SETTING_INCLUDE_CONTENTS_AS_ATTRS,
                        default = true,
                    )
                val attrs = if (includeContentsAsAttrsSetting) {
                    buildMap {
                        activeNotifications.forEach { item ->
                            putAll(mappedBundle(item.notification.extras, "_${item.packageName}_${item.id}").orEmpty())
                            put("${item.packageName}_${item.id}_post_time", item.postTime)
                            put("${item.packageName}_${item.id}_is_ongoing", item.isOngoing)
                            put("${item.packageName}_${item.id}_is_clearable", item.isClearable)
                            put("${item.packageName}_${item.id}_group_id", item.notification.group)
                            put("${item.packageName}_${item.id}_category", item.notification.category)

                            if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
                                put("${item.packageName}_${item.id}_channel_id", item.notification.channelId)
                            }
                        }
                    }
                } else {
                    emptyMap()
                }
                onSensorUpdated(
                    activeNotificationCount,
                    activeNotifications.size,
                    activeNotificationCount.statelessIcon,
                    attrs,
                )
            } catch (e: Exception) {
                Timber.e(e, "Unable to update active notifications")
            }
        }
    }

    private val mediaStates = mapOf(
        PlaybackState.STATE_PLAYING to "Playing",
        PlaybackState.STATE_PAUSED to "Paused",
        PlaybackState.STATE_STOPPED to "Stopped",
        PlaybackState.STATE_BUFFERING to "Buffering",
        PlaybackState.STATE_CONNECTING to "Connecting",
        PlaybackState.STATE_ERROR to "Error",
        PlaybackState.STATE_FAST_FORWARDING to "Fast Forwarding",
        PlaybackState.STATE_NONE to "None",
        PlaybackState.STATE_REWINDING to "Rewinding",
        PlaybackState.STATE_SKIPPING_TO_NEXT to "Skip to Next",
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS to "Skip to Previous",
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM to "Skip to Queue Item",
    )

    private suspend fun updateMediaSession() {
        if (!isEnabled(mediaSession)) {
            return
        }

        val mediaSessionManager = applicationContext.getSystemService<MediaSessionManager>()!!
        val mediaList = mediaSessionManager.getActiveSessions(
            ComponentName(applicationContext, NotificationSensorListenerService::class.java),
        )
        val sessionCount = mediaList.size
        val primaryPlaybackState = if (sessionCount >
            0
        ) {
            getPlaybackState(mediaList[0].playbackState?.state)
        } else {
            STATE_UNAVAILABLE
        }
        val attr: MutableMap<String, Any?> = mutableMapOf()
        if (mediaList.isNotEmpty()) {
            for (item in mediaList) {
                attr += mapOf(
                    "artist_" + item.packageName to item.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    "album_" + item.packageName to item.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    "title_" + item.packageName to item.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                    "duration_" + item.packageName to item.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    "media_id_" + item.packageName to item.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                    "playback_position_" + item.packageName to item.playbackState?.position,
                    "playback_state_" + item.packageName to getPlaybackState(item.playbackState?.state),
                )
            }
        }
        attr += mapOf(
            "total_media_session_count" to sessionCount,
            "options" to mediaStates.values.toList(),
        )
        onSensorUpdated(
            mediaSession,
            primaryPlaybackState,
            mediaSession.statelessIcon,
            attr,
            forceUpdate = primaryPlaybackState == "Playing",
        )
    }

    private fun getPlaybackState(state: Int?): String {
        return mediaStates.getOrDefault(state ?: PlaybackState.STATE_NONE, STATE_UNKNOWN)
    }

    /**
     * Returns the values of a bundle as a key/value map for use as a sensor's attributes.
     * Arrays are converted to lists to make them human readable.
     * Bundles inside the given bundle will also be mapped as a key/value map.
     */
    private suspend fun mappedBundle(bundle: Bundle, keySuffix: String = ""): Map<String, Any?>? = withContext(
        Dispatchers.Default,
    ) {
        try {
            bundle.keySet().associate { key ->
                @Suppress("DEPRECATION")
                val keyValue = when (val value = bundle.get(key)) {
                    is Array<*> -> {
                        if (value.all { it is Bundle }) {
                            value.map { mappedBundle(it as Bundle) ?: value }
                        } else {
                            value.toList()
                        }
                    }

                    is BooleanArray -> value.toList()
                    is Bundle -> mappedBundle(value) ?: value
                    is ByteArray -> value.toList()
                    is CharArray -> value.toList()
                    is DoubleArray -> value.toList()
                    is FloatArray -> value.toList()
                    is IntArray -> value.toList()
                    is LongArray -> value.toList()
                    is ShortArray -> value.toList()
                    else -> value
                }
                "${key}$keySuffix" to keyValue
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while trying to map notification bundle")
            null
        }
    }
}
