package io.homeassistant.companion.android.common.data.websocket.impl

import io.homeassistant.companion.android.common.util.jacksonObjectMapperForHACore

internal val webSocketMapper = jacksonObjectMapperForHACore()

internal const val EVENT_STATE_CHANGED = "state_changed"
internal const val EVENT_AREA_REGISTRY_UPDATED = "area_registry_updated"
internal const val EVENT_DEVICE_REGISTRY_UPDATED = "device_registry_updated"
internal const val EVENT_ENTITY_REGISTRY_UPDATED = "entity_registry_updated"

internal const val SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN = "assist_pipeline/run"
internal const val SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS = "subscribe_events"
internal const val SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES = "subscribe_entities"
internal const val SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER = "subscribe_trigger"
internal const val SUBSCRIBE_TYPE_RENDER_TEMPLATE = "render_template"
internal const val SUBSCRIBE_TYPE_PUSH_NOTIFICATION_CHANNEL =
    "mobile_app/push_notification_channel"
