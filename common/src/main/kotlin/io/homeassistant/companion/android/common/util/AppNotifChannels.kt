package io.homeassistant.companion.android.common.util

/**
 * Notification channel IDs for channels created by the app.
 *
 * These are distinct from channels created dynamically by the Home Assistant server
 * through notification commands.
 */
const val CHANNEL_SENSOR_WORKER = "Sensor Worker"
const val CHANNEL_SENSOR_SYNC = "Sensor Sync"
const val CHANNEL_WEBSOCKET = "Websocket"
const val CHANNEL_WEBSOCKET_ISSUES = "Websocket Issues"
const val CHANNEL_HIGH_ACCURACY = "High accuracy location"
const val CHANNEL_DATABASE = "App Database"
const val CHANNEL_LOCATION_DISABLED = "Location disabled"
const val CHANNEL_DOWNLOADS = "downloads"
const val CHANNEL_GENERAL = "general"
const val CHANNEL_BEACON_MONITOR = "beacon"
const val CHANNEL_ASSIST_LISTENING = "assist_listening"

/**
 * List of all notification channel IDs created by the app.
 *
 * Used to distinguish app-created channels from server-created channels when managing
 * notification settings.
 *
 * **Important:** Each channel ID must be unique. This invariant is enforced by unit tests.
 */
val appCreatedChannels = listOf(
    CHANNEL_SENSOR_WORKER,
    CHANNEL_SENSOR_SYNC,
    CHANNEL_WEBSOCKET,
    CHANNEL_WEBSOCKET_ISSUES,
    CHANNEL_HIGH_ACCURACY,
    CHANNEL_DATABASE,
    CHANNEL_LOCATION_DISABLED,
    CHANNEL_DOWNLOADS,
    CHANNEL_GENERAL,
    CHANNEL_BEACON_MONITOR,
    CHANNEL_ASSIST_LISTENING
)
