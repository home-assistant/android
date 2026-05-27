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
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.database.sensor.toSensorsWithAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

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

class NotificationSensorManager :
    NotificationListenerService(),
    SensorManager {
    companion object {
        private const val SETTING_ALLOW_LIST = "notification_allow_list"
        private const val SETTING_DISABLE_ALLOW_LIST = "notification_disable_allow_list"
        private const val SETTING_INCLUDE_CONTENTS_AS_ATTRS = "active_notification_count_content_attrs"

        private var listenerConnected = false
        val lastNotification = SensorManager.BasicSensor(
            "last_notification",
            "sensor",
            commonR.string.basic_sensor_name_last_notification,
            commonR.string.sensor_description_last_notification,
            "mdi:bell-ring",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#last-notification",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT_ONLY,
        )
        val lastRemovedNotification = SensorManager.BasicSensor(
            "last_removed_notification",
            "sensor",
            commonR.string.basic_sensor_name_last_removed_notification,
            commonR.string.sensor_description_last_removed_notification,
            "mdi:bell-ring",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#last-removed-notification",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT_ONLY,
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
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        private val mediaSession = SensorManager.BasicSensor(
            "media_session",
            "sensor",
            commonR.string.basic_sensor_name_media_session,
            commonR.string.sensor_description_media_session,
            "mdi:play-circle",
            deviceClass = "enum",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#media-session-sensor",
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#notification-sensors"
    }
    override fun hasSensor(context: Context): Boolean {
        return if (!context.isAutomotive()) {
            val uiManager = context.getSystemService<UiModeManager>()
            uiManager?.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION
        } else {
            false
        }
    }
    override val name: Int
        get() = commonR.string.sensor_name_last_notification
    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(lastNotification, lastRemovedNotification, activeNotificationCount, mediaSession)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
    }

    override suspend fun checkPermission(context: Context, sensorId: String): Boolean {
        return NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    override suspend fun requestSensorUpdate(context: Context) {
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

        sensorWorkerScope.launch {
            if (!isEnabled(applicationContext, lastNotification)) {
                return@launch
            }

            val allowPackages = getSetting(
                applicationContext,
                lastNotification,
                SETTING_ALLOW_LIST,
                SensorSettingType.LIST_APPS,
                default = "",
            ).split(", ").filter { it.isNotBlank() }

            val disableAllowListRequirement = getToggleSetting(
                applicationContext,
                lastNotification,
                SETTING_DISABLE_ALLOW_LIST,
                default = false,
            )

            if (sbn.packageName == application.packageName ||
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    put("channel_id", sbn.notification.channelId)
                }
            }

            // Attempt to use the text of the notification but fallback to package name if all else fails.
            val state = attrs["android.text"] ?: attrs["android.title"] ?: sbn.packageName

            onSensorUpdated(
                applicationContext,
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

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        updateActiveNotificationCount()

        sensorWorkerScope.launch {
            if (!isEnabled(applicationContext, lastRemovedNotification)) {
                return@launch
            }

            val allowPackages = getSetting(
                applicationContext,
                lastRemovedNotification,
                SETTING_ALLOW_LIST,
                SensorSettingType.LIST_APPS,
                default = "",
            ).split(", ").filter { it.isNotBlank() }

            val disableAllowListRequirement = getToggleSetting(
                applicationContext,
                lastRemovedNotification,
                SETTING_DISABLE_ALLOW_LIST,
                default = false,
            )

            if (sbn.packageName == application.packageName ||
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    put("channel_id", sbn.notification.channelId)
                }
            }

            // Attempt to use the text of the notification but fallback to package name if all else fails.
            val state = attrs["android.text"] ?: attrs["android.title"] ?: sbn.packageName

            onSensorUpdated(
                applicationContext,
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

    private fun updateActiveNotificationCount() {
        sensorWorkerScope.launch {
            if (!isEnabled(applicationContext, activeNotificationCount) || !listenerConnected) {
                return@launch
            }

            try {
                val activeNotificationSnapshot = activeNotifications.toList()
                val activeNotificationTotal = activeNotificationSnapshot.size
                val includeContentsAsAttrsSetting =
                    getToggleSetting(
                        applicationContext,
                        activeNotificationCount,
                        SETTING_INCLUDE_CONTENTS_AS_ATTRS,
                        default = true,
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

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            applicationContext,
            activeNotificationCount,
            totalCount,
            activeNotificationCount.statelessIcon,
            attrs,
            forceUpdate = true,
        )

        withContext(Dispatchers.IO) {
            val sensorDao = sensorDao(applicationContext)
            val serverManager = serverManager(applicationContext)
            val fullSensors = sensorDao.getFull(activeNotificationCount.id).toSensorsWithAttributes()
            fullSensors.filter {
                it.sensor.enabled && it.sensor.registered == true
            }.forEach { fullSensor ->
                try {
                    val registration = fullSensor.toSensorRegistration(activeNotificationCount)
                    val success = serverManager.integrationRepository(fullSensor.sensor.serverId)
                        .updateSensors(listOf(registration))
                    if (success) {
                        sensorDao.updateLastSentStateAndIcon(
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

    private suspend fun updateMediaSession(context: Context) {
        if (!isEnabled(context, mediaSession)) {
            return
        }

        val mediaSessionManager = context.getSystemService<MediaSessionManager>()!!
        val mediaList = mediaSessionManager.getActiveSessions(
            ComponentName(context, NotificationSensorManager::class.java),
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
        if (mediaList.size > 0) {
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
            context,
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
