package io.homeassistant.companion.android.common.data.websocket

import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_AREA_REGISTRY_UPDATED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_DEVICE_REGISTRY_UPDATED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_ENTITY_REGISTRY_UPDATED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_STATE_CHANGED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_RENDER_TEMPLATE
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineError
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEventType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentProgress
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentStart
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineRunStart
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineSttEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineTtsEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EventResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EventSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TemplateUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TriggerEvent
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

class WebSocketEventHandler {
    suspend fun handleEvent(response: EventSocketResponse) {
        // TODO https://github.com/home-assistant/android/issues/5271
        val subscriptionId = response.id

        activeMessages[subscriptionId]?.let { messageData ->
            val subscriptionType = messageData.message["type"]
            val eventResponseType = (response.event as? JsonObject)?.get("event_type")

            val message: Any =
                if ((response.event as? JsonObject)?.contains("hass_confirm_id") == true) {
                    kotlinJsonMapper.decodeFromJsonElement<Map<String, Any?>>(MapAnySerializer, response.event)
                } else if (subscriptionType == SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES) {
                    if (response.event != null) {
                        kotlinJsonMapper.decodeFromJsonElement<CompressedStateChangedEvent>(response.event)
                    } else {
                        Timber.w("Received no event for entity subscription, skipping")
                        return
                    }
                } else if (subscriptionType == SUBSCRIBE_TYPE_RENDER_TEMPLATE) {
                    if (response.event != null) {
                        kotlinJsonMapper.decodeFromJsonElement<TemplateUpdatedEvent>(response.event)
                    } else {
                        Timber.w("Received no event for template subscription, skipping")
                        return
                    }
                } else if (subscriptionType == SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER) {
                    val trigger = ((response.event as? JsonObject)?.get("variables") as? JsonObject)?.get("trigger")
                    if (trigger != null) {
                        kotlinJsonMapper.decodeFromJsonElement<TriggerEvent>(trigger)
                    } else {
                        Timber.w("Received no trigger value for trigger subscription, skipping")
                        return
                    }
                } else if (subscriptionType == SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN) {
                    val eventType = (response.event as? JsonObject)?.get("type")
                    if ((eventType as? JsonPrimitive)?.isString == true) {
                        val eventDataMap = response.event["data"]
                        if (eventDataMap == null) {
                            Timber.w("Received Assist pipeline event without data, skipping")
                            return
                        }
                        val eventData = when (eventType.jsonPrimitive.content) {
                            AssistPipelineEventType.RUN_START ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineRunStart>(eventDataMap)

                            AssistPipelineEventType.STT_END ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineSttEnd>(eventDataMap)

                            AssistPipelineEventType.INTENT_START ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineIntentStart>(eventDataMap)

                            AssistPipelineEventType.INTENT_PROGRESS ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineIntentProgress>(eventDataMap)

                            AssistPipelineEventType.INTENT_END ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineIntentEnd>(eventDataMap)

                            AssistPipelineEventType.TTS_END ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineTtsEnd>(eventDataMap)

                            AssistPipelineEventType.ERROR ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineError>(eventDataMap)

                            else -> {
                                Timber.d("Unknown event type ignoring. received data = \n$response")
                                null
                            }
                        }
                        AssistPipelineEvent(eventType.jsonPrimitive.content, eventData)
                    } else {
                        Timber.w("Received Assist pipeline event without type, skipping")
                        return
                    }
                } else if (eventResponseType != null && (eventResponseType as? JsonPrimitive)?.isString == true) {
                    when (eventResponseType.content) {
                        EVENT_STATE_CHANGED -> {
                            kotlinJsonMapper.decodeFromJsonElement<EventResponse<StateChangedEvent>>(
                                response.event,
                            ).data
                        }

                        EVENT_AREA_REGISTRY_UPDATED -> {
                            kotlinJsonMapper.decodeFromJsonElement<EventResponse<AreaRegistryUpdatedEvent>>(
                                response.event,
                            ).data
                        }

                        EVENT_DEVICE_REGISTRY_UPDATED -> {
                            kotlinJsonMapper.decodeFromJsonElement<EventResponse<DeviceRegistryUpdatedEvent>>(
                                response.event,
                            ).data
                        }

                        EVENT_ENTITY_REGISTRY_UPDATED -> {
                            kotlinJsonMapper.decodeFromJsonElement<EventResponse<EntityRegistryUpdatedEvent>>(
                                response.event,
                            ).data
                        }

                        else -> {
                            Timber.d("Unknown event type received ${response.event}")
                            return
                        }
                    }
                } else {
                    Timber.d("Unknown event for subscription received, skipping")
                    return
                }

            try {
                messageData.onEvent?.send(message)
            } catch (e: Exception) {
                Timber.e(e, "Unable to send event message to channel")
            }
        } ?: run {
            Timber.d("Received event for unknown subscription, unsubscribing")
            sendMessage(
                mapOf(
                    "type" to "unsubscribe_events",
                    "subscription" to subscriptionId,
                ),
            )
        }
    }
}
