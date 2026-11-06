package io.homeassistant.companion.android.common.data.integration

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationRepositoryImpl
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationService
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.di.qualifiers.NamedDeviceId
import io.homeassistant.companion.android.di.qualifiers.NamedIntegrationStorage
import io.homeassistant.companion.android.di.qualifiers.NamedManufacturer
import io.homeassistant.companion.android.di.qualifiers.NamedModel
import io.homeassistant.companion.android.di.qualifiers.NamedOsVersion
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow

interface IntegrationRepository {

    suspend fun registerDevice(deviceRegistration: DeviceRegistration)
    suspend fun updateRegistration(deviceRegistration: DeviceRegistration, allowReregistration: Boolean = true)
    suspend fun deletePreferences()

    suspend fun getNotificationRateLimits(): RateLimitResponse

    suspend fun renderTemplate(template: String, variables: Map<String, String>): String?
    suspend fun getTemplateUpdates(template: String): Flow<String?>?

    suspend fun updateLocation(updateLocation: UpdateLocation)

    suspend fun getZones(): List<Entity>

    suspend fun isAppLocked(): Boolean
    suspend fun setAppActive(active: Boolean)

    suspend fun sessionTimeOut(value: Int)
    suspend fun getSessionTimeOut(): Int

    suspend fun getHomeAssistantVersion(): String
    suspend fun isHomeAssistantVersionAtLeast(year: Int, month: Int, release: Int): Boolean

    suspend fun getConfig(): GetConfigResponse
    suspend fun getServices(): List<Action>?

    suspend fun getEntities(): List<Entity>?
    suspend fun getEntity(entityId: String): Entity?

    /**
     * Fetches the current state of [entityIds] in a single request, in the requested order and
     * omitting the ones the server doesn't know, without downloading every state of the server
     * when it supports filtered subscriptions (2022.4). Returns null when they can't be retrieved.
     */
    suspend fun getEntities(entityIds: List<String>): List<Entity>?

    /**
     * Follows [entityIds] on a single subscription: the first emission holds their current states,
     * omitting the ones the server doesn't know, and each next one the entities whose state changed.
     * The states are downloaded once, and no change can be missed between the two. Returns null when
     * the server doesn't support filtered subscriptions (2022.4) or the subscription can't be made.
     */
    suspend fun getEntitiesAndUpdates(entityIds: List<String>): Flow<List<Entity>>?
    suspend fun getEntityUpdates(): Flow<Entity>?
    suspend fun getEntityUpdates(entityIds: List<String>): Flow<Entity>?

    suspend fun callAction(domain: String, action: String, actionData: Map<String, Any?>)

    suspend fun scanTag(data: Map<String, String>)

    suspend fun fireEvent(eventType: String, eventData: Map<String, Any>)

    suspend fun registerSensor(sensorRegistration: SensorRegistration<Any>)
    suspend fun updateSensors(sensors: List<SensorRegistration<Any>>): Boolean

    suspend fun isTrusted(): Boolean

    suspend fun setTrusted(trusted: Boolean)

    suspend fun shouldNotifySecurityWarning(): Boolean

    suspend fun getAssistResponse(
        text: String,
        pipelineId: String? = null,
        conversationId: String? = null,
    ): Flow<AssistPipelineEvent>?

    suspend fun getLastUsedPipelineId(): String?

    suspend fun getLastUsedPipelineSttSupport(): Boolean

    suspend fun setLastUsedPipeline(pipelineId: String, supportsStt: Boolean)

    /** @return List of border agent IDs added to this device from the server */
    suspend fun getThreadBorderAgentIds(): List<String>

    /** Set the list of border agent IDs added to this device from the server */
    suspend fun setThreadBorderAgentIds(ids: List<String>)

    /** @return List of border agent IDs added to this device from a server that no longer exists */
    suspend fun getOrphanedThreadBorderAgentIds(): List<String>

    /** Clear the list of orphaned border agent IDs, to use after removing them from storage */
    suspend fun clearOrphanedThreadBorderAgentIds()

    suspend fun shouldAskNotificationPermission(): Boolean?

    suspend fun setAskNotificationPermission(shouldAsk: Boolean)
}

internal class IntegrationRepositoryFactory @Inject constructor(
    private val integrationServiceProvider: SuspendProvider<IntegrationService>,
    // Use a Provider to avoid a dependency circle since serverManager needs the factory
    private val serverManagerProvider: Provider<ServerManager>,
    @NamedIntegrationStorage private val localStorage: LocalStorage,
    @NamedManufacturer private val manufacturer: String,
    @NamedModel private val model: String,
    @NamedOsVersion private val osVersion: String,
    @NamedDeviceId private val deviceId: String,
) {
    suspend fun create(serverId: Int): IntegrationRepositoryImpl {
        return IntegrationRepositoryImpl(
            integrationService = integrationServiceProvider(),
            serverManager = serverManagerProvider.get(),
            serverId = serverId,
            localStorage = localStorage,
            manufacturer = manufacturer,
            model = model,
            osVersion = osVersion,
            deviceId = deviceId,
        )
    }
}
