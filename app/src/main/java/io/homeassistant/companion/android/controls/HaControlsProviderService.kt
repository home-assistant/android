package io.homeassistant.companion.android.controls

import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.actions.ControlAction
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
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
import retrofit2.HttpException
import java.util.Calendar
import java.util.concurrent.Flow
import java.util.function.Consumer
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.R)
@AndroidEntryPoint
class HaControlsProviderService : ControlsProviderService() {

    companion object {
        private const val TAG = "HaConProService"
    }

    @Inject
    lateinit var integrationRepository: IntegrationRepository

    @Inject
    lateinit var webSocketRepository: WebSocketRepository

    @Inject
    lateinit var urlRepository: UrlRepository

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

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

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        return Flow.Publisher { subscriber ->
            ioScope.launch {
                try {
                    val getAreaRegistry = async { webSocketRepository.getAreaRegistry() }
                    val getDeviceRegistry = async { webSocketRepository.getDeviceRegistry() }
                    val getEntityRegistry = async { webSocketRepository.getEntityRegistry() }
                    val getEntities = async { integrationRepository.getEntities() }

                    val areaRegistry = getAreaRegistry.await()
                    val deviceRegistry = getDeviceRegistry.await()
                    val entityRegistry = getEntityRegistry.await()
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
                    Log.d(TAG, "request $n")
                    ioScope.launch {
                        // Load up initial values
                        // This should use the cached values that we should store in the DB.
                        // For now we'll use the rest API
                        val getAreaRegistry = async { webSocketRepository.getAreaRegistry() }
                        val getDeviceRegistry = async { webSocketRepository.getDeviceRegistry() }
                        val getEntityRegistry = async { webSocketRepository.getEntityRegistry() }
                        val entities = mutableMapOf<String, Entity<Map<String, Any>>>()
                        controlIds.forEach {
                            try {
                                val entity = integrationRepository.getEntity(it)
                                if (entity != null) {
                                    entities[it] = entity
                                } else {
                                    Log.e(TAG, "Unable to get $it from Home Assistant, null response.")
                                }
                            } catch (e: Exception) {
                                entities["ha_failed.$it"] = getFailedEntity(it, e)
                                Log.e(TAG, "Unable to get $it from Home Assistant, caught exception.", e)
                            }
                        }
                        var areaRegistry = getAreaRegistry.await()
                        var deviceRegistry = getDeviceRegistry.await()
                        var entityRegistry = getEntityRegistry.await()

                        val baseUrl = urlRepository.getUrl().toString().removeSuffix("/")

                        sendEntitiesToSubscriber(subscriber, entities, areaRegistry, deviceRegistry, entityRegistry, baseUrl)

                        // Listen for the state changed events.
                        webSocketScope.launch {
                            integrationRepository.getEntityUpdates()?.collect {
                                if (controlIds.contains(it.entityId)) {
                                    val control = domainToHaControl[it.domain]?.createControl(
                                        applicationContext,
                                        it as Entity<Map<String, Any>>,
                                        RegistriesDataHandler.getAreaForEntity(it.entityId, areaRegistry, deviceRegistry, entityRegistry),
                                        baseUrl
                                    )
                                    if (control != null)
                                        subscriber.onNext(control)
                                }
                            }
                        }
                        webSocketScope.launch {
                            webSocketRepository.getAreaRegistryUpdates()?.collect {
                                areaRegistry = webSocketRepository.getAreaRegistry()
                                sendEntitiesToSubscriber(subscriber, entities, areaRegistry, deviceRegistry, entityRegistry, baseUrl)
                            }
                        }
                        webSocketScope.launch {
                            webSocketRepository.getDeviceRegistryUpdates()?.collect {
                                deviceRegistry = webSocketRepository.getDeviceRegistry()
                                sendEntitiesToSubscriber(subscriber, entities, areaRegistry, deviceRegistry, entityRegistry, baseUrl)
                            }
                        }
                        webSocketScope.launch {
                            webSocketRepository.getEntityRegistryUpdates()?.collect { event ->
                                if (event.action == "update" && controlIds.contains(event.entityId)) {
                                    entityRegistry = webSocketRepository.getEntityRegistry()
                                    sendEntitiesToSubscriber(subscriber, entities, areaRegistry, deviceRegistry, entityRegistry, baseUrl)
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
                    actionSuccess = haControl.performAction(integrationRepository, action)
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

    private fun sendEntitiesToSubscriber(
        subscriber: Flow.Subscriber<in Control>,
        entities: Map<String, Entity<Map<String, Any>>>,
        areaRegistry: List<AreaRegistryResponse>?,
        deviceRegistry: List<DeviceRegistryResponse>?,
        entityRegistry: List<EntityRegistryResponse>?,
        baseUrl: String
    ) {
        entities.forEach {
            val control = try {
                domainToHaControl[it.key.split(".")[0]]?.createControl(
                    applicationContext,
                    it.value,
                    RegistriesDataHandler.getAreaForEntity(it.value.entityId, areaRegistry, deviceRegistry, entityRegistry),
                    baseUrl
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unable to create control for ${it.value.domain} entity, sending error entity", e)
                domainToHaControl["ha_failed"]?.createControl(
                    applicationContext,
                    getFailedEntity(it.value.entityId, e),
                    RegistriesDataHandler.getAreaForEntity(it.value.entityId, areaRegistry, deviceRegistry, entityRegistry),
                    baseUrl
                )
            }
            if (control != null)
                subscriber.onNext(control)
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
}
