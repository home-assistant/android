package io.homeassistant.companion.android.common.data.integration.display

import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryDisplayEntry
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.FloorRegistryResponse
import io.homeassistant.companion.android.common.util.MDI_PREFIX
import io.homeassistant.companion.android.common.util.getIconByMdiName
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import timber.log.Timber

/**
 * Minimum server version to use `config/entity_registry/list_for_display` as the source of
 * entity display data.
 *
 * The command itself exists since 2023.3, but only since 2024.10 (https://github.com/home-assistant/core/pull/125832)
 * is the `en` field the server-resolved display name (`name or original_name`) and the `hn`
 * (`has_entity_name`) flag present. Before 2024.10, `en` was only sent for entities with
 * `has_entity_name` and no user-set name, so it cannot be trusted as the display name. Older
 * servers use the classic registry path instead, which matches the app's behavior before the
 * introduction of this adapter.
 */
private val MIN_VERSION_ENTITY_REGISTRY_DISPLAY = HomeAssistantVersion(year = 2024, month = 10, release = 0)

/** Minimum server version of the `config/floor_registry/list` command (https://github.com/home-assistant/core/pull/110741). */
private val MIN_VERSION_FLOOR_REGISTRY = HomeAssistantVersion(year = 2024, month = 3, release = 0)

/**
 * Events driving new emissions of [GetEntitiesForDisplayUseCase.observe]. Registry events name
 * the registry that changed, so only that part of the [RegistrySnapshot] is refetched.
 */
private sealed interface ObserveEvent {
    /** An entity state changed. */
    data class StateChanged(val entity: Entity) : ObserveEvent

    /** An area registry entry changed; the floors are refetched along, they resolve through areas. */
    data object AreaRegistryChanged : ObserveEvent

    /** A device registry entry changed. */
    data object DeviceRegistryChanged : ObserveEvent

    /** An entity registry entry changed. */
    data object EntityRegistryChanged : ObserveEvent
}

/**
 * Registry data fetched once per resolution, indexed by id for the merge.
 */
private data class RegistrySnapshot(
    val displayEntries: Map<String, EntityRegistryDisplayEntry>? = null,
    val entityCategories: Map<Int, String> = emptyMap(),
    val classicEntries: Map<String, EntityRegistryResponse>? = null,
    val devices: Map<String, DeviceRegistryResponse> = emptyMap(),
    val areas: Map<String, AreaRegistryResponse> = emptyMap(),
    val floors: Map<String, FloorRegistryResponse> = emptyMap(),
)

/**
 * Use case that resolves the display information (name, area, floor, device, icon, hidden,
 * category, precision, labels) for the given entities, fetching the data from the right
 * source depending on the server version:
 * - servers >= 2024.10 use the bandwidth-efficient `config/entity_registry/list_for_display`
 *   command (see [MIN_VERSION_ENTITY_REGISTRY_DISPLAY] for the rationale)
 * - older servers use the classic entity registry
 *
 * The [snapshot] variants return a cold [Flow] that emits [EntityDisplayState.Loading] first and
 * completes with a terminal state, so consumers can render loading and error indicators;
 * [observe] keeps emitting a fresh [EntityDisplayState.Loaded] as entity states and
 * registries change. Registry failures never produce an error state: the affected metadata
 * degrades to null and the entities are still returned, one item per input entity, in the same
 * order. The whole resolution runs on [Dispatchers.Default], making collection main thread safe.
 */
class GetEntitiesForDisplayUseCase @Inject constructor(private val serverManager: ServerManager) {

    /**
     * Variant that retrieves all entities of the server itself before resolving them,
     * keeping only the ones matching [filter] (which receives the raw [Entity] so it can
     * inspect attributes, like domain based or capability based filtering).
     *
     * Prefer the [snapshot] overload taking an entity list when the caller already has the
     * entities for other purposes. When the entities cannot be retrieved the flow completes
     * with [EntityDisplayState.Error].
     */
    fun snapshot(serverId: Int, filter: (Entity) -> Boolean = { true }): Flow<EntityDisplayState> = flow {
        val entities = loadEntities(serverId) ?: return@flow
        emit(resolveState(serverId = serverId, entities = entities.filter(filter)))
    }.flowOn(Dispatchers.Default)

    /**
     * Resolves the display information of the given [entities], for callers that already
     * have the entity list (for example to filter on data only available on [Entity]).
     *
     * The flow always completes with [EntityDisplayState.Loaded] containing one item per
     * input entity in the same order; missing registry data degrades to null metadata.
     */
    fun snapshot(serverId: Int, entities: List<Entity>): Flow<EntityDisplayState> = flow {
        emit(EntityDisplayState.Loading)
        emit(resolveState(serverId = serverId, entities = entities))
    }.flowOn(Dispatchers.Default)

    /**
     * Observes the display state of the entities matching [filter]: a new
     * [EntityDisplayState.Loaded] is emitted whenever an entity state change or an area,
     * device, or entity registry update changes the resolved items, so each emission is the
     * current display truth (including the state-dependent [EntityDisplayItem.icon] and
     * [EntityDisplayItem.state]). Updates that leave the items unchanged are skipped. Since
     * emissions are complete snapshots, slow consumers can safely `conflate()` the flow and
     * only render the latest one.
     *
     * The flow completes with [EntityDisplayState.Error] when the entities cannot be
     * retrieved, and completes after the initial [EntityDisplayState.Loaded] when no update
     * subscription is available (for example when the websocket connection is unavailable).
     */
    fun observe(serverId: Int, filter: (Entity) -> Boolean = { true }): Flow<EntityDisplayState> = flow {
        val initial = loadEntities(serverId) ?: return@flow

        val version = serverManager.getServer(serverId)?.version
        val entities = LinkedHashMap<String, Entity>()
        initial.filter(filter).forEach { entities[it.entityId] = it }

        val webSocketRepository = webSocketRepositoryOrNull(serverId)
        var snapshot = fetchRegistrySnapshot(webSocketRepository, version)
        var lastItems = resolveEntityDisplayItems(entities = entities.values.toList(), snapshot = snapshot)
        emit(EntityDisplayState.Loaded(lastItems))

        observeEvents(serverId, webSocketRepository).collect { event ->
            when (event) {
                is ObserveEvent.StateChanged -> with(event.entity) {
                    if (filter(this)) entities[entityId] = this else entities.remove(entityId)
                }
                ObserveEvent.AreaRegistryChanged -> webSocketRepository?.let {
                    snapshot = snapshot.copy(areas = it.fetchAreas(), floors = it.fetchFloors(version))
                }
                ObserveEvent.DeviceRegistryChanged -> webSocketRepository?.let {
                    snapshot = snapshot.copy(devices = it.fetchDevices())
                }
                ObserveEvent.EntityRegistryChanged -> webSocketRepository?.let {
                    snapshot = snapshot.withEntityEntries(it, version)
                }
            }
            val items = resolveEntityDisplayItems(entities = entities.values.toList(), snapshot = snapshot)
            if (items != lastItems) {
                lastItems = items
                emit(EntityDisplayState.Loaded(items))
            }
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Merges the entity state changes and the registry update events of the server into a
     * single [ObserveEvent] flow. Subscriptions that cannot be established are skipped, so the
     * flow is empty when the server supports none.
     */
    private suspend fun observeEvents(serverId: Int, webSocketRepository: WebSocketRepository?): Flow<ObserveEvent> =
        buildList {
            fetchOrNull("entity updates") { serverManager.integrationRepository(serverId).getEntityUpdates() }
                ?.let { updates -> add(updates.map { ObserveEvent.StateChanged(it) }) }

            if (webSocketRepository == null) return@buildList
            fetchOrNull("area updates") { webSocketRepository.getAreaRegistryUpdates() }
                ?.let { updates -> add(updates.map { ObserveEvent.AreaRegistryChanged }) }
            fetchOrNull("device updates") { webSocketRepository.getDeviceRegistryUpdates() }
                ?.let { updates -> add(updates.map { ObserveEvent.DeviceRegistryChanged }) }
            fetchOrNull("entity registry updates") { webSocketRepository.getEntityRegistryUpdates() }
                ?.let { updates -> add(updates.map { ObserveEvent.EntityRegistryChanged }) }
        }.merge()

    private suspend fun resolveState(serverId: Int, entities: List<Entity>): EntityDisplayState.Loaded {
        if (entities.isEmpty()) return EntityDisplayState.Loaded(emptyList())

        val version = serverManager.getServer(serverId)?.version
        val snapshot = fetchRegistrySnapshot(webSocketRepositoryOrNull(serverId), version)

        return EntityDisplayState.Loaded(resolveEntityDisplayItems(entities = entities, snapshot = snapshot))
    }

    /**
     * Merges entities with the registry snapshot into fully resolved [EntityDisplayItem]s,
     * returning one item per input entity in the same order.
     *
     * Each field is resolved from the registry entry of the entity (the display entry on
     * servers >= 2024.10, the classic entry otherwise, [RegistrySnapshot] carries only one
     * of the two) with the following precedence, stopping at the first available value:
     * - name: the registry display name (`en`), the `friendly_name` state attribute, the
     *   entity id
     * - icon: the custom icon of the registry entry (`ic`), the icon derived from the entity
     *   state attributes or its domain
     * - device name: the name given by the user to the device of the entity, the name
     *   provided by its integration
     * - area: the area assigned to the entity itself, the area of the device the entity
     *   belongs to
     * - floor: the floor of the resolved area, so a device-inherited area also resolves its
     *   floor
     * - hidden: the `hb` flag (display), a non null `hidden_by` (classic)
     * - category: the `ec` index decoded through the response categories mapping (display),
     *   the raw category string (classic)
     * - display precision: the server-computed `dp` (display); the user-configured precision,
     *   the integration-suggested precision (classic)
     * - labels: only available from the display entry, empty otherwise
     */
    private fun resolveEntityDisplayItems(
        entities: List<Entity>,
        snapshot: RegistrySnapshot,
    ): List<EntityDisplayItem> = entities.map { entity ->
        val displayEntry = snapshot.displayEntries?.get(entity.entityId)
        val classicEntry = snapshot.classicEntries?.get(entity.entityId)

        val device = (displayEntry?.deviceId ?: classicEntry?.deviceId)?.let { snapshot.devices[it] }
        val areaId = displayEntry?.areaId ?: classicEntry?.areaId ?: device?.areaId
        val area = areaId?.let { snapshot.areas[it] }
        val floor = area?.floorId?.let { snapshot.floors[it] }
        val customIcon = resolveCustomIcon(displayEntry?.icon)
        val displayPrecision = displayEntry?.displayPrecision
            ?: classicEntry?.options?.sensor?.let { it.displayPrecision ?: it.suggestedDisplayPrecision }

        EntityDisplayItem(
            entity = entity,
            name = displayEntry?.name?.takeIf { it.isNotBlank() } ?: entity.friendlyName,
            customIcon = customIcon,
            areaName = area?.name,
            floorName = floor?.name,
            deviceName = device?.nameByUser ?: device?.name,
            isHidden = displayEntry?.hidden ?: (classicEntry?.hiddenBy != null),
            entityCategory = displayEntry?.entityCategory
                ?.let { EntityCategory.fromString(snapshot.entityCategories[it]) }
                ?: EntityCategory.fromString(classicEntry?.entityCategory),
            displayPrecision = displayPrecision,
            labels = displayEntry?.labels.orEmpty(),
        )
    }

    /**
     * Emits [EntityDisplayState.Loading] and fetches the entities of the server, emitting
     * [EntityDisplayState.Error] and returning null when they cannot be retrieved.
     */
    private suspend fun FlowCollector<EntityDisplayState>.loadEntities(serverId: Int): List<Entity>? {
        emit(EntityDisplayState.Loading)
        val entities = fetchOrNull("entities") {
            serverManager.integrationRepository(serverId).getEntities()
        }
        if (entities == null) emit(EntityDisplayState.Error)
        return entities
    }

    /** The [ServerManager.webSocketRepository] of the server, or null when it cannot be provided. */
    private suspend fun webSocketRepositoryOrNull(serverId: Int): WebSocketRepository? = try {
        serverManager.webSocketRepository(serverId)
    } catch (e: IllegalStateException) {
        Timber.e(e, "Failed to get WebSocketRepository for server $serverId")
        null
    }

    private suspend fun fetchRegistrySnapshot(
        webSocketRepository: WebSocketRepository?,
        version: HomeAssistantVersion?,
    ): RegistrySnapshot = coroutineScope {
        if (webSocketRepository == null) return@coroutineScope RegistrySnapshot()

        val devices = async { webSocketRepository.fetchDevices() }
        val areas = async { webSocketRepository.fetchAreas() }
        val floors = async { webSocketRepository.fetchFloors(version) }

        RegistrySnapshot(
            devices = devices.await(),
            areas = areas.await(),
            floors = floors.await(),
        ).withEntityEntries(webSocketRepository, version)
    }

    private suspend fun WebSocketRepository.fetchDevices(): Map<String, DeviceRegistryResponse> =
        fetchOrNull("device") { getDeviceRegistry() }.orEmpty().associateBy { it.id }

    private suspend fun WebSocketRepository.fetchAreas(): Map<String, AreaRegistryResponse> =
        fetchOrNull("area") { getAreaRegistry() }.orEmpty().associateBy { it.areaId }

    private suspend fun WebSocketRepository.fetchFloors(
        version: HomeAssistantVersion?,
    ): Map<String, FloorRegistryResponse> = if (version?.isAtLeast(MIN_VERSION_FLOOR_REGISTRY) == true) {
        fetchOrNull("floor") { getFloorRegistry() }.orEmpty().associateBy { it.floorId }
    } else {
        emptyMap()
    }

    /** Returns a copy of the snapshot with freshly fetched entity registry entries. */
    private suspend fun RegistrySnapshot.withEntityEntries(
        webSocketRepository: WebSocketRepository,
        version: HomeAssistantVersion?,
    ): RegistrySnapshot {
        val displayResponse = if (version?.isAtLeast(MIN_VERSION_ENTITY_REGISTRY_DISPLAY) == true) {
            fetchOrNull("entity display") { webSocketRepository.getEntityRegistryDisplay() }
        } else {
            null
        }
        // Fall back to the classic registry when the display command is unavailable or failed
        val classicEntries = if (displayResponse == null) {
            fetchOrNull("entity") { webSocketRepository.getEntityRegistry() }
        } else {
            null
        }

        return copy(
            displayEntries = displayResponse?.entities?.associateBy { it.entityId },
            entityCategories = displayResponse?.entityCategories.orEmpty(),
            classicEntries = classicEntries?.associateBy { it.entityId },
        )
    }

    private fun resolveCustomIcon(customIcon: String?): IIcon? = customIcon
        ?.takeIf { it.startsWith(MDI_PREFIX) }
        ?.let { CommunityMaterial.getIconByMdiName(it) }
}

private suspend fun <T> fetchOrNull(registryName: String, fetch: suspend () -> T?): T? = try {
    fetch()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Timber.e(e, "Couldn't load $registryName registry")
    null
}
