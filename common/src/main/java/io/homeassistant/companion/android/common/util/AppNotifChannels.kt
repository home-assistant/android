package io.homeassistant.companion.android.common.util

const val sensorWorkerChannel = "Sensor Worker"
const val sensorCoreSyncChannel = "Sensor Sync"
const val websocketChannel = "Websocket"
const val highAccuracyChannel = "High accuracy location"
const val databaseChannel = "App Database"
const val locationDisabledChannel = "Location disabled"
const val downloadsChannel = "downloads"
const val generalChannel = "general"

val appCreatedChannels = listOf(
    sensorWorkerChannel,
    sensorCoreSyncChannel,
    websocketChannel,
    highAccuracyChannel,
    databaseChannel,
    locationDisabledChannel,
    downloadsChannel,
    generalChannel
)
