package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.integration.Entity
import java.util.Calendar

val attributes: Map<*, *> = mapOf(
    "friendly_name" to "Testing",
    "icon" to "mdi:cellphone"
)

private val calendar: Calendar = Calendar.getInstance()

val previewEntity1 = Entity("light.test", "on", attributes, calendar, calendar, mapOf())
val previewEntity2 = Entity("scene.test", "on", attributes, calendar, calendar, mapOf())

val previewEntityList = mapOf(
    previewEntity1.entityId to previewEntity1,
    previewEntity2.entityId to previewEntity2
)

val previewFavoritesList = listOf("light.test")
