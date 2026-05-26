package io.homeassistant.companion.android.sensors

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Thin [NotificationListenerService] that forwards the framework's notification callbacks to
 * [NotificationListenerSensorManager], which owns all the sensor logic.
 *
 * Registered in the manifest as a `BIND_NOTIFICATION_LISTENER_SERVICE`.
 *
 * The class name must stay `NotificationSensorManager` for backward compatibility: Android ties the
 * user's granted notification-listener access to this service's component name, so renaming it would
 * silently revoke access for existing users, forcing them to grant it again.
 */
@AndroidEntryPoint
class NotificationSensorManager : NotificationListenerService() {

    @Inject
    lateinit var notificationListenerSensorManager: NotificationListenerSensorManager

    override fun onListenerConnected() {
        super.onListenerConnected()
        notificationListenerSensorManager.onListenerConnectionChanged(connected = true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        notificationListenerSensorManager.onListenerConnectionChanged(connected = false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        notificationListenerSensorManager.onNotificationPosted(sbn, activeNotifications)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        notificationListenerSensorManager.onNotificationRemoved(sbn, activeNotifications)
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
