package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.data.SimplifiedEntity
import java.util.Calendar

val attributes: Map<*, *> = mapOf(
    "friendly_name" to "Testing",
    "icon" to "mdi:cellphone"
)

private val calendar: Calendar = Calendar.getInstance()

val previewEntity1 = Entity("light.first", "on", attributes, calendar, calendar, mapOf())
val previewEntity2 = Entity("light.second", "off", attributes, calendar, calendar, mapOf())
val previewEntity3 = Entity("scene.first", "on", attributes, calendar, calendar, mapOf())

val previewEntityList = mapOf(
    previewEntity1.entityId to previewEntity1,
    previewEntity2.entityId to previewEntity2,
    previewEntity3.entityId to previewEntity3
)

val previewFavoritesList = listOf("light.first", "scene.first")

val simplifiedEntity = SimplifiedEntity(previewEntity1.entityId, attributes["friendly_name"].toString(), attributes["icon"].toString())
