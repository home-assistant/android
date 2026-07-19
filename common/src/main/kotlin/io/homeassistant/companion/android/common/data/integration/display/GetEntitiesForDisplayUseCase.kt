package io.homeassistant.companion.android.common.data.integration.display

import android.content.Context
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryDisplayEntry
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.FloorRegistryResponse
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

private const val MDI_PREFIX = "mdi:"

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
 * Both variants return a cold [Flow] that emits [EntityDisplayState.Loading] first and
 * completes with a terminal state, so consumers can render loading and error indicators.
 * Registry failures never produce [EntityDisplayState.Error]: the affected metadata degrades
 * to null and the entities are still returned, one item per input entity, in the same order.
 * The whole resolution runs on [Dispatchers.Default], making collection main thread safe.
 */
class GetEntitiesForDisplayUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: ServerManager,
) {

    /**
     * Variant that retrieves all entities of the server itself before resolving them,
     * keeping only the ones matching [filter] (which receives the raw [Entity] so it can
     * inspect attributes, like domain based or capability based filtering).
     *
     * Prefer the [invoke] overload taking an entity list when the caller already has the
     * entities for other purposes. When the entities cannot be retrieved the flow completes
     * with [EntityDisplayState.Error].
     */
    operator fun invoke(serverId: Int, filter: (Entity) -> Boolean = { true }): Flow<EntityDisplayState> = flow {
        emit(EntityDisplayState.Loading)
        val entities = fetchOrNull("entities") {
            serverManager.integrationRepository(serverId).getEntities()
        }
        if (entities == null) {
            emit(EntityDisplayState.Error)
        } else {
            emit(resolveState(serverId = serverId, entities = entities.filter(filter)))
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Resolves the display information of the given [entities], for callers that already
     * have the entity list (for example to filter on data only available on [Entity]).
     *
     * The flow always completes with [EntityDisplayState.Loaded] containing one item per
     * input entity in the same order; missing registry data degrades to null metadata.
     */
    operator fun invoke(serverId: Int, entities: List<Entity>): Flow<EntityDisplayState> = flow {
        emit(EntityDisplayState.Loading)
        emit(resolveState(serverId = serverId, entities = entities))
    }.flowOn(Dispatchers.Default)

    private suspend fun resolveState(serverId: Int, entities: List<Entity>): EntityDisplayState.Loaded {
        if (entities.isEmpty()) return EntityDisplayState.Loaded(emptyList())

        val version = serverManager.getServer(serverId)?.version
        val snapshot = fetchRegistrySnapshot(serverId, version)

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

        EntityDisplayItem(
            entityId = entity.entityId,
            name = displayEntry?.name ?: entity.friendlyName,
            icon = resolveIcon(entity, displayEntry?.icon),
            areaName = area?.name,
            floorName = floor?.name,
            deviceName = device?.nameByUser ?: device?.name,
            isHidden = displayEntry?.hidden ?: (classicEntry?.hiddenBy != null),
            entityCategory = displayEntry?.entityCategory
                ?.let { EntityCategory.fromString(snapshot.entityCategories[it]) }
                ?: EntityCategory.fromString(classicEntry?.entityCategory),
            displayPrecision = displayEntry?.displayPrecision
                ?: classicEntry?.options?.sensor?.let { it.displayPrecision ?: it.suggestedDisplayPrecision },
            labels = displayEntry?.labels.orEmpty(),
        )
    }

    private suspend fun fetchRegistrySnapshot(serverId: Int, version: HomeAssistantVersion?): RegistrySnapshot =
        coroutineScope {
            val webSocketRepository = try {
                serverManager.webSocketRepository(serverId)
            } catch (e: IllegalStateException) {
                Timber.e(e, "Failed to get WebSocketRepository for server $serverId")
                return@coroutineScope RegistrySnapshot()
            }

            val devices = async {
                fetchOrNull("device") { webSocketRepository.getDeviceRegistry() }
            }
            val areas = async {
                fetchOrNull("area") { webSocketRepository.getAreaRegistry() }
            }
            val floors = async {
                if (version?.isAtLeast(MIN_VERSION_FLOOR_REGISTRY) == true) {
                    fetchOrNull("floor") { webSocketRepository.getFloorRegistry() }
                } else {
                    null
                }
            }

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

            RegistrySnapshot(
                displayEntries = displayResponse?.entities?.associateBy { it.entityId },
                entityCategories = displayResponse?.entityCategories.orEmpty(),
                classicEntries = classicEntries?.associateBy { it.entityId },
                devices = devices.await().orEmpty().associateBy { it.id },
                areas = areas.await().orEmpty().associateBy { it.areaId },
                floors = floors.await().orEmpty().associateBy { it.floorId },
            )
        }

    private suspend fun <T> fetchOrNull(registryName: String, fetch: suspend () -> T?): T? = try {
        fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load $registryName registry")
        null
    }

    private fun resolveIcon(entity: Entity, customIcon: String?): IIcon {
        val customMdiIcon = customIcon
            ?.takeIf { it.startsWith(MDI_PREFIX) }
            ?.let { IconicsDrawable(context, "cmd-${it.removePrefix(MDI_PREFIX)}").icon }
        return customMdiIcon ?: entity.getIcon(context)
    }
}
