package io.homeassistant.companion.android.vehicle

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.util.vehicle.SUPPORTED_DOMAINS_WITH_STRING
import io.homeassistant.companion.android.util.vehicle.getHeaderBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A Car App screen that allows users to manage their automotive favorites when the vehicle is
 * parked. Each entity from the supported domains is displayed with a toggle to add or remove
 * it from the favorites list. Current favorites are sorted to the top.
 *
 * Pagination prev/next controls live in the header so that the full list capacity is available
 * for entity rows. A search action in the header opens a [SearchFavoritesVehicleScreen] for
 * filtering by name.
 *
 * This screen stays fully within the Car App API, making it compliant with Play Store
 * automotive distribution policies.
 */
@RequiresApi(Build.VERSION_CODES.O)
class ManageFavoritesVehicleScreen(
    carContext: CarContext,
    private val serverId: StateFlow<Int>,
    private val allEntities: Flow<Map<String, Entity>>,
    private val prefsRepository: PrefsRepository,
) : BaseVehicleScreen(carContext) {

    private data class UIState(
        val entities: List<Entity> = emptyList(),
        val favoritesList: List<AutoFavorite> = emptyList(),
        val isLoaded: Boolean = false,
        val page: Int = 0,
    )

    @Volatile
    private var uiState = UIState()
    private val stateMutex = Mutex()

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val initialFavorites = withContext(Dispatchers.IO) { prefsRepository.getAutoFavorites() }
                stateMutex.withLock {
                    uiState = uiState.copy(favoritesList = initialFavorites)
                }
                allEntities.collect { entityMap ->
                    withContext(Dispatchers.Default) {
                        stateMutex.withLock {
                            val state = uiState
                            val favoriteIds = favoriteEntityIdsForServer(state.favoritesList, serverId.value)
                            val newEntities = entityMap.values
                                .filter { it.domain in SUPPORTED_DOMAINS_WITH_STRING }
                                .sortedWith(compareByFavoriteThenName(favoriteIds))
                            val listChanged = newEntities.map { it.entityId } != state.entities.map { it.entityId }
                            val shouldInvalidate = !state.isLoaded || listChanged
                            uiState = state.copy(
                                entities = newEntities,
                                isLoaded = true,
                                page = if (listChanged) 0 else state.page,
                            )
                            if (shouldInvalidate) invalidate()
                        }
                    }
                }
            }
        }
    }

    override fun onDrivingOptimizedChanged(newState: Boolean) {
        if (newState) {
            screenManager.pop()
        }
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val state = uiState
        val listLimit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val pageSlice = computePageSlice(state.entities.size, state.page, listLimit)
        val pageEntities = if (state.isLoaded && pageSlice.fromIndex < state.entities.size) {
            state.entities.subList(pageSlice.fromIndex, pageSlice.toIndex)
        } else {
            emptyList()
        }

        return ListTemplate.Builder()
            .setHeader(buildHeader(state, pageSlice))
            .setLoading(!state.isLoaded)
            .apply {
                if (state.isLoaded) setSingleList(buildEntityList(state, pageEntities).build())
            }
            .build()
    }

    private fun buildHeader(state: UIState, pageSlice: PageSlice): Header {
        val builder = carContext.getHeaderBuilder(commonR.string.android_automotive_favorites)
        if (state.isLoaded) {
            if (pageSlice.hasPreviousPage) {
                builder.addEndHeaderAction(
                    Action.Builder()
                        .setIcon(carIcon(carContext, CommunityMaterial.Icon.cmd_chevron_left))
                        .setTitle(carContext.getString(commonR.string.aa_previous_page))
                        .setOnClickListener {
                            lifecycleScope.launch {
                                stateMutex.withLock {
                                    uiState = uiState.copy(page = (uiState.page - 1).coerceAtLeast(0))
                                    invalidate()
                                }
                            }
                        }
                        .build(),
                )
            }
            if (pageSlice.hasNextPage) {
                builder.addEndHeaderAction(
                    Action.Builder()
                        .setIcon(carIcon(carContext, CommunityMaterial.Icon.cmd_chevron_right))
                        .setTitle(carContext.getString(commonR.string.aa_next_page))
                        .setOnClickListener {
                            lifecycleScope.launch {
                                stateMutex.withLock {
                                    uiState = uiState.copy(page = uiState.page + 1)
                                    invalidate()
                                }
                            }
                        }
                        .build(),
                )
            }
        }
        builder.addEndHeaderAction(
            Action.Builder()
                .setIcon(carIcon(carContext, CommunityMaterial.Icon3.cmd_magnify))
                .setOnClickListener {
                    screenManager.push(
                        SearchFavoritesVehicleScreen(carContext, serverId, allEntities, prefsRepository),
                    )
                }
                .build(),
        )
        return builder.build()
    }

    private fun buildEntityList(state: UIState, pageEntities: List<Entity>): ItemList.Builder {
        val listBuilder = ItemList.Builder()
        pageEntities.forEach { entity ->
            val isFavorite = state.favoritesList.any {
                it.serverId == serverId.value && it.entityId == entity.entityId
            }
            listBuilder.addItem(
                buildFavoriteEntityRow(carContext, entity, isFavorite) { isChecked ->
                    onFavoriteToggled(entity, isChecked)
                },
            )
        }
        if (state.entities.isEmpty()) {
            listBuilder.setNoItemsMessage(carContext.getString(commonR.string.no_supported_entities))
        }
        return listBuilder
    }

    private fun onFavoriteToggled(entity: Entity, isChecked: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                val newFavorites = persistFavoriteToggle(
                    prefsRepository,
                    serverId.value,
                    entity.entityId,
                    isChecked,
                    uiState.favoritesList,
                )
                val favoriteIds = favoriteEntityIdsForServer(newFavorites, serverId.value)
                uiState = uiState.copy(
                    favoritesList = newFavorites,
                    entities = uiState.entities.sortedWith(compareByFavoriteThenName(favoriteIds)),
                )
                invalidate()
            }
        }
    }
}

/**
 * A Car App screen showing a [SearchTemplate] over the same favorite-aware entity list as
 * [ManageFavoritesVehicleScreen]. The user types to filter by friendly name or entity id and
 * can toggle favorites on the matching rows. Pops itself when driving-optimized restrictions
 * kick in (the search keyboard is unsafe while driving).
 */
@RequiresApi(Build.VERSION_CODES.O)
private class SearchFavoritesVehicleScreen(
    carContext: CarContext,
    private val serverId: StateFlow<Int>,
    private val allEntities: Flow<Map<String, Entity>>,
    private val prefsRepository: PrefsRepository,
) : BaseVehicleScreen(carContext) {

    private data class UIState(
        val entities: List<Entity> = emptyList(),
        val favoritesList: List<AutoFavorite> = emptyList(),
        val query: String = "",
        val isLoaded: Boolean = false,
    )

    @Volatile
    private var uiState = UIState()
    private val stateMutex = Mutex()

    private val searchCallback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            lifecycleScope.launch {
                stateMutex.withLock {
                    if (uiState.query != searchText) {
                        uiState = uiState.copy(query = searchText)
                        invalidate()
                    }
                }
            }
        }
    }

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val initialFavorites = withContext(Dispatchers.IO) { prefsRepository.getAutoFavorites() }
                stateMutex.withLock {
                    uiState = uiState.copy(favoritesList = initialFavorites)
                }
                allEntities.collect { entityMap ->
                    withContext(Dispatchers.Default) {
                        stateMutex.withLock {
                            val state = uiState
                            val favoriteIds = favoriteEntityIdsForServer(state.favoritesList, serverId.value)
                            val newEntities = entityMap.values
                                .filter { it.domain in SUPPORTED_DOMAINS_WITH_STRING }
                                .sortedWith(compareByFavoriteThenName(favoriteIds))
                            val listChanged = newEntities.map { it.entityId } != state.entities.map { it.entityId }
                            val shouldInvalidate = !state.isLoaded || listChanged
                            uiState = state.copy(entities = newEntities, isLoaded = true)
                            if (shouldInvalidate) invalidate()
                        }
                    }
                }
            }
        }
    }

    override fun onDrivingOptimizedChanged(newState: Boolean) {
        if (newState) {
            screenManager.pop()
        }
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val state = uiState
        val listLimit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val results = filterEntitiesByQuery(state.entities, state.query).take(listLimit)

        val itemListBuilder = ItemList.Builder()
        results.forEach { entity ->
            val isFavorite = state.favoritesList.any {
                it.serverId == serverId.value && it.entityId == entity.entityId
            }
            itemListBuilder.addItem(
                buildFavoriteEntityRow(carContext, entity, isFavorite) { isChecked ->
                    onFavoriteToggled(entity, isChecked)
                },
            )
        }
        if (state.isLoaded && results.isEmpty()) {
            val message = if (state.query.isBlank()) {
                commonR.string.no_supported_entities
            } else {
                commonR.string.aa_search_no_results
            }
            itemListBuilder.setNoItemsMessage(carContext.getString(message))
        }

        return SearchTemplate.Builder(searchCallback)
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(commonR.string.aa_search_entities))
            .setShowKeyboardByDefault(true)
            .setLoading(!state.isLoaded)
            .apply { if (state.isLoaded) setItemList(itemListBuilder.build()) }
            .build()
    }

    private fun onFavoriteToggled(entity: Entity, isChecked: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            stateMutex.withLock {
                val newFavorites = persistFavoriteToggle(
                    prefsRepository,
                    serverId.value,
                    entity.entityId,
                    isChecked,
                    uiState.favoritesList,
                )
                val favoriteIds = favoriteEntityIdsForServer(newFavorites, serverId.value)
                uiState = uiState.copy(
                    favoritesList = newFavorites,
                    entities = uiState.entities.sortedWith(compareByFavoriteThenName(favoriteIds)),
                )
                invalidate()
            }
        }
    }
}

private data class PageSlice(
    val fromIndex: Int,
    val toIndex: Int,
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean,
)

/**
 * Computes the slice of entities to display for the given page.
 *
 * Pagination controls live in the header instead of consuming list rows, so [listLimit] is
 * the full capacity available for entity rows. [page] is clamped against the current total so
 * a stale page index can't produce an empty slice or an out-of-bounds [List.subList] read.
 */
private fun computePageSlice(totalItems: Int, page: Int, listLimit: Int): PageSlice {
    val itemsPerPage = listLimit.coerceAtLeast(1)
    val maxPage = if (totalItems == 0) 0 else (totalItems - 1) / itemsPerPage
    val safePage = page.coerceIn(0, maxPage)
    val fromIndex = safePage * itemsPerPage
    val toIndex = (fromIndex + itemsPerPage).coerceAtMost(totalItems)
    return PageSlice(
        fromIndex = fromIndex,
        toIndex = toIndex,
        hasPreviousPage = safePage > 0,
        hasNextPage = toIndex < totalItems,
    )
}

private fun buildFavoriteEntityRow(
    carContext: CarContext,
    entity: Entity,
    isFavorite: Boolean,
    onCheckedChange: (Boolean) -> Unit,
): Row {
    val friendlyName = entity.attributes["friendly_name"]?.toString() ?: entity.entityId
    val domainLabel = SUPPORTED_DOMAINS_WITH_STRING[entity.domain]
        ?.let { carContext.getString(it) }
        ?: entity.domain

    return Row.Builder()
        .setTitle(friendlyName)
        .addText(domainLabel)
        .setToggle(
            Toggle.Builder { isChecked -> onCheckedChange(isChecked) }
                .setChecked(isFavorite)
                .build(),
        )
        .build()
}

private suspend fun persistFavoriteToggle(
    prefsRepository: PrefsRepository,
    serverId: Int,
    entityId: String,
    isChecked: Boolean,
    currentFavorites: List<AutoFavorite>,
): List<AutoFavorite> {
    val favorite = AutoFavorite(serverId = serverId, entityId = entityId)
    if (isChecked) {
        prefsRepository.addAutoFavorite(favorite)
    } else {
        prefsRepository.setAutoFavorites(currentFavorites.filterNot { it == favorite })
    }
    return prefsRepository.getAutoFavorites()
}

private fun favoriteEntityIdsForServer(favorites: List<AutoFavorite>, serverId: Int): Set<String> =
    favorites.asSequence()
        .filter { it.serverId == serverId }
        .map { it.entityId }
        .toSet()

private fun compareByFavoriteThenName(favoriteEntityIds: Set<String>): Comparator<Entity> =
    compareByDescending<Entity> { it.entityId in favoriteEntityIds }
        .thenBy { it.attributes["friendly_name"]?.toString() ?: it.entityId }

private fun filterEntitiesByQuery(entities: List<Entity>, query: String): List<Entity> {
    if (query.isBlank()) return entities
    val needle = query.trim().lowercase()
    return entities.filter { entity ->
        val friendlyName = entity.attributes["friendly_name"]?.toString()?.lowercase()
        friendlyName?.contains(needle) == true || entity.entityId.lowercase().contains(needle)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun carIcon(carContext: CarContext, icon: IIcon): CarIcon = CarIcon.Builder(
    IconicsDrawable(carContext, icon).apply { sizeDp = 64 }.toAndroidIconCompat(),
)
    .setTint(CarColor.DEFAULT)
    .build()
