package io.homeassistant.companion.android.controls

import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.actions.ControlAction
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.applyCompressedStateDiff
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.util.RegistriesDataHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.util.Calendar
import java.util.concurrent.Flow
import java.util.function.Consumer
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.R)
@AndroidEntryPoint
class HaControlsProviderService : ControlsProviderService() {

    companion object {
        private const val TAG = "HaConProService"

        private val domainToHaControl = mapOf(
            "automation" to DefaultSwitchControl,
            "button" to DefaultButtonControl,
            "camera" to CameraControl,
            "climate" to ClimateControl,
            "cover" to CoverControl,
            "fan" to FanControl,
            "ha_failed" to HaFailedControl,
            "input_boolean" to DefaultSwitchControl,
            "input_button" to DefaultButtonControl,
            "input_number" to DefaultSliderControl,
            "light" to LightControl,
            "lock" to LockControl,
            "media_player" to null,
            "remote" to null,
            "scene" to DefaultButtonControl,
            "script" to DefaultButtonControl,
            "switch" to DefaultSwitchControl,
            "vacuum" to VacuumControl
        )
        private val domainToMinimumApi = mapOf(
            "camera" to Build.VERSION_CODES.S
        )

        fun getSupportedDomains(): List<String> =
            domainToHaControl
                .filter { it.value != null }
                .map { it.key }
                .filter {
                    domainToMinimumApi[it] == null ||
                        Build.VERSION.SDK_INT >= domainToMinimumApi[it]!!
                }
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var prefsRepository: PrefsRepository

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private var areaRegistry = mutableMapOf<Int, List<AreaRegistryResponse>?>()
    private var deviceRegistry = mutableMapOf<Int, List<DeviceRegistryResponse>?>()
    private var entityRegistry = mutableMapOf<Int, List<EntityRegistryResponse>?>()

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        return Flow.Publisher { subscriber ->
            ioScope.launch {
                if (!serverManager.isRegistered()) {
                    subscriber.onComplete()
                    return@launch
                }

                try {
                    val entities = mutableMapOf<Int, List<Entity<Any>>?>()
                    val areaForEntity = mutableMapOf<Int, Map<String, AreaRegistryResponse?>>()
                    serverManager.defaultServers.map { server ->
                        async {
                            val getAreaRegistry = async { serverManager.webSocketRepository(server.id).getAreaRegistry() }
                            val getDeviceRegistry = async { serverManager.webSocketRepository(server.id).getDeviceRegistry() }
                            val getEntityRegistry = async { serverManager.webSocketRepository(server.id).getEntityRegistry() }
                            val getEntities = async { serverManager.integrationRepository(server.id).getEntities() }

                            areaRegistry[server.id] = getAreaRegistry.await()
                            deviceRegistry[server.id] = getDeviceRegistry.await()
                            entityRegistry[server.id] = getEntityRegistry.await()
                            entities[server.id] = getEntities.await()

                            areaForEntity[server.id] = entities[server.id].orEmpty().associate {
                                it.entityId to RegistriesDataHandler.getAreaForEntity(
                                    it.entityId,
                                    areaRegistry[server.id],
                                    deviceRegistry[server.id],
                                    entityRegistry[server.id]
                                )
                            }
                            entities[server.id] = entities[server.id].orEmpty()
                                .sortedWith(compareBy(nullsLast()) { areaForEntity[server.id]?.get(it.entityId)?.name })
                        }
                    }.awaitAll()

                    val allEntities = mutableListOf<Pair<Int, Entity<Any>>>()
                    entities.forEach { serverEntities ->
                        serverEntities.value?.forEach { allEntities += Pair(serverEntities.key, it) }
                    }
                    val serverNames = mutableMapOf<Int, String>()
                    if (serverManager.defaultServers.size > 1) {
                        serverManager.defaultServers.forEach { serverNames[it.id] = it.friendlyName }
                    }
                    allEntities
                        .filter {
                            domainToMinimumApi[it.second.domain] == null ||
                                Build.VERSION.SDK_INT >= domainToMinimumApi[it.second.domain]!!
                        }
                        .mapNotNull { (serverId, entity) ->
                            try {
                                val info = HaControlInfo(
                                    systemId = "$serverId.${entity.entityId}",
                                    entityId = entity.entityId,
                                    serverId = serverId,
                                    serverName = serverNames[serverId],
                                    area = getAreaForEntity(entity.entityId, serverId)
                                ) // No auth for preview, no base url to prevent downloading images
                                domainToHaControl[entity.domain]?.createControl(
                                    applicationContext,
                                    entity as Entity<Map<String, Any>>,
                                    info
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Unable to create control for ${entity.domain} entity, skipping", e)
                                null
                            }
                        }
                        .forEach {
                            subscriber.onNext(it)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting list of entities", e)
                }
                subscriber.onComplete()
            }
        }
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        Log.d(TAG, "publisherFor $controlIds")
        return Flow.Publisher { subscriber ->
            subscriber.onSubscribe(object : Flow.Subscription {
                val webSocketScope = CoroutineScope(Dispatchers.IO)
                override fun request(n: Long) {
                    if (!serverManager.isRegistered()) return else Log.d(TAG, "request $n")

                    ioScope.launch {
                        controlIds
                            .groupBy {
                                // Controls added before multiserver don't have a server ID, assume the first
                                it.split(".")[0].toIntOrNull()
                                    ?: serverManager.defaultServers.firstOrNull()?.id
                            }.forEach { (serverId, serverControlIds) ->
                                if (serverId == null) return@forEach
                                subscribeToEntitiesForServer(
                                    serverId,
                                    serverControlIds,
                                    webSocketScope,
                                    subscriber
                                )
                            }
                    }
                }

                override fun cancel() {
                    Log.d(TAG, "cancel")
                    webSocketScope.cancel()
                }
            })
        }
    }

    override fun performControlAction(
        controlId: String,
        action: ControlAction,
        consumer: Consumer<Int>
    ) {
        Log.d(TAG, "Control: $controlId, action: $action")
        if (!serverManager.isRegistered()) return consumer.accept(ControlAction.RESPONSE_FAIL)

        var server = 0
        var domain = ""
        controlId.split(".")[0].toIntOrNull()?.let {
            server = it
            domain = controlId.split(".")[1]
        } ?: run {
            server = serverManager.defaultServers.firstOrNull()!!.id
            domain = controlId.split(".")[0]
        }
        val haControl = domainToHaControl[domain]

        ioScope.launch {
            var actionSuccess = false
            if (haControl != null) {
                try {
                    actionSuccess = haControl.performAction(serverManager.integrationRepository(server), action)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to control or get entity information", e)
                }
            }

            withContext(Dispatchers.Main) {
                if (actionSuccess) {
                    consumer.accept(ControlAction.RESPONSE_OK)
                } else {
                    consumer.accept(ControlAction.RESPONSE_UNKNOWN)
                }
            }
        }
    }

    private suspend fun subscribeToEntitiesForServer(
        serverId: Int,
        controlIds: List<String>,
        webSocketScope: CoroutineScope,
        subscriber: Flow.Subscriber<in Control>
    ) {
        if (serverManager.getServer(serverId) == null) {
            controlIds.forEach {
                val entityId =
                    if (it.split(".")[0].toIntOrNull() != null) {
                        it.removePrefix("$serverId.")
                    } else {
                        it
                    }
                val entity = getFailedEntity(entityId, Exception())
                domainToHaControl["ha_failed"]?.createControl(
                    applicationContext,
                    entity,
                    HaControlInfo(
                        systemId = it,
                        entityId = entityId,
                        serverId = serverId,
                        area = getAreaForEntity(entity.entityId, serverId)
                    )
                )?.let { control -> subscriber.onNext(control) }
            }
            return
        }

        // Load up initial values
        val getAreaRegistry = ioScope.async { serverManager.webSocketRepository(serverId).getAreaRegistry() }
        val getDeviceRegistry = ioScope.async { serverManager.webSocketRepository(serverId).getDeviceRegistry() }
        val getEntityRegistry = ioScope.async { serverManager.webSocketRepository(serverId).getEntityRegistry() }
        val entityIds = controlIds.map {
            if (it.split(".")[0].toIntOrNull() != null) {
                it.removePrefix("$serverId.")
            } else {
                it
            }
        }
        val entities = mutableMapOf<String, Entity<Map<String, Any>>>()
        val baseUrl = serverManager.getServer(serverId)?.connection?.getUrl()?.toString()?.removeSuffix("/") ?: ""

        areaRegistry[serverId] = getAreaRegistry.await()
        deviceRegistry[serverId] = getDeviceRegistry.await()
        entityRegistry[serverId] = getEntityRegistry.await()

        if (serverManager.integrationRepository(serverId).isHomeAssistantVersionAtLeast(2022, 4, 0)) {
            webSocketScope.launch {
                var sentInitial = false
                val error404 = HttpException(Response.error<ResponseBody>(404, byteArrayOf().toResponseBody()))

                serverManager.webSocketRepository(serverId).getCompressedStateAndChanges(entityIds)
                    ?.collect { event ->
                        val toSend = mutableMapOf<String, Entity<Map<String, Any>>>()
                        event.added?.forEach {
                            val entity = it.value.toEntity(it.key)
                            entities.remove("ha_failed.$it")
                            entities[it.key] = entity
                            toSend[it.key] = entity
                        }
                        event.changed?.forEach {
                            val entity = entities[it.key]?.applyCompressedStateDiff(it.value)
                            entity?.let { thisEntity ->
                                entities[it.key] = thisEntity
                                toSend[it.key] = entity
                            }
                        }
                        event.removed?.forEach {
                            entities.remove(it)
                            val entity = getFailedEntity(it, error404)
                            entities["ha_failed.$it"] = entity
                            toSend["ha_failed.$it"] = entity
                        }
                        if (!sentInitial) {
                            // All initial states will be in the first message
                            sentInitial = true
                            (entityIds - entities.keys).forEach { missingEntity ->
                                Log.e(TAG, "Unable to get $missingEntity from Home Assistant, not returned in subscribe_entities.")
                                val entity = getFailedEntity(missingEntity, error404)
                                entities["ha_failed.$missingEntity"] = entity
                                toSend["ha_failed.$missingEntity"] = entity
                            }
                        }
                        Log.d(TAG, "Sending ${toSend.size} entities to subscriber")
                        sendEntitiesToSubscriber(subscriber, controlIds, toSend, serverId, webSocketScope, baseUrl)
                    } ?: run {
                    entityIds.forEachIndexed { index, entityId ->
                        val entity = getFailedEntity(entityId, Exception())
                        entities["ha_failed.$entityId"] = entity
                        domainToHaControl["ha_failed"]?.createControl(
                            applicationContext,
                            entity,
                            HaControlInfo(
                                systemId = controlIds[index],
                                entityId = entity.entityId,
                                serverId = serverId,
                                area = getAreaForEntity(entity.entityId, serverId),
                                authRequired = entityRequiresAuth(entity.entityId, serverId),
                                baseUrl = baseUrl
                            )
                        )?.let { control -> subscriber.onNext(control) }
                    }
                }
            }
        } else {
            // Set up initial states
            entityIds.forEachIndexed { index, entityId ->
                webSocketScope.launch { // using launch to create controls async
                    var id = entityId
                    try {
                        val entity = serverManager.integrationRepository(serverId).getEntity(entityId)
                        if (entity != null) {
                            entities[entityId] = entity
                        } else {
                            Log.e(TAG, "Unable to get $entityId from Home Assistant, null response.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to get $entityId from Home Assistant, caught exception.", e)
                        entities["ha_failed.$entityId"] = getFailedEntity(entityId, e)
                        id = "ha_failed.$entityId"
                    }
                    entities[id]?.let { entity ->
                        domainToHaControl[id.split(".")[0]]?.createControl(
                            applicationContext,
                            entity,
                            HaControlInfo(
                                systemId = controlIds[index],
                                entityId = entity.entityId,
                                serverId = serverId,
                                area = getAreaForEntity(entity.entityId, serverId),
                                authRequired = entityRequiresAuth(entity.entityId, serverId),
                                baseUrl = baseUrl
                            )
                        )?.let { control -> subscriber.onNext(control) }
                    }
                }
            }

            // Listen for the state changed events.
            webSocketScope.launch {
                serverManager.integrationRepository(serverId).getEntityUpdates(entityIds)?.collect {
                    val control = domainToHaControl[it.domain]?.createControl(
                        applicationContext,
                        it as Entity<Map<String, Any>>,
                        HaControlInfo(
                            systemId = controlIds[entityIds.indexOf(it.entityId)],
                            entityId = it.entityId,
                            serverId = serverId,
                            area = getAreaForEntity(it.entityId, serverId),
                            authRequired = entityRequiresAuth(it.entityId, serverId),
                            baseUrl = baseUrl
                        )
                    )
                    if (control != null) {
                        subscriber.onNext(control)
                    }
                }
            }
        }
        webSocketScope.launch {
            serverManager.webSocketRepository(serverId).getAreaRegistryUpdates()?.collect {
                areaRegistry[serverId] = serverManager.webSocketRepository(serverId).getAreaRegistry()
                sendEntitiesToSubscriber(subscriber, controlIds, entities, serverId, webSocketScope, baseUrl)
            }
        }
        webSocketScope.launch {
            serverManager.webSocketRepository(serverId).getDeviceRegistryUpdates()?.collect {
                deviceRegistry[serverId] = serverManager.webSocketRepository(serverId).getDeviceRegistry()
                sendEntitiesToSubscriber(subscriber, controlIds, entities, serverId, webSocketScope, baseUrl)
            }
        }
        webSocketScope.launch {
            serverManager.webSocketRepository(serverId).getEntityRegistryUpdates()?.collect { event ->
                if (event.action == "update" && entityIds.contains(event.entityId)) {
                    entityRegistry[serverId] = serverManager.webSocketRepository(serverId).getEntityRegistry()
                    sendEntitiesToSubscriber(subscriber, controlIds, entities, serverId, webSocketScope, baseUrl)
                }
            }
        }
    }

    private suspend fun sendEntitiesToSubscriber(
        subscriber: Flow.Subscriber<in Control>,
        controlIds: List<String>,
        entities: Map<String, Entity<Map<String, Any>>>,
        serverId: Int,
        coroutineScope: CoroutineScope,
        baseUrl: String
    ) {
        val entityIds = controlIds.map {
            if (it.split(".")[0].toIntOrNull() != null) {
                it.removePrefix("$serverId.")
            } else {
                it
            }
        }
        entities.forEach {
            coroutineScope.launch {
                val info = HaControlInfo(
                    systemId = controlIds[entityIds.indexOf(it.value.entityId)],
                    entityId = it.value.entityId,
                    serverId = serverId,
                    area = getAreaForEntity(it.value.entityId, serverId),
                    authRequired = entityRequiresAuth(it.value.entityId, serverId),
                    baseUrl = baseUrl
                )
                val control = try {
                    domainToHaControl[it.key.split(".")[0]]?.createControl(
                        applicationContext,
                        it.value,
                        info
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to create control for ${it.value.domain} entity, sending error entity", e)
                    domainToHaControl["ha_failed"]?.createControl(
                        applicationContext,
                        getFailedEntity(it.value.entityId, e),
                        info
                    )
                }
                if (control != null) {
                    subscriber.onNext(control)
                }
            }
        }
    }

    private fun getFailedEntity(
        entityId: String,
        exception: Exception
    ): Entity<Map<String, Any>> {
        return Entity(
            entityId = entityId,
            state = if (exception is HttpException && exception.code() == 404) "notfound" else "exception",
            attributes = mapOf<String, String>(),
            lastChanged = Calendar.getInstance(),
            lastUpdated = Calendar.getInstance(),
            context = null
        )
    }

    private fun getAreaForEntity(entityId: String, serverId: Int) = RegistriesDataHandler.getAreaForEntity(
        entityId,
        areaRegistry[serverId],
        deviceRegistry[serverId],
        entityRegistry[serverId]
    )

    private suspend fun entityRequiresAuth(entityId: String, serverId: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val setting = prefsRepository.getControlsAuthRequired()
            if (setting == ControlsAuthRequiredSetting.SELECTION) {
                val includeList = prefsRepository.getControlsAuthEntities()
                includeList.contains("$serverId.$entityId")
            } else {
                setting == ControlsAuthRequiredSetting.ALL
            }
        } else {
            false
        }
    }
}
