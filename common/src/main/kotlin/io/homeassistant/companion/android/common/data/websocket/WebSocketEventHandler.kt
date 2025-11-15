package io.homeassistant.companion.android.common.data.websocket

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT_STRING
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketCore
import io.homeassistant.companion.android.common.data.websocket.WebSocketRequest
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
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
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AuthInvalidSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AuthOkSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AuthRequiredSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EventResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EventSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MessageSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.PongSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.RawMessageSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TemplateUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TriggerEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.UnknownTypeSocketResponse
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
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

