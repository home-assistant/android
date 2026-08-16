package io.homeassistant.companion.android.widgets.climate

import android.util.Log
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
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
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
) {
    /**
     * Observes and provides the state of the widget identified by the given [widgetId].
     *
     * ### Flow details:
     * 1. **Initial state flow**: Emits the current state of the widget using the data in the database. If no configuration exists, it emits an empty state.
     * 2. **Watch for changes flow**: Listens for changes in the widget's configuration or updates from the server. When a change is detected:
     *    - It fetches the latest entity and climateEntity from the server.
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

                // Every emission is a change of the entity or of its name, so each one refreshes
                entitiesForDisplayManager.observe(serverId, listOf(listEntityId))
                    .map { state ->
                        val displayEntity = when (state) {
                            // Nothing was ever loaded for this widget, so there is nothing to show yet
                            EntityDisplayState.Loading if climateEntity.latestUpdateData == null -> {
                                return@map LoadingClimateState
                            }
                            // Still loading, or the entity is gone (removed server, deleted list),
                            // so the widget keeps showing the data of the database
                            EntityDisplayState.Loading, EntityDisplayState.Error -> {
                                return@map ClimateStateWithData.from(climateEntity)
                            }
                            is EntityDisplayState.Loaded -> state.entity(listEntityId)
                                ?: return@map ClimateStateWithData.from(climateEntity)
                        }
                        Timber.d("Got an update of the entity ${displayEntity.entityId} getting climates")

                        val step = displayEntity.climateControls?.targetTemperatureStep
                        val currentTemp = displayEntity.climateControls?.currentTemperature
                        val climateTemp = displayEntity.climateControls?.targetTemperature

                        // We update the DAO to keep it up to date for the next update of the widget
                        climateWidgetDao.updateWidgetLastUpdate(
                            widgetId = widgetId,
                            lastUpdateData = ClimateWidgetEntity.LastUpdateData(
                            entityName = displayEntity.name,
                            climateTemp = climateTemp,
                            currentTemp = currentTemp ?: 0f,
                            minTemp = displayEntity.climateControls?.minTemperature,
                            maxTemp = displayEntity.climateControls?.maxTemperature,
                            stepTemp = step,
                            stateClimate = displayEntity.rawState,
                            hvacModesSupported = displayEntity.climateControls?.hvacSupportedModes,
                            ),
                        )
                        ClimateStateWithData.from(climateEntity, displayEntity)
                    }
            }

        // Initial state should emit before watch but if an issue occur make it explicit in the flow
        return merge(getInitialStateFlow(widgetId), watchForChangeFlow).catch {
            // TODO send error to the widget???
            Timber.e(it, "Error while watching for changes for widget $widgetId")
        }.onCompletion {
            Timber.d("Stop watching for changes for widget $widgetId")
        }
    }

    private fun getClimateEntityOnConfigurationChange(widgetId: Int): Flow<ClimateWidgetEntity> {
        // The flow starts with a null dao entity until the configuration is done
        // We emit again when the configuration f the widget change
        return climateWidgetDao.getFlow(widgetId).filterNotNull().distinctUntilChanged { old, new ->
            old.isSameConfiguration(new)
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
}
