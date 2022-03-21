package io.homeassistant.companion.android.common.util

const val sensorWorkerChannel = "Sensor Worker"
const val websocketChannel = "Websocket"
const val highAccuracyChannel = "High accuracy location"
const val databaseChannel = "App Database"
const val locationDisabledChannel = "Location disabled"
const val generalChannel = "general"

val appCreatedChannels = listOf(
    sensorWorkerChannel,
    websocketChannel,
    highAccuracyChannel,
    databaseChannel,
    locationDisabledChannel,
    generalChannel
)
