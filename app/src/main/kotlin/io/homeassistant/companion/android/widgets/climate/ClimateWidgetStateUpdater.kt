package io.homeassistant.companion.android.widgets.climate

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.ClimateWidgetDao
import io.homeassistant.companion.android.database.widget.ClimateWidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

/**
 * This class allow the [ClimateGlanceAppWidget] to get their states while in composition state
 * by exposing a flow through [stateFlow].
 */
internal class ClimateWidgetStateUpdater @Inject constructor(
    val climateWidgetDao: ClimateWidgetDao,
    val serverManager: ServerManager,
) {
    private fun getClimateEntityOnConfigurationChange(widgetId: Int): Flow<ClimateWidgetEntity> {
        // The flow starts with a null dao entity until the configuration is done
        // We emit again when the configuration f the widget change
        return climateWidgetDao.getFlow(widgetId).filterNotNull().distinctUntilChanged { old, new ->
            old.isSameConfiguration(new)
        }
    }

    private suspend fun getAndSubscribeEntityUpdates(serverId: Int, listEntityId: String): Flow<Entity?>? {
        if (serverManager.getServer(serverId) == null) {
            Timber.w("Server has been removed and the widget needs to be reconfigured")
            return null
        }

        // Since we might be re-subscribing we might not have get the entity update when subscribing so we query it first
        val currentEntity = serverManager.integrationRepository(serverId).getEntity(listEntityId)

        val entityUpdateFlow = serverManager.integrationRepository(serverId).getEntityUpdates(listOf(listEntityId))

        if (entityUpdateFlow == null) {
            Timber.w("Integration return null for entity update the widget won't update")
        }

        return entityUpdateFlow?.onStart {
            currentEntity?.let {
                emit(currentEntity)
            }
        }
    }

    private fun getInitialStateFlow(widgetId: Int): Flow<ClimateState> {
        return suspend { climateWidgetDao.get(widgetId) }.asFlow().map {
            if (it == null) {
                Timber.d("Error empty climate widget")
                EmptyClimateState
            } else {
                ClimateStateWithData.from(it)
            }
        }
    }

    /**
     * Observes and provides the state of the widget identified by the given [widgetId].
     *
     * ### Flow details:
     * 1. **Initial state flow**: Emits the current state of the widget using the data in the database. If no configuration exists, it emits an empty state.
     * 2. **Watch for changes flow**: Listens for changes in the widget's configuration or updates from the server. When a change is detected:
     *    - Updates the database with the latest data.
     *    - Emits the updated state.
     *
     * ### Error handling:
     * - If an error occurs while watching for changes, it logs the error.
     * - The flow completes gracefully when no longer needed, logging a message to indicate the end of observation.
     *
     * @param widgetId The unique identifier of the widget whose state is being observed.
     * @return A [Flow] emitting [ClimateState] objects representing the current state of the widget.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun stateFlow(widgetId: Int): Flow<ClimateState> {
        val watchForChangeFlow = getClimateEntityOnConfigurationChange(widgetId)
            .flatMapLatest { climateEntity ->
                Timber.d("Got a new entity to watch $climateEntity")
                val serverId = climateEntity.serverId
                val listEntityId = climateEntity.entityId
                getAndSubscribeEntityUpdates(
                    serverId,
                    listEntityId,
                )?.filterNotNull()?.distinctUntilChanged()?.map { entity ->
                    Timber.d("Got an update of the entity $entity")

                    val attributes = entity.attributes
                    val min = attributes["min_temp"] as? Double
                    val max = attributes["max_temp"] as? Double
                    val step = attributes["target_temp_step"] as? Double
                    val currentTemp = attributes["current_temperature"] as? Double
                    val climateTemp = attributes["temperature"] as? Double
                    val hvacSupportedModes = (attributes["hvac_modes"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val fanSupportedModes = (attributes["fan_modes"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                    // We update the DAO to keep it up to date for the next update of the widget
                    climateWidgetDao.updateWidgetLastUpdate(
                        widgetId = widgetId,
                        lastUpdateData = ClimateWidgetEntity.LastUpdateData(
                            entityName = entity.friendlyName,
                            climateTemp = climateTemp,
                            currentTemp = currentTemp,
                            minTemp = min,
                            maxTemp = max,
                            stepTemp = step,
                            stateClimate = entity.state,
                            hvacModesSupported = hvacSupportedModes,
                            fanModes = fanSupportedModes
                        ),
                    )
                    ClimateStateWithData.from(climateEntity, entity)
                } ?: flowOf(ClimateStateWithData.from(climateEntity))
            }

        // Initial state should emit before watch but if an issue occur make it explicit in the flow
        return merge(getInitialStateFlow(widgetId), watchForChangeFlow).catch {
            // TODO send error to the widget???
            Timber.e(it, "Error while watching for changes for widget $widgetId")
        }.onCompletion {
            Timber.d("Stop watching for changes for widget $widgetId")
        }
    }
}
