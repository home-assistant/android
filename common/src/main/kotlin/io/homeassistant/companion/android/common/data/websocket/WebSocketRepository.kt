package io.homeassistant.companion.android.common.data.websocket

import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketRepositoryImpl
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineListResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraCapabilitiesResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraStreamTypes
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraWebRtcClientConfigResult
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
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TemplateUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetTlvResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TriggerEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcCandidate
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcEvent
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow

interface WebSocketRepository {
    fun getConnectionState(): WebSocketState
    fun shutdown()
    suspend fun sendPing(): Boolean
    suspend fun getCurrentUser(): CurrentUserResponse?
    suspend fun getConfig(): GetConfigResponse?
    suspend fun getStates(): List<EntityResponse>?
    suspend fun getAreaRegistry(): List<AreaRegistryResponse>?
    suspend fun getDeviceRegistry(): List<DeviceRegistryResponse>?
    suspend fun getEntityRegistry(): List<EntityRegistryResponse>?
    suspend fun getEntityRegistryFor(entityId: String): EntityRegistryResponse?
    suspend fun getServices(): List<DomainResponse>?
    suspend fun getStateChanges(): Flow<StateChangedEvent>?
    suspend fun getStateChanges(entityIds: List<String>): Flow<TriggerEvent>?
    suspend fun getCompressedStateAndChanges(): Flow<CompressedStateChangedEvent>?
    suspend fun getCompressedStateAndChanges(entityIds: List<String>): Flow<CompressedStateChangedEvent>?
    suspend fun getAreaRegistryUpdates(): Flow<AreaRegistryUpdatedEvent>?
    suspend fun getDeviceRegistryUpdates(): Flow<DeviceRegistryUpdatedEvent>?
    suspend fun getEntityRegistryUpdates(): Flow<EntityRegistryUpdatedEvent>?
    suspend fun getTemplateUpdates(template: String): Flow<TemplateUpdatedEvent>?
    suspend fun getNotifications(): Flow<Map<String, Any>>?
    suspend fun ackNotification(confirmId: String): Boolean

    suspend fun getTodos(entityId: String): GetTodosResponse?
    suspend fun updateTodo(entityId: String, todoItem: String, newName: String?, status: String?): Boolean

    /**
     * Request the server to add a Matter device to the network and commission it.
     * @return [MatterCommissionResponse] detailing the server's response, or `null` if the server
     * did not return a response.
     */
    suspend fun commissionMatterDevice(code: String): MatterCommissionResponse?

    /**
     * Request the server to commission a Matter device that is already on the network.
     * @return [MatterCommissionResponse] detailing the server's response, or `null` if the server
     * did not return a response.
     */
    suspend fun commissionMatterDeviceOnNetwork(pin: Long, ip: String): MatterCommissionResponse?

    /**
     * Return a list of all Thread datasets known to the server.
     * @return List with [ThreadDatasetResponse]s, or `null` if not an admin or no response.
     */
    suspend fun getThreadDatasets(): List<ThreadDatasetResponse>?

    /**
     * Return the TLV value for a dataset.
     * @return [ThreadDatasetTlvResponse] for the Thread dataset, or `null` if not found, not an
     * admin or no response.
     */
    suspend fun getThreadDatasetTlv(datasetId: String): ThreadDatasetTlvResponse?

    /**
     * Add a new set of Thread network credentials to the server.
     * @return `true` if the server indicated success
     */
    suspend fun addThreadDataset(tlv: ByteArray): Boolean

    /**
     * Get an Assist response for the given text input. For core >= 2023.5, use [runAssistPipelineForText]
     * instead.
     */
    suspend fun getConversation(speech: String): ConversationResponse?

    /**
     * Get information about an Assist pipeline.
     * @param pipelineId the ID of the pipeline to get details for, if not specified the preferred
     * pipeline will be returned
     * @return [AssistPipelineResponse] detailing the Assist pipeline, or `null` if not found or no
     * response.
     */
    suspend fun getAssistPipeline(pipelineId: String? = null): AssistPipelineResponse?

    /**
     * @return [AssistPipelineListResponse] listing all Assist pipelines and which one is preferred.
     */
    suspend fun getAssistPipelines(): AssistPipelineListResponse?

    /**
     * Run the Assist pipeline for the given text input
     * @return a Flow that will emit all events for the pipeline
     */
    suspend fun runAssistPipelineForText(
        text: String,
        pipelineId: String? = null,
        conversationId: String? = null,
    ): Flow<AssistPipelineEvent>?

    /**
     * Run the Assist pipeline for voice input
     * @return a Flow that will emit all events for the pipeline
     */
    suspend fun runAssistPipelineForVoice(
        sampleRate: Int,
        outputTts: Boolean,
        pipelineId: String? = null,
        conversationId: String? = null,
        wakeWordPhrase: String? = null,
    ): Flow<AssistPipelineEvent>?

    /**
     * Send voice data for an active Assist pipeline
     * @return `true`/`false` indicating if it was enqueued, or `null` on unexpected failures
     */
    suspend fun sendVoiceData(binaryHandlerId: Int, data: ByteArray): Boolean

    /**
     * Get the stream types the frontend can use for a camera entity. Consumers should only start
     * a WebRTC session when [CameraStreamTypes.WEB_RTC] is reported and fall back to HLS
     * otherwise.
     *
     * Requires Home Assistant Core 2024.11 or later.
     *
     * @return [CameraCapabilitiesResponse] for the entity, or `null` if the server did not return
     * a successful response.
     */
    suspend fun getCameraCapabilities(entityId: String): CameraCapabilitiesResponse?

    /**
     * Get the WebRTC client configuration (STUN/TURN servers and optional data channel label) to
     * use when creating a peer connection for a camera entity.
     *
     * Requires Home Assistant Core 2024.11 or later.
     *
     * @return [CameraWebRtcClientConfigResult.Success] with the configuration, or
     * [CameraWebRtcClientConfigResult.Failure] carrying the server error when the command failed,
     * for example when the camera does not support WebRTC (`webrtc_get_client_config_failed`).
     */
    suspend fun getCameraWebRtcClientConfig(entityId: String): CameraWebRtcClientConfigResult

    /**
     * Start a WebRTC session for a camera entity by sending the SDP offer, and subscribe to the
     * signaling events for this session.
     *
     * The subscription lifetime is the session lifetime: when the returned Flow is no longer
     * collected the subscription is cancelled with `unsubscribe_events`, which closes the WebRTC
     * session on the server (there is no dedicated close command).
     *
     * Requires Home Assistant Core 2024.11 or later (2024.12 or later for trickle ICE with
     * `RTCIceCandidateInit` dictionaries).
     *
     * @return a Flow that will emit all [WebRtcEvent]s for the session, or `null` if the
     * subscription could not be started.
     */
    suspend fun startCameraWebRtcSession(entityId: String, offerSdp: String): Flow<WebRtcEvent>?

    /**
     * Send a local ICE candidate for a WebRTC session previously started with
     * [startCameraWebRtcSession].
     *
     * @param sessionId the session identifier received in [WebRtcEvent.Session]
     * @return `true` if the server accepted the candidate
     */
    suspend fun sendCameraWebRtcCandidate(entityId: String, sessionId: String, candidate: WebRtcCandidate): Boolean
}

internal class WebSocketRepositoryFactory @Inject internal constructor(
    private val coreFactory: WebSocketCoreFactory,
    // Use a Provider to avoid a dependency circle since serverManager needs the factory
    private val serverManagerProvider: Provider<ServerManager>,
) {

    suspend fun create(serverId: Int): WebSocketRepository {
        return WebSocketRepositoryImpl(coreFactory.create(serverId), serverManagerProvider.get())
    }
}
