package io.homeassistant.companion.android.common.data.websocket.impl

import io.homeassistant.companion.android.common.data.integration.ActionData
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.TODO_DOMAIN
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketCore
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRequest
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_AREA_REGISTRY_UPDATED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_DEVICE_REGISTRY_UPDATED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_ENTITY_REGISTRY_UPDATED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_STATE_CHANGED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_PUSH_NOTIFICATION_CHANNEL
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_RENDER_TEMPLATE
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineListResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ConversationResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CurrentUserResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DomainResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.PongSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.RawMessageSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TemplateUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetTlvResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TriggerEvent
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.common.util.toHexString
import io.homeassistant.companion.android.database.server.ServerUserInfo
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import okhttp3.WebSocketListener

private val matterTimeout = 2.minutes

class WebSocketRepositoryImpl internal constructor(
    private val webSocketCore: WebSocketCore,
    private val serverManager: ServerManager,
) : WebSocketListener(),
    WebSocketRepository {

    override fun getConnectionState(): WebSocketState? {
        return webSocketCore.getConnectionState()
    }

    override fun shutdown() {
        return webSocketCore.shutdown()
    }

    override suspend fun sendPing(): Boolean {
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "ping",
            ),
        )
        return socketResponse is PongSocketResponse
    }

    override suspend fun getConfig(): GetConfigResponse? {
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "get_config",
            ),
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getTodos(entityId: String): GetTodosResponse? {
        val response = webSocketCore.sendMessage(
            mapOf(
                "type" to "call_service",
                "domain" to TODO_DOMAIN,
                "service" to "get_items",
                "target" to mapOf(
                    "entity_id" to entityId,
                ),
                "return_response" to true,
            ),
        )
        return mapResponse(response)
    }

    override suspend fun updateTodo(entityId: String, todoItem: String, newName: String?, status: String?): Boolean {
        val response = webSocketCore.sendMessage(
            mapOf(
                "type" to "call_service",
                "domain" to TODO_DOMAIN,
                "service" to "update_item",
                "target" to mapOf(
                    "entity_id" to entityId,
                ),
                "service_data" to mapOf(
                    "item" to todoItem,
                    "status" to status,
                    "rename" to newName,
                ).filterValues { it != null },
            ),
        )
        return response?.success == true
    }

    override suspend fun getCurrentUser(): CurrentUserResponse? {
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "auth/current_user",
            ),
        )

        val response: CurrentUserResponse? = mapResponse(socketResponse)
        response?.let { updateServerWithUser(it) }
        return response
    }

    override suspend fun getStates(): List<EntityResponse>? {
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "get_states",
            ),
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getAreaRegistry(): List<AreaRegistryResponse>? {
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "config/area_registry/list",
            ),
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getDeviceRegistry(): List<DeviceRegistryResponse>? {
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "config/device_registry/list",
            ),
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getEntityRegistry(): List<EntityRegistryResponse>? {
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "config/entity_registry/list",
            ),
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getEntityRegistryFor(entityId: String): EntityRegistryResponse? {
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "config/entity_registry/get",
                "entity_id" to entityId,
            ),
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getServices(): List<DomainResponse>? {
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "get_services",
            ),
        )

        val response: Map<String, Map<String, ActionData>>? = mapResponse(socketResponse)
        return response?.map {
            DomainResponse(it.key, it.value)
        }
    }

    override suspend fun getConversation(speech: String): ConversationResponse? {
        // TODO: Send default locale of device with request.
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "conversation/process",
                "text" to speech,
            ),
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getAssistPipeline(pipelineId: String?): AssistPipelineResponse? {
        val data = mapOf(
            "type" to "assist_pipeline/pipeline/get",
        )
        val socketResponse = webSocketCore.sendMessage(
            if (pipelineId != null) data.plus("pipeline_id" to pipelineId) else data,
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getAssistPipelines(): AssistPipelineListResponse? {
        val socketResponse = webSocketCore.sendMessage(
            mapOf(
                "type" to "assist_pipeline/pipeline/list",
            ),
        )

        return mapResponse(socketResponse)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun runAssistPipelineForText(
        text: String,
        pipelineId: String?,
        conversationId: String?,
    ): Flow<AssistPipelineEvent>? {
        var data = mapOf(
            "start_stage" to "intent",
            "end_stage" to "intent",
            "input" to mapOf(
                "text" to text,
            ),
            "conversation_id" to conversationId,
        )
        pipelineId?.let {
            data = data.plus("pipeline" to it)
        }
        webSocketCore.server()?.deviceRegistryId?.let {
            data = data.plus("device_id" to it)
        }
        return webSocketCore.subscribeTo(
            SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN,
            data,
        )
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun runAssistPipelineForVoice(
        sampleRate: Int,
        outputTts: Boolean,
        pipelineId: String?,
        conversationId: String?,
    ): Flow<AssistPipelineEvent>? {
        var data = mapOf(
            "start_stage" to "stt",
            "end_stage" to (if (outputTts) "tts" else "intent"),
            "input" to mapOf(
                "sample_rate" to sampleRate,
            ),
            "conversation_id" to conversationId,
        )
        pipelineId?.let {
            data = data.plus("pipeline" to it)
        }
        webSocketCore.server()?.deviceRegistryId?.let {
            data = data.plus("device_id" to it)
        }
        return webSocketCore.subscribeTo(
            SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN,
            data,
        )
    }

    override suspend fun sendVoiceData(binaryHandlerId: Int, data: ByteArray): Boolean? =
        webSocketCore.sendBytes(byteArrayOf(binaryHandlerId.toByte()) + data)

    override suspend fun getStateChanges(): Flow<StateChangedEvent>? = subscribeToEventsForType(EVENT_STATE_CHANGED)

    override suspend fun getStateChanges(entityIds: List<String>): Flow<TriggerEvent>? =
        subscribeToTrigger("state", mapOf("entity_id" to entityIds))

    override suspend fun getCompressedStateAndChanges(): Flow<CompressedStateChangedEvent>? =
        webSocketCore.subscribeTo(SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES)

    override suspend fun getCompressedStateAndChanges(entityIds: List<String>): Flow<CompressedStateChangedEvent>? =
        webSocketCore.subscribeTo(SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES, mapOf("entity_ids" to entityIds))

    override suspend fun getAreaRegistryUpdates(): Flow<AreaRegistryUpdatedEvent>? =
        subscribeToEventsForType(EVENT_AREA_REGISTRY_UPDATED)

    override suspend fun getDeviceRegistryUpdates(): Flow<DeviceRegistryUpdatedEvent>? =
        subscribeToEventsForType(EVENT_DEVICE_REGISTRY_UPDATED)

    override suspend fun getEntityRegistryUpdates(): Flow<EntityRegistryUpdatedEvent>? =
        subscribeToEventsForType(EVENT_ENTITY_REGISTRY_UPDATED)

    private suspend fun <T : Any> subscribeToEventsForType(eventType: String): Flow<T>? =
        webSocketCore.subscribeTo(SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS, mapOf("event_type" to eventType))

    override suspend fun getTemplateUpdates(template: String): Flow<TemplateUpdatedEvent>? =
        webSocketCore.subscribeTo(SUBSCRIBE_TYPE_RENDER_TEMPLATE, mapOf("template" to template))

    private suspend fun subscribeToTrigger(platform: String, data: Map<Any, Any>): Flow<TriggerEvent>? {
        val triggerData = mapOf(
            "platform" to platform,
        ).plus(data)
        return webSocketCore.subscribeTo(SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER, mapOf("trigger" to triggerData))
    }

    override suspend fun getNotifications(): Flow<Map<String, Any>>? = webSocketCore.server()?.let {
        webSocketCore.subscribeTo(
            SUBSCRIBE_TYPE_PUSH_NOTIFICATION_CHANNEL,
            mapOf(
                "webhook_id" to it.connection.webhookId!!,
                "support_confirm" to true,
            ),
            10.seconds,
        )
    }

    override suspend fun ackNotification(confirmId: String): Boolean {
        val response = webSocketCore.server()?.let {
            webSocketCore.sendMessage(
                mapOf(
                    "type" to "mobile_app/push_notification_confirm",
                    "webhook_id" to it.connection.webhookId!!,
                    "confirm_id" to confirmId,
                ),
            )
        }
        return response?.success == true
    }

    override suspend fun commissionMatterDevice(code: String): MatterCommissionResponse? {
        val response = webSocketCore.sendMessage(
            WebSocketRequest(
                message = mapOf(
                    "type" to "matter/commission",
                    "code" to code,
                ),
                // Matter commissioning takes at least 60 seconds + interview
                timeout = matterTimeout,
            ),
        )

        return response?.let {
            MatterCommissionResponse(
                success = response.success == true,
                errorCode = ((response.error as? JsonObject)?.get("code") as? JsonPrimitive)?.intOrNull,
            )
        }
    }

    override suspend fun commissionMatterDeviceOnNetwork(pin: Long, ip: String): MatterCommissionResponse? {
        val data = mapOf(
            "type" to "matter/commission_on_network",
            "pin" to pin,
        )
        val response = webSocketCore.sendMessage(
            WebSocketRequest(
                message = if (webSocketCore.server()?.version?.isAtLeast(2024, 1) ==
                    true
                ) {
                    data.plus("ip_addr" to ip)
                } else {
                    data
                },
                // Matter commissioning takes at least 60 seconds + interview
                timeout = matterTimeout,
            ),
        )

        return response?.let {
            MatterCommissionResponse(
                success = response.success == true,
                errorCode = ((response.error as? JsonObject)?.get("code") as? JsonPrimitive)?.intOrNull,
            )
        }
    }

    override suspend fun getThreadDatasets(): List<ThreadDatasetResponse>? {
        val response = webSocketCore.sendMessage(
            mapOf(
                "type" to "thread/list_datasets",
            ),
        )

        val result = (response?.result as? JsonObject)?.get("datasets")
        return if (response?.success == true && result != null) {
            kotlinJsonMapper.decodeFromJsonElement(result)
        } else {
            null
        }
    }

    override suspend fun getThreadDatasetTlv(datasetId: String): ThreadDatasetTlvResponse? {
        val response = webSocketCore.sendMessage(
            mapOf(
                "type" to "thread/get_dataset_tlv",
                "dataset_id" to datasetId,
            ),
        )

        return mapResponse(response)
    }

    override suspend fun addThreadDataset(tlv: ByteArray): Boolean {
        val response = webSocketCore.sendMessage(
            mapOf(
                "type" to "thread/add_dataset_tlv",
                "source" to "Google",
                "tlv" to tlv.toHexString(),
            ),
        )
        return response?.success == true
    }

    /**
     * Update server entry in [serverManager] with information from a [CurrentUserResponse] like user
     * name and admin status.
     */
    private suspend fun updateServerWithUser(user: CurrentUserResponse) {
        webSocketCore.server()?.let {
            serverManager.updateServer(
                it.copy(
                    user = ServerUserInfo(
                        id = user.id,
                        name = user.name,
                        isOwner = user.isOwner,
                        isAdmin = user.isAdmin,
                    ),
                ),
            )
        }
    }

    private inline fun <reified T> mapResponse(response: RawMessageSocketResponse?): T? =
        response?.result?.run { kotlinJsonMapper.decodeFromJsonElement(this) }
}
