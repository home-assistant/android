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
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.util.RegistriesDataHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private val domainToHaControl = mapOf(
        "automation" to DefaultSwitchControl,
        "camera" to null,
        "button" to DefaultButtonControl,
        "climate" to ClimateControl,
        "cover" to CoverControl,
        "fan" to FanControl,
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

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        return Flow.Publisher { subscriber ->
            ioScope.launch {
                try {
                    val areaRegistry = webSocketRepository.getAreaRegistry()
                    val deviceRegistry = webSocketRepository.getDeviceRegistry()
                    val entityRegistry = webSocketRepository.getEntityRegistry()

                    val entities = integrationRepository.getEntities()
                    val areaForEntity = mutableMapOf<String, AreaRegistryResponse?>()
                    entities?.forEach {
                        areaForEntity[it.entityId] = RegistriesDataHandler.getAreaForEntity(it.entityId, areaRegistry, deviceRegistry, entityRegistry)
                    }

                    entities
                        ?.sortedWith(compareBy(nullsLast()) { areaForEntity[it.entityId]?.name })
                        ?.mapNotNull {
                            val domain = it.entityId.split(".")[0]
                            domainToHaControl[domain]?.createControl(
                                applicationContext,
                                it as Entity<Map<String, Any>>,
                                areaForEntity[it.entityId]
                            )
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
                        val entityFlow = integrationRepository.getEntityUpdates()
                        val areaRegistryFlow = webSocketRepository.getAreaRegistryUpdates()
                        val deviceRegistryFlow = webSocketRepository.getDeviceRegistryUpdates()
                        val entityRegistryFlow = webSocketRepository.getEntityRegistryUpdates()
                        // Load up initial values
                        // This should use the cached values that we should store in the DB.
                        // For now we'll use the rest API
                        var areaRegistry = webSocketRepository.getAreaRegistry()
                        var deviceRegistry = webSocketRepository.getDeviceRegistry()
                        var entityRegistry = webSocketRepository.getEntityRegistry()
                        val entities = mutableMapOf<String, Entity<Map<String, Any>>>()
                        controlIds.forEach {
                            val entity = integrationRepository.getEntity(it)
                            if (entity != null) {
                                entities[it] = entity
                            } else {
                                Log.e(TAG, "Unable to get $it from Home Assistant.")
                            }
                        }
                        sendEntitiesToSubscriber(subscriber, entities, areaRegistry, deviceRegistry, entityRegistry)

                        // Listen for the state changed events.
                        webSocketScope.launch {
                            entityFlow?.collect {
                                if (controlIds.contains(it.entityId)) {
                                    val domain = it.entityId.split(".")[0]
                                    val control = domainToHaControl[domain]?.createControl(
                                        applicationContext,
                                        it as Entity<Map<String, Any>>,
                                        RegistriesDataHandler.getAreaForEntity(it.entityId, areaRegistry, deviceRegistry, entityRegistry)
                                    )
                                    subscriber.onNext(control)
                                }
                            }
                        }
                        webSocketScope.launch {
                            areaRegistryFlow?.collect {
                                areaRegistry = webSocketRepository.getAreaRegistry()
                                sendEntitiesToSubscriber(subscriber, entities, areaRegistry, deviceRegistry, entityRegistry)
                            }
                        }
                        webSocketScope.launch {
                            deviceRegistryFlow?.collect {
                                deviceRegistry = webSocketRepository.getDeviceRegistry()
                                sendEntitiesToSubscriber(subscriber, entities, areaRegistry, deviceRegistry, entityRegistry)
                            }
                        }
                        webSocketScope.launch {
                            entityRegistryFlow?.collect { event ->
                                if (event.action == "update" && controlIds.contains(event.entityId)) {
                                    entityRegistry = webSocketRepository.getEntityRegistry()
                                    sendEntitiesToSubscriber(subscriber, entities, areaRegistry, deviceRegistry, entityRegistry)
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

        var actionSuccess = false
        if (haControl != null) {
            try {
                actionSuccess = haControl.performAction(integrationRepository, action)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to control or get entity information", e)
            }
        }
        if (actionSuccess) {
            consumer.accept(ControlAction.RESPONSE_OK)
        } else {
            consumer.accept(ControlAction.RESPONSE_UNKNOWN)
        }
    }

    private fun sendEntitiesToSubscriber(
        subscriber: Flow.Subscriber<in Control>,
        entities: Map<String, Entity<Map<String, Any>>>,
        areaRegistry: List<AreaRegistryResponse>?,
        deviceRegistry: List<DeviceRegistryResponse>?,
        entityRegistry: List<EntityRegistryResponse>?
    ) {
        entities.forEach {
            val domain = it.key.split(".")[0]
            val control = domainToHaControl[domain]?.createControl(
                applicationContext,
                it.value,
                RegistriesDataHandler.getAreaForEntity(it.key, areaRegistry, deviceRegistry, entityRegistry)
            )
            subscriber.onNext(control)
        }
    }
}
