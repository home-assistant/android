package io.homeassistant.companion.android.common.data.integration.display

import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.getEntitiesOrNull
import io.homeassistant.companion.android.common.data.integration.getEntityUpdatesOrNull
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.integrationRepositoryOrNull
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.getAreaRegistryOrNull
import io.homeassistant.companion.android.common.data.websocket.getAreaRegistryUpdatesOrNull
import io.homeassistant.companion.android.common.data.websocket.getDeviceRegistryOrNull
import io.homeassistant.companion.android.common.data.websocket.getDeviceRegistryUpdatesOrNull
import io.homeassistant.companion.android.common.data.websocket.getEntityRegistryDisplayOrNull
import io.homeassistant.companion.android.common.data.websocket.getEntityRegistryOrNull
import io.homeassistant.companion.android.common.data.websocket.getEntityRegistryUpdatesOrNull
import io.homeassistant.companion.android.common.data.websocket.getFloorRegistryOrNull
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryDisplayEntry
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.FloorRegistryResponse
import io.homeassistant.companion.android.common.util.MDI_PREFIX
import io.homeassistant.companion.android.common.util.getIconByMdiName
import javax.inject.Inject
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
 * Events driving new emissions of [EntitiesForDisplayManager.observe]. Registry events name
 * the registry that changed, so only that part of the [RegistrySnapshot] is refetched.
 */
private sealed interface ObserveEvent {
    sealed interface EntityEvent : ObserveEvent {
        /** An entity state changed. */
        data class StateChanged(val entity: Entity) : EntityEvent

        /** An entity registry entry changed. */
        data object EntityRegistryChanged : EntityEvent
    }

    /** An area registry entry changed; the floors are refetched along, they resolve through areas. */
    data object AreaRegistryChanged : ObserveEvent

    /** A device registry entry changed. */
    data object DeviceRegistryChanged : ObserveEvent
}

/**
 * Registry data fetched once per resolution, indexed by id for the merge.
 */
/**
 * Entity registry of a server, the source of everything an [EntityDisplayWithoutContext] resolves beyond the
 * entity state: servers >= 2024.10 provide the display entries, older ones the classic entries.
 */
private class EntityRegistryEntries(
    private val displayEntries: Map<String, EntityRegistryDisplayEntry>? = null,
    private val entityCategories: Map<Int, String> = emptyMap(),
    private val classicEntries: Map<String, EntityRegistryResponse>? = null,
) {
    /** Display entry of [entityId], for the callers needing what only it holds (area, device). */
    fun displayEntry(entityId: String): EntityRegistryDisplayEntry? = displayEntries?.get(entityId)

    /** Classic entry of [entityId], for the callers needing what only it holds (area, device). */
    fun classicEntry(entityId: String): EntityRegistryResponse? = classicEntries?.get(entityId)

    /**
     * Name to display for [entity]: the name of its registry entry, its `friendly_name` state
     * attribute, or its id. This is the single place where the app decides how an entity is named,
     * so a caller without registry entries resolves the same name as one with them.
     */
    fun nameOf(entity: Entity): String =
        displayEntries?.get(entity.entityId)?.name?.takeIf { it.isNotBlank() } ?: entity.friendlyName

    /**
     * Resolves [entity] into everything its state and the registry provide, with the following
     * precedence, stopping at the first available value:
     * - name: the registry display name (`en`), the `friendly_name` state attribute, the entity id
     * - icon: the custom icon of the registry entry (`ic`), the icon derived from the entity state
     *   attributes or its domain
     * - hidden: the `hb` flag (display), a non null `hidden_by` (classic)
     * - category: the `ec` index decoded through the response categories mapping (display), the raw
     *   category string (classic)
     * - display precision: the server-computed `dp` (display); the user-configured precision, the
     *   integration-suggested precision (classic)
     * - labels: only available from the display entry, empty otherwise
     */
    fun itemFor(entity: Entity): EntityDisplayWithoutContext {
        val displayEntry = displayEntry(entity.entityId)
        val classicEntry = classicEntry(entity.entityId)

        return EntityDisplayWithoutContext(
            entity = entity,
            name = nameOf(entity),
            customIcon = displayEntry?.icon.toIcon(),
            isHidden = displayEntry?.hidden ?: (classicEntry?.hiddenBy != null),
            entityCategory = displayEntry?.entityCategory
                ?.let { EntityCategory.fromString(entityCategories[it]) }
                ?: EntityCategory.fromString(classicEntry?.entityCategory),
            displayPrecision = displayEntry?.displayPrecision
                ?: classicEntry?.options?.sensor?.let { it.displayPrecision ?: it.suggestedDisplayPrecision },
            labels = displayEntry?.labels.orEmpty(),
        )
    }

    private fun String?.toIcon(): IIcon? = this
        ?.takeIf { it.startsWith(MDI_PREFIX) }
        ?.let { CommunityMaterial.getIconByMdiName(it) }
}

/**
 * Fetches the entity registry of the server, falling back to the classic registry when the display
 * one is unavailable, and empty when there is no websocket to ask.
 */
private suspend fun WebSocketRepository?.fetchEntityRegistryEntries(): EntityRegistryEntries {
    if (this == null) return EntityRegistryEntries()

    val displayResponse = getEntityRegistryDisplayOrNull()
    // Fall back to the classic registry when the display command is unavailable or failed
    val classicEntries = if (displayResponse == null) getEntityRegistryOrNull() else null

    return EntityRegistryEntries(
        displayEntries = displayResponse?.entities?.associateBy { it.entityId },
        entityCategories = displayResponse?.entityCategories.orEmpty(),
        classicEntries = classicEntries?.associateBy { it.entityId },
    )
}

/**
 * Registry data fetched once per resolution, indexed by id for the merge.
 */
private data class RegistrySnapshot(
    val entries: EntityRegistryEntries = EntityRegistryEntries(),
    val devices: Map<String, DeviceRegistryResponse> = emptyMap(),
    val areas: Map<String, AreaRegistryResponse> = emptyMap(),
    val floors: Map<String, FloorRegistryResponse> = emptyMap(),
)

/**
 * Use case that resolves the display information (name, area, floor, device, icon, hidden,
 * category, precision, labels) for the given entities, from the bandwidth-efficient
 * `config/entity_registry/list_for_display` command, falling back to the classic entity registry
 * when the server is too old for it (see [WebSocketRepository.getEntityRegistryDisplay]).
 *
 * The [snapshot] variants return a cold [Flow] that emits [EntityDisplayState.Loading] first and
 * completes with a terminal state, so consumers can render loading and error indicators;
 * [observe] keeps emitting a fresh [EntityDisplayState.Loaded] as entity states and
 * registries change. Registry failures never produce an error state: the affected metadata
 * degrades to null and the entities are still returned, one item per input entity, in the same
 * order. The whole resolution runs on [Dispatchers.Default], making collection main thread safe.
 *
 * The `observe`/`snapshot` variants resolve from the entity registry only; the `*InContext`
 * variants also resolve the area, floor and device registries into an [EntityDisplayWithContext].
 */
class EntitiesForDisplayManager @Inject constructor(private val serverManager: ServerManager) {

    /**
     * Observes [entityIds] with their display information, or every entity of the server when it is
     * null.
     *
     * Each emission is a complete [EntityDisplayState.Loaded] snapshot of the entities that could
     * be retrieved. A new snapshot is emitted on every state change of the observed entities and
     * on every entity registry update, even when it resolves to the same items, so consumers that
     * refresh on the entities themselves see each change; apply `distinctUntilChanged` when only
     * the resolved values matter.
     *
     * The flow completes with [EntityDisplayState.Error] when none of the entities can be
     * retrieved, and completes after the initial snapshot when no update subscription is
     * available (for example when the websocket connection is unavailable).
     */
    fun observe(serverId: Int, entityIds: List<String>? = null): Flow<EntityDisplayState<EntityDisplayWithoutContext>> =
        flow {
            emit(EntityDisplayState.Loading)

            if (!hasRegisteredServer()) return@flow

            val webSocketRepository = serverManager.webSocketRepositoryOrNull(serverId)
            var registryEntries = webSocketRepository.fetchEntityRegistryEntries()

            val entities = fetchEntities(serverId, entityIds).associateByTo(LinkedHashMap()) { it.entityId }
            if (entities.isEmpty()) {
                emit(EntityDisplayState.Error)
                return@flow
            }

            fun snapshot() = EntityDisplayState.Loaded(entities.values.map { registryEntries.itemFor(it) })

            emit(snapshot())

            observeEntityEvents(serverId, webSocketRepository, entityIds).collect { event ->
                when (event) {
                    is ObserveEvent.EntityEvent.StateChanged -> entities[event.entity.entityId] = event.entity
                    ObserveEvent.EntityEvent.EntityRegistryChanged -> {
                        registryEntries = webSocketRepository.fetchEntityRegistryEntries()
                    }
                }
                emit(snapshot())
            }
        }.flowOn(Dispatchers.Default)

    /**
     * Resolves [entityIds] with their display information once, the one-shot counterpart of
     * [observe]. Use [observe] instead to follow the entities and their display information over
     * time.
     *
     * The flow emits [EntityDisplayState.Loading], then [EntityDisplayState.Error] when none of the
     * entities can be retrieved, otherwise a single [EntityDisplayState.Loaded] holding the entities
     * that could be retrieved, in the requested order.
     */
    fun snapshot(serverId: Int, entityIds: List<String>): Flow<EntityDisplayState<EntityDisplayWithoutContext>> = flow {
        emit(EntityDisplayState.Loading)

        if (!hasRegisteredServer()) return@flow

        val entities = fetchEntities(serverId, entityIds)
        if (entities.isEmpty()) {
            emit(EntityDisplayState.Error)
            return@flow
        }

        val registryEntries = serverManager.webSocketRepositoryOrNull(serverId).fetchEntityRegistryEntries()
        emit(EntityDisplayState.Loaded(entities.map { registryEntries.itemFor(it) }))
    }.flowOn(Dispatchers.Default)

    /**
     * Variant that retrieves all entities of the server itself before resolving them,
     * keeping only the ones matching [filter] (which receives the raw [Entity] so it can
     * inspect attributes, like domain based or capability based filtering).
     *
     * When the entities cannot be retrieved the flow completes with [EntityDisplayState.Error].
     */
    fun snapshotInContext(
        serverId: Int,
        filter: (Entity) -> Boolean = { true },
    ): Flow<EntityDisplayState<EntityDisplayWithContext>> = flow {
        val entities = loadEntities(serverId) ?: return@flow
        emit(resolveState(serverId = serverId, entities = entities.filter(filter)))
    }.flowOn(Dispatchers.Default)

    /**
     * Observes the display state of the entities matching [filter]: a new
     * [EntityDisplayState.Loaded] is emitted whenever an entity state change or an area,
     * device, or entity registry update changes the resolved items, so each emission is the
     * current display truth (including the state-dependent [EntityDisplayWithoutContext.icon] and
     * [EntityDisplayWithoutContext.state]). Updates that leave the items unchanged are skipped. Since
     * emissions are complete snapshots, slow consumers can safely `conflate()` the flow and
     * only render the latest one.
     *
     * The flow completes with [EntityDisplayState.Error] when the entities cannot be
     * retrieved, and completes after the initial [EntityDisplayState.Loaded] when no update
     * subscription is available (for example when the websocket connection is unavailable).
     */
    fun observeInContext(
        serverId: Int,
        filter: (Entity) -> Boolean = { true },
    ): Flow<EntityDisplayState<EntityDisplayWithContext>> = flow {
        val initial = loadEntities(serverId) ?: return@flow

        val entities = initial.filter(filter).associateByTo(LinkedHashMap()) { it.entityId }

        val webSocketRepository = serverManager.webSocketRepositoryOrNull(serverId)
        var snapshot = fetchRegistrySnapshot(webSocketRepository)
        var lastItems = resolveEntityDisplayItems(entities = entities.values.toList(), snapshot = snapshot)
        emit(EntityDisplayState.Loaded(lastItems))

        observeContextEvents(serverId, webSocketRepository).collect { event ->
            when (event) {
                is ObserveEvent.EntityEvent.StateChanged -> with(event.entity) {
                    if (filter(this)) entities[entityId] = this else entities.remove(entityId)
                }
                ObserveEvent.AreaRegistryChanged -> webSocketRepository?.let {
                    snapshot = snapshot.copy(areas = it.fetchAreas(), floors = it.fetchFloors())
                }
                ObserveEvent.DeviceRegistryChanged -> webSocketRepository?.let {
                    snapshot = snapshot.copy(devices = it.fetchDevices())
                }
                ObserveEvent.EntityEvent.EntityRegistryChanged -> webSocketRepository?.let {
                    snapshot = snapshot.withEntityEntries(it)
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
     * Merges what an [EntityDisplayWithoutContext] depends on into a single [ObserveEvent] flow: the state
     * changes of [entityIds] and the entity registry updates. Subscriptions that cannot be
     * established are skipped, so the flow is empty when the server supports none.
     */
    private suspend fun observeEntityEvents(
        serverId: Int,
        webSocketRepository: WebSocketRepository?,
        entityIds: List<String>?,
    ): Flow<ObserveEvent.EntityEvent> = listOfNotNull(
        stateChanges(serverId, entityIds),
        webSocketRepository?.entityRegistryChanges(),
    ).merge()

    /**
     * Fetches the entities of the server in a single call, keeping [entityIds] in the order they
     * were requested and omitting the ones the server doesn't know, or all of them when it is null.
     */
    private suspend fun fetchEntities(serverId: Int, entityIds: List<String>?): List<Entity> {
        val entities = serverManager.integrationRepositoryOrNull(serverId)?.getEntitiesOrNull().orEmpty()
        if (entityIds == null) return entities

        val entitiesById = entities.associateBy { it.entityId }
        return entityIds.mapNotNull { entitiesById[it] }
    }

    /**
     * Merges the entity state changes and the registry update events of the server into a
     * single [ObserveEvent] flow. Subscriptions that cannot be established are skipped, so the
     * flow is empty when the server supports none.
     */
    private suspend fun observeContextEvents(
        serverId: Int,
        webSocketRepository: WebSocketRepository?,
    ): Flow<ObserveEvent> = listOfNotNull(
        stateChanges(serverId, entityIds = null),
        webSocketRepository?.areaRegistryChanges(),
        webSocketRepository?.deviceRegistryChanges(),
        webSocketRepository?.entityRegistryChanges(),
    ).merge()

    /**
     * Subscription to the state changes of [entityIds], or of every entity of the server when it
     * is null, and null when it cannot be established.
     */
    private suspend fun stateChanges(serverId: Int, entityIds: List<String>?): Flow<ObserveEvent.EntityEvent>? {
        val integrationRepository = serverManager.integrationRepositoryOrNull(serverId) ?: return null
        val updates = with(integrationRepository) {
            if (entityIds == null) getEntityUpdatesOrNull() else getEntityUpdatesOrNull(entityIds)
        }

        return updates?.map { ObserveEvent.EntityEvent.StateChanged(it) }
    }

    private suspend fun WebSocketRepository.areaRegistryChanges(): Flow<ObserveEvent>? =
        getAreaRegistryUpdatesOrNull()?.map { ObserveEvent.AreaRegistryChanged }

    private suspend fun WebSocketRepository.deviceRegistryChanges(): Flow<ObserveEvent>? =
        getDeviceRegistryUpdatesOrNull()?.map { ObserveEvent.DeviceRegistryChanged }

    private suspend fun WebSocketRepository.entityRegistryChanges(): Flow<ObserveEvent.EntityEvent>? =
        getEntityRegistryUpdatesOrNull()?.map { ObserveEvent.EntityEvent.EntityRegistryChanged }

    /**
     * Emits [EntityDisplayState.Error] when no server is registered, returning whether one is, so
     * the callers stop before asking a server that isn't there.
     */
    private suspend fun <T : EntityDisplay> FlowCollector<EntityDisplayState<T>>.hasRegisteredServer(): Boolean {
        if (serverManager.isRegistered()) return true

        Timber.w("No server registered, there is no entity to display")
        emit(EntityDisplayState.Error)
        return false
    }

    private suspend fun resolveState(
        serverId: Int,
        entities: List<Entity>,
    ): EntityDisplayState.Loaded<EntityDisplayWithContext> {
        if (entities.isEmpty()) return EntityDisplayState.Loaded(emptyList())

        val snapshot = fetchRegistrySnapshot(serverManager.webSocketRepositoryOrNull(serverId))

        return EntityDisplayState.Loaded(resolveEntityDisplayItems(entities = entities, snapshot = snapshot))
    }

    /**
     * Merges entities with the registry snapshot into fully resolved [EntityDisplayWithoutContext]s,
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
    ): List<EntityDisplayWithContext> = entities.map { entity ->
        val displayEntry = snapshot.entries.displayEntry(entity.entityId)
        val classicEntry = snapshot.entries.classicEntry(entity.entityId)

        val device = (displayEntry?.deviceId ?: classicEntry?.deviceId)?.let { snapshot.devices[it] }
        val areaId = displayEntry?.areaId ?: classicEntry?.areaId ?: device?.areaId
        val area = areaId?.let { snapshot.areas[it] }
        val floor = area?.floorId?.let { snapshot.floors[it] }
        EntityDisplayWithContext(
            item = snapshot.entries.itemFor(entity),
            areaName = area?.name,
            floorName = floor?.name,
            deviceName = device?.nameByUser ?: device?.name,
        )
    }

    /**
     * Emits [EntityDisplayState.Loading] and fetches the entities of the server, emitting
     * [EntityDisplayState.Error] and returning null when they cannot be retrieved.
     */
    private suspend fun FlowCollector<EntityDisplayState<EntityDisplayWithContext>>.loadEntities(
        serverId: Int,
    ): List<Entity>? {
        emit(EntityDisplayState.Loading)

        if (!hasRegisteredServer()) return null

        val entities = serverManager.integrationRepositoryOrNull(serverId)?.getEntitiesOrNull()
        if (entities == null) emit(EntityDisplayState.Error)
        return entities
    }

    private suspend fun fetchRegistrySnapshot(webSocketRepository: WebSocketRepository?): RegistrySnapshot =
        coroutineScope {
            if (webSocketRepository == null) return@coroutineScope RegistrySnapshot()

            val devices = async { webSocketRepository.fetchDevices() }
            val areas = async { webSocketRepository.fetchAreas() }
            val floors = async { webSocketRepository.fetchFloors() }

            RegistrySnapshot(
                devices = devices.await(),
                areas = areas.await(),
                floors = floors.await(),
            ).withEntityEntries(webSocketRepository)
        }

    private suspend fun WebSocketRepository.fetchDevices(): Map<String, DeviceRegistryResponse> =
        getDeviceRegistryOrNull().orEmpty().associateBy { it.id }

    private suspend fun WebSocketRepository.fetchAreas(): Map<String, AreaRegistryResponse> =
        getAreaRegistryOrNull().orEmpty().associateBy { it.areaId }

    private suspend fun WebSocketRepository.fetchFloors(): Map<String, FloorRegistryResponse> =
        getFloorRegistryOrNull().orEmpty().associateBy { it.floorId }

    /** Returns a copy of the snapshot with freshly fetched entity registry entries. */
    private suspend fun RegistrySnapshot.withEntityEntries(webSocketRepository: WebSocketRepository) =
        copy(entries = webSocketRepository.fetchEntityRegistryEntries())
}
