package io.homeassistant.companion.android.widgets.grid

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.integration.isActive
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import io.homeassistant.companion.android.database.widget.GridWidgetEntity
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import timber.log.Timber

internal class GridWidgetStateUpdater @Inject constructor(
    private val gridWidgetDao: GridWidgetDao,
    private val serverManager: ServerManager,
    @param:ApplicationContext private val context: Context,
) {
    fun stateFlow(widgetId: Int): Flow<GridWidgetState> = gridWidgetDao.getFlow(widgetId)
        .withEntityState()
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<GridWidgetEntity>.withEntityState(): Flow<GridStateWithData> = transformLatest { widgetEntity ->
        // Use transformLatest to cancel previous entity fetches if widgetEntity changes quickly
        var currentUiState = widgetEntity.toUiState()
        emit(currentUiState)

        val entityIds = widgetEntity.items.map(GridWidgetEntity.Item::entityId)
        val entityUpdates = getAndObserveEntities(widgetEntity.serverId, entityIds)

        entityUpdates?.collect { updatedEntity ->
            val items = currentUiState.items.toMutableList()
            val itemIndex = items.indexOfFirst { it.id == updatedEntity.entityId }

            if (itemIndex != -1) {
                items[itemIndex] = items[itemIndex].copy(
                    state = updatedEntity.friendlyState(context, appendUnitOfMeasurement = true),
                    isActive = updatedEntity.isActive(),
                )
                currentUiState = currentUiState.copy(items = items.toImmutableList())
                emit(currentUiState)
            }
        }
    }

    private suspend fun getAndObserveEntities(serverId: Int, entityIds: List<String>): Flow<Entity>? {
        val currentEntities = entityIds.asFlow().mapNotNull {
            serverManager.integrationRepository(serverId).getEntity(it)
        }
        val entityUpdateFlow = serverManager.integrationRepository(serverId).getEntityUpdates(entityIds)

        if (entityUpdateFlow == null) {
            Timber.w("Failed to get entity updates, the widget won't update")
        }

        return entityUpdateFlow?.onStart {
            emitAll(currentEntities)
        }
    }
}

private fun GridWidgetEntity.toUiState() = GridStateWithData(
    label = label,
    items = items.map(GridWidgetEntity.Item::toUiState).toImmutableList(),
)

private fun GridWidgetEntity.Item.toUiState() = GridButtonData(
    id = entityId,
    label = label,
    icon = iconName,
)
