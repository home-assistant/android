package io.homeassistant.companion.android.vehicle

import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.util.vehicle.SUPPORTED_DOMAINS_WITH_STRING
import io.homeassistant.companion.android.util.vehicle.getHeaderBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A Car App screen that allows users to manage their automotive favorites when the vehicle is
 * parked. Each entity from the supported domains is displayed with a toggle to add or remove
 * it from the favorites list. Current favorites are sorted to the top.
 *
 * This screen stays fully within the Car App API, making it compliant with Play Store
 * automotive distribution policies.
 */
class ManageFavoritesVehicleScreen(
    carContext: CarContext,
    private val serverId: StateFlow<Int>,
    private val allEntities: Flow<Map<String, Entity>>,
    private val prefsRepository: PrefsRepository,
) : BaseVehicleScreen(carContext) {

    private var entities: List<Entity> = emptyList()
    private var favoritesList: List<AutoFavorite> = emptyList()
    private var isLoaded = false
    private var page = 0
    private val toggleMutex = Mutex()

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoritesList = prefsRepository.getAutoFavorites()
                allEntities.collect { entityMap ->
                    val favoriteEntityIds = favoritesList
                        .asSequence()
                        .filter { it.serverId == serverId.value }
                        .map { it.entityId }
                        .toSet()

                    val newEntities = entityMap.values
                        .filter { it.domain in SUPPORTED_DOMAINS_WITH_STRING }
                        .sortedWith(
                            compareByDescending<Entity> { entity ->
                                favoriteEntityIds.contains(entity.entityId)
                            }.thenBy { it.friendlyName },
                        )
                    val listChanged = newEntities.map { it.entityId } != entities.map { it.entityId }
                    if (listChanged) page = 0
                    val shouldInvalidate = !isLoaded || listChanged
                    entities = newEntities
                    isLoaded = true
                    if (shouldInvalidate) invalidate()
                }
            }
        }
    }

    override fun onDrivingOptimizedChanged(newState: Boolean) {
        if (newState) {
            lifecycleScope.launch {
                screenManager.pop()
            }
        }
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val listLimit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val pageSlice = computePageSlice(entities.size, page, listLimit)
        val pageEntities = if (isLoaded && pageSlice.fromIndex < entities.size) {
            entities.subList(pageSlice.fromIndex, pageSlice.toIndex)
        } else {
            emptyList()
        }

        return ListTemplate.Builder()
            .setHeader(carContext.getHeaderBuilder(commonR.string.android_automotive_favorites).build())
            .setLoading(!isLoaded)
            .apply {
                if (isLoaded) setSingleList(buildList(pageEntities, pageSlice).build())
            }
            .build()
    }

    private fun buildList(pageEntities: List<Entity>, pageSlice: PageSlice): ItemList.Builder {
        val listBuilder = ItemList.Builder()

        if (pageSlice.hasPreviousPage) {
            listBuilder.addItem(
                buildNavigationRow(commonR.string.aa_previous_page) {
                    page--
                    invalidate()
                },
            )
        }

        pageEntities.forEach { entity ->
            listBuilder.addItem(buildEntityRow(entity))
        }

        if (pageSlice.hasNextPage) {
            listBuilder.addItem(
                buildNavigationRow(commonR.string.aa_next_page) {
                    page++
                    invalidate()
                },
            )
        }

        if (isLoaded && entities.isEmpty()) {
            listBuilder.setNoItemsMessage(carContext.getString(commonR.string.no_supported_entities))
        }

        return listBuilder
    }

    private fun buildNavigationRow(titleRes: Int, onClick: () -> Unit): Row = Row.Builder()
        .setTitle(carContext.getString(titleRes))
        .setOnClickListener(onClick)
        .build()

    private fun buildEntityRow(entity: Entity): Row {
        val isFavorite = favoritesList.any {
            it.serverId == serverId.value && it.entityId == entity.entityId
        }
        val friendlyName = entity.attributes["friendly_name"]?.toString() ?: entity.entityId
        val domainLabel = SUPPORTED_DOMAINS_WITH_STRING[entity.domain]
            ?.let { carContext.getString(it) }
            ?: entity.domain

        return Row.Builder()
            .setTitle(friendlyName)
            .addText(domainLabel)
            .setToggle(
                Toggle.Builder { isChecked ->
                    lifecycleScope.launch {
                        toggleMutex.withLock {
                            val favorite = AutoFavorite(
                                serverId = serverId.value,
                                entityId = entity.entityId,
                            )
                            if (isChecked) {
                                prefsRepository.addAutoFavorite(favorite)
                            } else {
                                prefsRepository.setAutoFavorites(
                                    favoritesList.filterNot { it == favorite },
                                )
                            }
                            favoritesList = prefsRepository.getAutoFavorites()
                            val favoriteEntityIds = favoritesList
                                .filter { it.serverId == serverId.value }
                                .map { it.entityId }
                                .toSet()
                            entities = entities.sortedWith(
                                compareByDescending<Entity> { it.entityId in favoriteEntityIds }
                                    .thenBy { it.attributes["friendly_name"]?.toString() ?: it.entityId },
                            )
                            invalidate()
                        }
                    }
                }
                    .setChecked(isFavorite)
                    .build(),
            )
            .build()
    }
}

internal data class PageSlice(
    val fromIndex: Int,
    val toIndex: Int,
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean,
)

/**
 * Computes the slice of entities to display for the given page.
 *
 * Always reserves 2 rows for navigation (previous/next), giving a consistent
 * [itemsPerPage] across all pages and avoiding skipped entities.
 */
internal fun computePageSlice(totalItems: Int, page: Int, listLimit: Int): PageSlice {
    val itemsPerPage = (listLimit - 2).coerceAtLeast(1)
    val fromIndex = page * itemsPerPage
    val toIndex = minOf(fromIndex + itemsPerPage, totalItems)
    return PageSlice(
        fromIndex = fromIndex,
        toIndex = toIndex,
        hasPreviousPage = page > 0,
        hasNextPage = toIndex < totalItems,
    )
}
