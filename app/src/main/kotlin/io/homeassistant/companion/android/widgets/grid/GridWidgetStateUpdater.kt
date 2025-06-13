package io.homeassistant.companion.android.widgets.grid

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.integration.isActive
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import io.homeassistant.companion.android.database.widget.GridWidgetItemEntity
import io.homeassistant.companion.android.database.widget.GridWidgetWithItemsEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import timber.log.Timber

internal class GridWidgetStateUpdater @Inject constructor(
    private val gridWidgetDao: GridWidgetDao,
    private val serverManager: ServerManager,
    @ApplicationContext private val context: Context
) {
    fun stateFlow(widgetId: Int): Flow<GridWidgetState> = gridWidgetDao.observe(widgetId).withEntityState()

    private fun Flow<GridWidgetWithItemsEntity>.withEntityState(): Flow<GridStateWithData> = transform {
        var state = it.toUiState()
        emit(state)

        val entityIds = it.items.map(GridWidgetItemEntity::entityId)
        val updates = getAndObserveEntities(it.gridWidget.serverId, entityIds)
        updates?.collect { entity ->
            state = state.copy(
                items = state.items.map { item ->
                    if (entity.entityId == item.id) {
                        item.copy(
                            state = entity.friendlyState(context, appendUnitOfMeasurement = true),
                            isActive = entity.isActive()
                        )
                    } else {
                        item
                    }
                }
            )
            emit(state)
        }
    }

    private suspend fun getAndObserveEntities(serverId: Int, entityIds: List<String>): Flow<Entity<*>>? {
        val currentEntities = entityIds.asFlow().mapNotNull { serverManager.integrationRepository(serverId).getEntity(it) }
        val entityUpdateFlow = serverManager.integrationRepository(serverId).getEntityUpdates(entityIds)

        if (entityUpdateFlow == null) {
            Timber.w("Failed to get entity updates, the widget won't update")
        }

        return entityUpdateFlow?.onStart {
            emitAll(currentEntities)
        }
    }
}

private fun GridWidgetWithItemsEntity.toUiState() = GridStateWithData(
    label = gridWidget.label,
    items = items.map(GridWidgetItemEntity::toUiState),
)

private fun GridWidgetItemEntity.toUiState() = GridButtonData(
    id = entityId,
    label = label,
    icon = iconName,
)
