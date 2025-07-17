package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.onboarding.discovery.HomeAssistantInstance
import java.net.URL
import java.time.LocalDateTime

val notificationItem = NotificationItem(1, 1636389288682, "testing", "{\"message\":\"test\"}", "FCM", null)

val wearDeviceName = "Device Name"

val attributes: Map<String, *> = mapOf(
    "friendly_name" to "Testing",
    "icon" to "mdi:cellphone",
)

private val localDateTime: LocalDateTime = LocalDateTime.now()

val previewEntity1 = Entity("light.test", "on", attributes, localDateTime, localDateTime)
val previewEntity2 = Entity("scene.testing", "on", attributes, localDateTime, localDateTime)
val previewEntity3 = Entity("switch.testing", "on", attributes, localDateTime, localDateTime)

val previewEntityList = mapOf(
    previewEntity1.entityId to previewEntity1,
    previewEntity2.entityId to previewEntity2,
    previewEntity3.entityId to previewEntity3,
)

val previewFavoritesList = listOf("light.test")

val homeAssistantInstance1 =
    HomeAssistantInstance(
        name = "Home",
        url = URL("https://google.com"),
        version = HomeAssistantVersion(year = 2024, month = 1, release = 1),
    )
val homeAssistantInstance2 =
    HomeAssistantInstance(
        name = "Vacation Home",
        url = URL("http://localhost"),
        version = HomeAssistantVersion(year = 2024, month = 1, release = 1),
    )

val previewServer1 =
    Server(
        id = 0,
        _name = "Home",
        listOrder = -1,
        connection = ServerConnectionInfo(externalUrl = ""),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )
val previewServer2 =
    Server(
        id = 1,
        _name = "Friends home",
        listOrder = -1,
        connection = ServerConnectionInfo(externalUrl = ""),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )
