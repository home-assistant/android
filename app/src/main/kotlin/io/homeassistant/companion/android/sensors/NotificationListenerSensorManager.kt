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
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.BellRing
import io.github.timoptr.mdiicons.generated.PlayCircle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor.Setting
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.sensor.toSensorsWithAttributes
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
class NotificationListenerSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        private const val SETTING_ALLOW_LIST = "notification_allow_list"
        private const val SETTING_DISABLE_ALLOW_LIST = "notification_disable_allow_list"
        private const val SETTING_INCLUDE_CONTENTS_AS_ATTRS = "active_notification_count_content_attrs"
        private val notificationSettings = listOf(
            Setting.Apps(SETTING_ALLOW_LIST),
            Setting.Toggle(SETTING_DISABLE_ALLOW_LIST, default = false),
        )

        @ProvidesSensor
        val lastNotification = SensorManager.BasicSensor(
            "last_notification",
            "sensor",
            commonR.string.basic_sensor_name_last_notification,
            commonR.string.sensor_description_last_notification,
            Mdi.BellRing,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#last-notification",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT_ONLY,
            settings = notificationSettings,
        )

        @ProvidesSensor
        val lastRemovedNotification = SensorManager.BasicSensor(
            "last_removed_notification",
            "sensor",
            commonR.string.basic_sensor_name_last_removed_notification,
            commonR.string.sensor_description_last_removed_notification,
            Mdi.BellRing,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#last-removed-notification",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT_ONLY,
            settings = notificationSettings,
        )

        @ProvidesSensor
        val activeNotificationCount = SensorManager.BasicSensor(
            "active_notification_count",
            "sensor",
            commonR.string.basic_sensor_name_active_notification_count,
            commonR.string.sensor_description_active_notification_count,
            Mdi.BellRing,
            unitOfMeasurement = "notifications",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#active-notification-count",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
            settings = listOf(
                Setting.Toggle(SETTING_INCLUDE_CONTENTS_AS_ATTRS, default = true),
            ),
        )

        @ProvidesSensor
        internal val mediaSession = SensorManager.BasicSensor(
            "media_session",
            "sensor",
            commonR.string.basic_sensor_name_media_session,
            commonR.string.sensor_description_media_session,
            Mdi.PlayCircle,
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

    /** Called by [NotificationSensorManager] when the listener connects or disconnects. */
    fun onListenerConnectionChanged(connected: Boolean) {
        listenerConnected.set(connected)
    }

    /** Called by [NotificationSensorManager.onNotificationPosted]. */
    fun onNotificationPosted(sbn: StatusBarNotification, activeNotifications: Array<StatusBarNotification>) {
        updateActiveNotificationCount(activeNotifications)

        sensorWorkerScope.launch {
            if (!shouldProcessNotification(lastNotification, sbn.packageName)) {
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

    /** Called by [NotificationSensorManager.onNotificationRemoved]. */
    fun onNotificationRemoved(sbn: StatusBarNotification, activeNotifications: Array<StatusBarNotification>) {
        updateActiveNotificationCount(activeNotifications)

        sensorWorkerScope.launch {
            if (!shouldProcessNotification(lastRemovedNotification, sbn.packageName)) {
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
                val activeNotificationSnapshot = activeNotifications.toList()
                val activeNotificationTotal = activeNotificationSnapshot.size
                val includeContentsAsAttrsSetting =
                    getToggleSetting(
                        activeNotificationCount,
                        SETTING_INCLUDE_CONTENTS_AS_ATTRS,
                    )
                if (includeContentsAsAttrsSetting) {
                    val notificationRecords = activeNotificationSnapshot.sortedBy { it.postTime }.map { item ->
                        ActiveNotificationRecord(
                            item.postTime,
                            buildMap {
                                put("${item.packageName}_${item.id}_post_time", item.postTime)
                                put("${item.packageName}_${item.id}_is_ongoing", item.isOngoing)
                                put("${item.packageName}_${item.id}_is_clearable", item.isClearable)
                                put("${item.packageName}_${item.id}_group_id", item.notification.group)
                                put("${item.packageName}_${item.id}_category", item.notification.category)

                                if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
                                    put("${item.packageName}_${item.id}_channel_id", item.notification.channelId)
                                }
                                putAll(
                                    mappedBundle(
                                        item.notification.extras,
                                        "_${item.packageName}_${item.id}",
                                    ).orEmpty(),
                                )
                            },
                        )
                    }
                    buildActiveNotificationBuckets(notificationRecords, activeNotificationTotal).forEach { attrs ->
                        sendActiveNotificationCountBucket(activeNotificationTotal, attrs)
                    }
                } else {
                    sendActiveNotificationCountBucket(activeNotificationTotal, emptyMap())
                }
            } catch (e: Exception) {
                Timber.e(e, "Unable to update active notifications")
            }
        }
    }

    private suspend fun sendActiveNotificationCountBucket(totalCount: Int, attrs: Map<String, Any?>) {
        onSensorUpdated(
            activeNotificationCount,
            totalCount,
            activeNotificationCount.statelessIcon,
            attrs,
            forceUpdate = true,
        )

        withContext(Dispatchers.IO) {
            val fullSensors = sensorRepository.getFull(activeNotificationCount.id).toSensorsWithAttributes()
            fullSensors.filter {
                it.sensor.enabled && it.sensor.registered == true
            }.forEach { fullSensor ->
                try {
                    val registration = fullSensor.toSensorRegistration(activeNotificationCount)
                    val success = serverManager.integrationRepository(fullSensor.sensor.serverId)
                        .updateSensors(listOf(registration))
                    if (success) {
                        sensorRepository.updateLastSentStateAndIcon(
                            activeNotificationCount.id,
                            fullSensor.sensor.serverId,
                            fullSensor.sensor.state,
                            fullSensor.sensor.icon,
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Exception while updating active notification count bucket.")
                }
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
            ComponentName(applicationContext, NotificationSensorManager::class.java),
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

    private suspend fun getNotificationSettings(sensor: SensorManager.BasicSensor): NotificationSettings {
        val allowPackages = getSetting(
            sensor,
            SETTING_ALLOW_LIST,
        ).split(", ").filter { it.isNotBlank() }
        val disableAllowListRequirement = getToggleSetting(
            sensor,
            SETTING_DISABLE_ALLOW_LIST,
        )

        return NotificationSettings(allowPackages, disableAllowListRequirement)
    }

    private suspend fun shouldProcessNotification(sensor: SensorManager.BasicSensor, packageName: String): Boolean {
        if (!isEnabled(sensor)) {
            return false
        }

        val settings = getNotificationSettings(sensor)
        val packageAllowed = packageName in settings.allowPackages
        val allowListRequirementDisabled = settings.allowPackages.isEmpty() && settings.disableAllowListRequirement
        return packageName != applicationContext.packageName && (packageAllowed || allowListRequirementDisabled)
    }

    private data class NotificationSettings(val allowPackages: List<String>, val disableAllowListRequirement: Boolean)

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

private const val RECORDER_SAFE_ATTRIBUTE_BYTES = 14 * 1024
private const val MAX_ACTIVE_NOTIFICATION_ATTRIBUTE_STRING_LENGTH = 512
private const val MAX_ACTIVE_NOTIFICATION_ATTRIBUTE_LIST_SIZE = 10
private const val ACTIVE_NOTIFICATION_TOTAL = "total"
private const val ACTIVE_NOTIFICATION_PART = "part"
private const val ACTIVE_NOTIFICATION_PARTS = "parts"
private const val ACTIVE_NOTIFICATION_LAST = "last"
private const val ACTIVE_NOTIFICATION_SNAPSHOT = "snapshot"
private const val ACTIVE_NOTIFICATION_CUT = "cut"
private const val ACTIVE_NOTIFICATION_CUT_COUNT = "cut_count"

internal data class ActiveNotificationRecord(
    val postTime: Long,
    val attributes: Map<String, Any?>,
    val cutCount: Int = 0,
)

internal fun buildActiveNotificationBuckets(
    records: List<ActiveNotificationRecord>,
    totalCount: Int,
    maxSerializedBytes: Int = RECORDER_SAFE_ATTRIBUTE_BYTES,
): List<Map<String, Any?>> {
    val sortedRecords = records.sortedBy { it.postTime }
    val snapshot = activeNotificationSnapshot(sortedRecords, totalCount)
    if (sortedRecords.isEmpty()) {
        return listOf(activeNotificationBucketAttributes(emptyList(), totalCount, 1, 1, true, snapshot))
    }

    val reverseBuckets = mutableListOf<List<ActiveNotificationRecord>>()
    var currentBucket = mutableListOf<ActiveNotificationRecord>()
    sortedRecords.asReversed().forEach { record ->
        val fittingRecord = record.trimToFitInActiveNotificationBucket(totalCount, snapshot, maxSerializedBytes)
        val candidateBucket = mutableListOf(fittingRecord).apply {
            addAll(currentBucket)
        }
        if (
            currentBucket.isEmpty() ||
            activeNotificationBucketAttributes(
                candidateBucket,
                totalCount,
                999,
                999,
                true,
                snapshot,
            ).serializedAttributeBytes() <= maxSerializedBytes
        ) {
            currentBucket = candidateBucket
        } else {
            reverseBuckets.add(currentBucket)
            currentBucket = mutableListOf(fittingRecord)
        }
    }
    reverseBuckets.add(currentBucket)

    val buckets = reverseBuckets.asReversed()
    return buckets.mapIndexed { index, bucket ->
        activeNotificationBucketAttributes(
            bucket,
            totalCount,
            index + 1,
            buckets.size,
            index == buckets.lastIndex,
            snapshot,
        )
    }
}

private fun ActiveNotificationRecord.trimToFitInActiveNotificationBucket(
    totalCount: Int,
    snapshot: String,
    maxSerializedBytes: Int,
): ActiveNotificationRecord {
    if (
        activeNotificationBucketAttributes(
            listOf(this),
            totalCount,
            999,
            999,
            true,
            snapshot,
        ).serializedAttributeBytes() <= maxSerializedBytes
    ) {
        return this
    }

    val trimmedAttributes = linkedMapOf<String, Any?>()
    var currentCutCount = cutCount
    attributes.forEach { (key, value) ->
        val trimmedValue = value.trimActiveNotificationAttributeValue()
        trimmedAttributes[key] = trimmedValue
        if (trimmedValue != value) {
            currentCutCount++
        }
    }

    var trimmedRecord = copy(attributes = trimmedAttributes, cutCount = currentCutCount)
    while (
        activeNotificationBucketAttributes(
            listOf(trimmedRecord),
            totalCount,
            999,
            999,
            true,
            snapshot,
        ).serializedAttributeBytes() > maxSerializedBytes &&
        trimmedAttributes.isNotEmpty()
    ) {
        trimmedAttributes.remove(trimmedAttributes.keys.last())
        currentCutCount++
        trimmedRecord = copy(attributes = trimmedAttributes.toMap(), cutCount = currentCutCount)
    }
    return trimmedRecord
}

private fun activeNotificationBucketAttributes(
    records: List<ActiveNotificationRecord>,
    totalCount: Int,
    part: Int,
    parts: Int,
    last: Boolean,
    snapshot: String,
): Map<String, Any?> = buildMap {
    put(ACTIVE_NOTIFICATION_TOTAL, totalCount)
    put(ACTIVE_NOTIFICATION_PART, part)
    put(ACTIVE_NOTIFICATION_PARTS, parts)
    put(ACTIVE_NOTIFICATION_LAST, last)
    put(ACTIVE_NOTIFICATION_SNAPSHOT, snapshot)

    val cutCount = records.sumOf { it.cutCount }
    if (cutCount > 0) {
        put(ACTIVE_NOTIFICATION_CUT, true)
        put(ACTIVE_NOTIFICATION_CUT_COUNT, cutCount)
    }

    records.forEach { record ->
        putAll(record.attributes)
    }
}

private fun Any?.trimActiveNotificationAttributeValue(): Any? = when (this) {
    is CharSequence -> toString().limitNotificationAttributeString()
    is List<*> -> take(MAX_ACTIVE_NOTIFICATION_ATTRIBUTE_LIST_SIZE).map {
        when (it) {
            is CharSequence -> it.toString().limitNotificationAttributeString()
            else -> it
        }
    }
    else -> this
}

private fun String.limitNotificationAttributeString(): String =
    if (length <= MAX_ACTIVE_NOTIFICATION_ATTRIBUTE_STRING_LENGTH) {
        this
    } else {
        take(MAX_ACTIVE_NOTIFICATION_ATTRIBUTE_STRING_LENGTH) + "..."
    }

private fun activeNotificationSnapshot(records: List<ActiveNotificationRecord>, totalCount: Int): String {
    val source = records.joinToString("|", prefix = "$totalCount|") { record ->
        "${record.postTime}:${record.attributes.keys.joinToString(",")}"
    }
    return Integer.toUnsignedString(source.hashCode(), 36)
}

internal fun Map<String, Any?>.serializedAttributeBytes(): Int {
    return kotlinJsonMapper.encodeToString(
        MapAnySerializer,
        mapValues { it.value.toRecorderBudgetValue() },
    ).encodeToByteArray().size
}

private fun Any?.toRecorderBudgetValue(): Any? = when (this) {
    null,
    is Boolean,
    is Number,
    is String,
    -> this
    is CharSequence -> toString()
    is List<*> -> map { it.toRecorderBudgetValue() }
    is Array<*> -> map { it.toRecorderBudgetValue() }
    is Map<*, *> -> entries.associate { it.key.toString() to it.value.toRecorderBudgetValue() }
    else -> toString()
}
}
