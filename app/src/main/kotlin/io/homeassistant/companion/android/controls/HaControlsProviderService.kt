package io.homeassistant.companion.android.controls

import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.actions.ControlAction
import androidx.annotation.RequiresApi
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.ControlsAuthRequiredSetting
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CLIMATE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.COVER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.FAN_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.LIGHT_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.integration.display.awaitLoadedOrNull
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
import io.homeassistant.companion.android.common.util.SdkVersion
import java.util.concurrent.Flow
import java.util.function.Consumer
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.R)
@AndroidEntryPoint
class HaControlsProviderService : ControlsProviderService() {

    companion object {
        private val domainToHaControl = mapOf(
            "automation" to DefaultSwitchControl,
            "button" to DefaultButtonControl,
            CAMERA_DOMAIN to CameraControl,
            CLIMATE_DOMAIN to ClimateControl,
            COVER_DOMAIN to CoverControl,
            FAN_DOMAIN to FanControl,
            "ha_failed" to HaFailedControl,
            "humidifier" to DefaultSwitchControl,
            "input_boolean" to DefaultSwitchControl,
            "input_button" to DefaultButtonControl,
            "input_number" to DefaultSliderControl,
            LIGHT_DOMAIN to LightControl,
            "lock" to LockControl,
            MEDIA_PLAYER_DOMAIN to MediaPlayerControl,
            "number" to DefaultSliderControl,
            "remote" to DefaultSwitchControl,
            "scene" to DefaultButtonControl,
            "script" to DefaultButtonControl,
            "siren" to DefaultSwitchControl,
            "switch" to DefaultSwitchControl,
            "vacuum" to VacuumControl,
        )
        private val domainToMinimumApi = mapOf(
            CAMERA_DOMAIN to Build.VERSION_CODES.S,
        )

        fun getSupportedDomains(): List<String> = domainToHaControl.keys.filter(::isDomainSupportedByApi)

        /** Whether the domain's controls are available on this device's API level. */
        private fun isDomainSupportedByApi(domain: String): Boolean {
            val minimumApi = domainToMinimumApi[domain] ?: return true
            return SdkVersion.isAtLeast(minimumApi)
        }
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var prefsRepository: PrefsRepository

    @Inject
    lateinit var entitiesForDisplayManager: EntitiesForDisplayManager

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        return Flow.Publisher { subscriber ->
            ioScope.launch {
                if (!serverManager.isRegistered()) {
                    subscriber.onComplete()
                    return@launch
                }

                val splitServersIntoMultipleStructures = splitMultiServersIntoStructures()
                val servers = serverManager.servers()
                val serverNames = mutableMapOf<Int, String>()
                if (servers.size > 1) {
                    servers.forEach { serverNames[it.id] = it.friendlyName }
                }

                val serverItems = servers.map { server ->
                    async {
                        val items = try {
                            entitiesForDisplayManager.snapshotInContext(server.id)
                                .awaitLoadedOrNull()
                                ?.entities
                                ?.sortedWith(compareBy(nullsLast()) { it.areaName })
                                .orEmpty()
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "Unable to load entities for server ${server.id} (${server.friendlyName}), skipping",
                            )
                            emptyList()
                        }
                        server.id to items
                    }
                }.awaitAll()

                try {
                    serverItems.forEach { (serverId, items) ->
                        items
                            .filter { isDomainSupportedByApi(it.domain) }
                            .mapNotNull { item ->
                                try {
                                    val info = HaControlInfo(
                                        systemId = "$serverId.${item.entityId}",
                                        entityId = item.entityId,
                                        serverId = serverId,
                                        serverName = serverNames[serverId],
                                        splitMultiServerIntoStructure = splitServersIntoMultipleStructures,
                                    ) // No auth for preview, no base url to prevent downloading images
                                    domainToHaControl[item.domain]?.createControl(
                                        applicationContext,
                                        item,
                                        info,
                                    )
                                } catch (e: Exception) {
                                    Timber.e(e, "Unable to create control for ${item.domain} entity, skipping")
                                    null
                                }
                            }
                            .forEach {
                                subscriber.onNext(it)
                            }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error building list of entities")
                }
                subscriber.onComplete()
            }
        }
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        Timber.d("publisherFor $controlIds")
        return Flow.Publisher { subscriber ->
            subscriber.onSubscribe(
                object : Flow.Subscription {
                    val webSocketScope = CoroutineScope(Dispatchers.IO)
                    override fun request(n: Long) {
                        ioScope.launch {
                            if (!serverManager.isRegistered()) return@launch else Timber.d("request $n")

                            controlIds
                                .groupBy {
                                    // Controls added before multiserver don't have a server ID, assume the first
                                    it.split(".")[0].toIntOrNull()
                                        ?: serverManager.servers().firstOrNull()?.id
                                }.forEach { (serverId, serverControlIds) ->
                                    if (serverId == null) return@forEach
                                    subscribeToEntitiesForServer(
                                        serverId,
                                        serverControlIds,
                                        webSocketScope,
                                        subscriber,
                                    )
                                }
                        }
                    }

                    override fun cancel() {
                        Timber.d("cancel")
                        webSocketScope.cancel()
                    }
                },
            )
        }
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        ioScope.launch {
            Timber.d("Control: $controlId, action: $action")
            if (!serverManager.isRegistered()) return@launch consumer.accept(ControlAction.RESPONSE_FAIL)

            var server = 0
            var domain = ""
            controlId.split(".")[0].toIntOrNull()?.let {
                server = it
                domain = controlId.split(".")[1]
            } ?: run {
                server = serverManager.servers().firstOrNull()!!.id
                domain = controlId.split(".")[0]
            }
            val haControl = domainToHaControl[domain]
            var actionSuccess = false
            if (haControl != null) {
                try {
                    actionSuccess =
                        haControl.performAction(serverManager.integrationRepository(server), action, server)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Unable to control or get entity information")
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
        subscriber: Flow.Subscriber<in Control>,
    ) {
        val serverCount = serverManager.servers().size
        val server = serverManager.getServer(serverId)

        // Server name should only be specified if there's more than one server, as controls being split by structure (or the area names appended with the server name)
        // is done based on the presence of a server name.
        var serverName: String? = null
        if (server != null && serverCount > 1) {
            serverName = server.friendlyName
        }

        val splitMultiServersIntoStructures = splitMultiServersIntoStructures()
        val entityIds = controlIds.map { it.toEntityId(serverId) }

        if (server == null) {
            entityIds.forEachIndexed { index, entityId ->
                domainToHaControl["ha_failed"]?.createControl(
                    applicationContext,
                    failedItem(entityId, notFound = false),
                    HaControlInfo(
                        systemId = controlIds[index],
                        entityId = entityId,
                        serverId = serverId,
                    ),
                )?.let { control -> subscriber.onNext(control) }
            }
            return
        }

        val baseUrl =
            serverManager.connectionStateProvider(serverId).urlFlow().firstUrlOrNull()?.toString()?.removeSuffix("/")
                ?: ""

        webSocketScope.launch {
            var sentInitial = false
            entitiesForDisplayManager.observeInContext(serverId) { it.entityId in entityIds }
                .collect { state ->
                    when (state) {
                        EntityDisplayState.Loading -> Unit

                        EntityDisplayState.Error -> entityIds.forEachIndexed { index, entityId ->
                            sendControl(
                                subscriber = subscriber,
                                item = failedItem(entityId, notFound = false),
                                systemId = controlIds[index],
                                serverId = serverId,
                                serverName = serverName,
                                baseUrl = baseUrl,
                                splitMultiServersIntoStructures = splitMultiServersIntoStructures,
                                failed = true,
                            )
                        }

                        is EntityDisplayState.Loaded -> {
                            if (!sentInitial) {
                                // All requested entities are in the first resolution
                                sentInitial = true
                                (entityIds - state.entitiesById.keys).forEach { missingEntity ->
                                    Timber.e("Unable to get $missingEntity from Home Assistant, not resolved.")
                                    sendControl(
                                        subscriber = subscriber,
                                        item = failedItem(missingEntity, notFound = true),
                                        systemId = controlIds[entityIds.indexOf(missingEntity)],
                                        serverId = serverId,
                                        serverName = serverName,
                                        baseUrl = baseUrl,
                                        splitMultiServersIntoStructures = splitMultiServersIntoStructures,
                                        failed = true,
                                    )
                                }
                            }
                            Timber.d("Sending ${state.entities.size} entities to subscriber")
                            state.entities.forEach { item ->
                                sendControl(
                                    subscriber = subscriber,
                                    item = item,
                                    systemId = controlIds[entityIds.indexOf(item.entityId)],
                                    serverId = serverId,
                                    serverName = serverName,
                                    baseUrl = baseUrl,
                                    splitMultiServersIntoStructures = splitMultiServersIntoStructures,
                                )
                            }
                        }
                    }
                }
        }
    }

    private suspend fun sendControl(
        subscriber: Flow.Subscriber<in Control>,
        item: EntityDisplayWithContext,
        systemId: String,
        serverId: Int,
        serverName: String?,
        baseUrl: String,
        splitMultiServersIntoStructures: Boolean,
        failed: Boolean = false,
    ) {
        val info = HaControlInfo(
            systemId = systemId,
            entityId = item.entityId,
            serverId = serverId,
            serverName = serverName,
            authRequired = entityRequiresAuth(item.entityId, serverId),
            baseUrl = baseUrl,
            splitMultiServerIntoStructure = splitMultiServersIntoStructures,
        )
        val control = try {
            domainToHaControl[if (failed) "ha_failed" else item.domain]?.createControl(
                applicationContext,
                item,
                info,
            )
        } catch (e: Exception) {
            Timber.e(e, "Unable to create control for ${item.domain} entity, sending error entity")
            domainToHaControl["ha_failed"]?.createControl(
                applicationContext,
                failedItem(item.entityId, notFound = false),
                info,
            )
        }
        if (control != null) {
            subscriber.onNext(control)
        }
    }

    /** A display item for an entity that could not be resolved, rendered as a failed control. */
    private fun failedItem(entityId: String, notFound: Boolean): EntityDisplayWithContext = EntityDisplayWithContext(
        item = EntityDisplayWithoutContext(
            entityId = entityId,
            name = entityId,
            icon = CommunityMaterial.Icon.cmd_alert,
            rawState = if (notFound) FAILED_STATE_NOT_FOUND else FAILED_STATE_EXCEPTION,
        ),
    )

    /** The entity id a control id maps to, stripping the server prefix controls carry since multiserver. */
    private fun String.toEntityId(serverId: Int): String = if (split(".")[0].toIntOrNull() != null) {
        removePrefix("$serverId.")
    } else {
        this
    }

    private suspend fun entityRequiresAuth(entityId: String, serverId: Int): Boolean {
        return if (SdkVersion.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
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

    private suspend fun splitMultiServersIntoStructures(): Boolean {
        return prefsRepository.getControlsEnableStructure()
    }
}

private const val FAILED_STATE_NOT_FOUND = "notfound"
private const val FAILED_STATE_EXCEPTION = "exception"
