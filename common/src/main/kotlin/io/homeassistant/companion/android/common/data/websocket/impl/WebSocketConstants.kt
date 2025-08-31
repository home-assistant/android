package io.homeassistant.companion.android.common.data.websocket.impl

internal object WebSocketConstants {

    const val EVENT_STATE_CHANGED = "state_changed"
    const val EVENT_AREA_REGISTRY_UPDATED = "area_registry_updated"
    const val EVENT_DEVICE_REGISTRY_UPDATED = "device_registry_updated"
    const val EVENT_ENTITY_REGISTRY_UPDATED = "entity_registry_updated"

    const val SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN = "assist_pipeline/run"
    const val SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS = "subscribe_events"
    const val SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES = "subscribe_entities"
    const val SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER = "subscribe_trigger"
    const val SUBSCRIBE_TYPE_RENDER_TEMPLATE = "render_template"
    const val SUBSCRIBE_TYPE_PUSH_NOTIFICATION_CHANNEL =
        "mobile_app/push_notification_channel"
}
