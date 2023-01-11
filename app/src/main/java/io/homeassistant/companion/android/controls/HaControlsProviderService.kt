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

    private var areaRegistry: List<AreaRegistryResponse>? = null
    private var deviceRegistry: List<DeviceRegistryResponse>? = null
    private var entityRegistry: List<EntityRegistryResponse>? = null

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        return Flow.Publisher { subscriber ->
            ioScope.launch {
                if (!serverManager.isRegistered()) return@launch subscriber.onComplete()

                try {
                    val getAreaRegistry = async { serverManager.webSocketRepository().getAreaRegistry() }
                    val getDeviceRegistry = async { serverManager.webSocketRepository().getDeviceRegistry() }
                    val getEntityRegistry = async { serverManager.webSocketRepository().getEntityRegistry() }
                    val getEntities = async { serverManager.integrationRepository().getEntities() }

                    areaRegistry = getAreaRegistry.await()
                    deviceRegistry = getDeviceRegistry.await()
                    entityRegistry = getEntityRegistry.await()
                    val entities = getEntities.await()

                    val areaForEntity = entities.orEmpty().associate {
                        it.entityId to RegistriesDataHandler.getAreaForEntity(it.entityId, areaRegistry, deviceRegistry, entityRegistry)
                    }

                    entities
                        ?.sortedWith(compareBy(nullsLast()) { areaForEntity[it.entityId]?.name })
                        ?.filter {
                            domainToMinimumApi[it.domain] == null ||
                                Build.VERSION.SDK_INT >= domainToMinimumApi[it.domain]!!
                        }
                        ?.mapNotNull {
                            try {
                                domainToHaControl[it.domain]?.createControl(
                                    applicationContext,
                                    it as Entity<Map<String, Any>>,
                                    areaForEntity[it.entityId],
                                    false, // Auth not required for preview
                                    null // Prevent downloading camera images
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Unable to create control for ${it.domain} entity, skipping", e)
                                null
                            }
                        }
                        ?.forEach {
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
                        // Load up initial values
                        // This should use the cached values that we should store in the DB.
                        // For now we'll use the rest API
                        val getAreaRegistry = async { serverManager.webSocketRepository().getAreaRegistry() }
                        val getDeviceRegistry = async { serverManager.webSocketRepository().getDeviceRegistry() }
                        val getEntityRegistry = async { serverManager.webSocketRepository().getEntityRegistry() }
                        val entities = mutableMapOf<String, Entity<Map<String, Any>>>()
                        val baseUrl = serverManager.getServer()?.connection?.getUrl()?.toString()?.removeSuffix("/") ?: ""

                        areaRegistry = getAreaRegistry.await()
                        deviceRegistry = getDeviceRegistry.await()
                        entityRegistry = getEntityRegistry.await()

                        if (serverManager.integrationRepository().isHomeAssistantVersionAtLeast(2022, 4, 0)) {
                            webSocketScope.launch {
                                var sentInitial = false
                                val error404 = HttpException(Response.error<ResponseBody>(404, byteArrayOf().toResponseBody()))

                                serverManager.webSocketRepository().getCompressedStateAndChanges(controlIds.toList())
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
                                            (controlIds - entities.keys).forEach { missingEntity ->
                                                Log.e(TAG, "Unable to get $missingEntity from Home Assistant, not returned in subscribe_entities.")
                                                val entity = getFailedEntity(missingEntity, error404)
                                                entities["ha_failed.$missingEntity"] = entity
                                                toSend["ha_failed.$missingEntity"] = entity
                                            }
                                        }
                                        Log.d(TAG, "Sending ${toSend.size} entities to subscriber")
                                        sendEntitiesToSubscriber(subscriber, toSend, webSocketScope, baseUrl)
                                    } ?: run {
                                    controlIds.forEach {
                                        val entity = getFailedEntity(it, Exception())
                                        entities["ha_failed.$it"] = entity
                                        domainToHaControl["ha_failed"]?.createControl(
                                            applicationContext,
                                            entity,
                                            RegistriesDataHandler.getAreaForEntity(entity.entityId, areaRegistry, deviceRegistry, entityRegistry),
                                            entityRequiresAuth(entity.entityId),
                                            baseUrl
                                        )?.let { control -> subscriber.onNext(control) }
                                    }
                                }
                            }
                        } else {
                            // Set up initial states
                            controlIds.forEach {
                                launch { // using launch to create controls async
                                    var id = it
                                    try {
                                        val entity = serverManager.integrationRepository().getEntity(it)
                                        if (entity != null) {
                                            entities[it] = entity
                                        } else {
                                            Log.e(TAG, "Unable to get $it from Home Assistant, null response.")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Unable to get $it from Home Assistant, caught exception.", e)
                                        entities["ha_failed.$it"] = getFailedEntity(it, e)
                                        id = "ha_failed.$it"
                                    }
                                    entities[id]?.let {
                                        domainToHaControl[id.split(".")[0]]?.createControl(
                                            applicationContext,
                                            it,
                                            RegistriesDataHandler.getAreaForEntity(it.entityId, areaRegistry, deviceRegistry, entityRegistry),
                                            entityRequiresAuth(it.entityId),
                                            baseUrl
                                        )?.let { control -> subscriber.onNext(control) }
                                    }
                                }
                            }

                            // Listen for the state changed events.
                            webSocketScope.launch {
                                serverManager.integrationRepository().getEntityUpdates(controlIds)?.collect {
                                    val control = domainToHaControl[it.domain]?.createControl(
                                        applicationContext,
                                        it as Entity<Map<String, Any>>,
                                        RegistriesDataHandler.getAreaForEntity(it.entityId, areaRegistry, deviceRegistry, entityRegistry),
                                        entityRequiresAuth(it.entityId),
                                        baseUrl
                                    )
                                    if (control != null)
                                        subscriber.onNext(control)
                                }
                            }
                        }
                        webSocketScope.launch {
                            serverManager.webSocketRepository().getAreaRegistryUpdates()?.collect {
                                areaRegistry = serverManager.webSocketRepository().getAreaRegistry()
                                sendEntitiesToSubscriber(subscriber, entities, webSocketScope, baseUrl)
                            }
                        }
                        webSocketScope.launch {
                            serverManager.webSocketRepository().getDeviceRegistryUpdates()?.collect {
                                deviceRegistry = serverManager.webSocketRepository().getDeviceRegistry()
                                sendEntitiesToSubscriber(subscriber, entities, webSocketScope, baseUrl)
                            }
                        }
                        webSocketScope.launch {
                            serverManager.webSocketRepository().getEntityRegistryUpdates()?.collect { event ->
                                if (event.action == "update" && controlIds.contains(event.entityId)) {
                                    entityRegistry = serverManager.webSocketRepository().getEntityRegistry()
                                    sendEntitiesToSubscriber(subscriber, entities, webSocketScope, baseUrl)
                                }
                            }
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
        val domain = controlId.split(".")[0]
        val haControl = domainToHaControl[domain]

        ioScope.launch {
            var actionSuccess = false
            if (haControl != null) {
                try {
                    actionSuccess = haControl.performAction(serverManager.integrationRepository(), action)
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

    private suspend fun sendEntitiesToSubscriber(
        subscriber: Flow.Subscriber<in Control>,
        entities: Map<String, Entity<Map<String, Any>>>,
        coroutineScope: CoroutineScope,
        baseUrl: String
    ) {
        entities.forEach {
            coroutineScope.launch {
                val control = try {
                    domainToHaControl[it.key.split(".")[0]]?.createControl(
                        applicationContext,
                        it.value,
                        RegistriesDataHandler.getAreaForEntity(it.value.entityId, areaRegistry, deviceRegistry, entityRegistry),
                        entityRequiresAuth(it.value.entityId),
                        baseUrl
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to create control for ${it.value.domain} entity, sending error entity", e)
                    domainToHaControl["ha_failed"]?.createControl(
                        applicationContext,
                        getFailedEntity(it.value.entityId, e),
                        RegistriesDataHandler.getAreaForEntity(it.value.entityId, areaRegistry, deviceRegistry, entityRegistry),
                        entityRequiresAuth(it.value.entityId),
                        baseUrl
                    )
                }
                if (control != null)
                    subscriber.onNext(control)
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

    private suspend fun entityRequiresAuth(entityId: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val setting = prefsRepository.getControlsAuthRequired()
            if (setting == ControlsAuthRequiredSetting.SELECTION) {
                val includeList = prefsRepository.getControlsAuthEntities()
                includeList.contains(entityId)
            } else {
                setting == ControlsAuthRequiredSetting.ALL
            }
        } else false
    }
}
