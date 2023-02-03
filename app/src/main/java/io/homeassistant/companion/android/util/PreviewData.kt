package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.notification.NotificationItem
import java.util.Calendar

val notificationItem = NotificationItem(1, 1636389288682, "testing", "{\"message\":\"test\"}", "FCM", null)

val wearDeviceName = "Device Name"

val attributes: Map<*, *> = mapOf(
    "friendly_name" to "Testing",
    "icon" to "mdi:cellphone"
)

private val calendar: Calendar = Calendar.getInstance()

val previewEntity1 = Entity("light.test", "on", attributes, calendar, calendar, mapOf())
val previewEntity2 = Entity("scene.testing", "on", attributes, calendar, calendar, mapOf())
val previewEntity3 = Entity("switch.testing", "on", attributes, calendar, calendar, mapOf())

val previewEntityList = mapOf(
    previewEntity1.entityId to previewEntity1,
    previewEntity2.entityId to previewEntity2,
    previewEntity3.entityId to previewEntity3
)

val previewFavoritesList = listOf("light.test")
